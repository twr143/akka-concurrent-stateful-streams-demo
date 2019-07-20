package multipleStreams.server
import java.util.UUID
import akka.{Done, NotUsed}
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, UpgradeToWebSocket}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream._
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonParseException, readFromArray, writeToArray}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn
import scala.util.{Failure, Random, Success, Try}
import scala.util.control.NonFatal
import multipleStreams.Model._
import multipleStreams.Util._
import ElementRouter._
import akka.stream.contrib.Implicits.TimedFlowDsl
import ch.qos.logback.classic.{Level, Logger}
import multipleStreams.server.StateHolder.RemoveSubscriber
import util.StreamWrapperApp2
import scala.collection.mutable.ListBuffer
import scala.collection.{immutable, mutable}
import scala.concurrent.duration._

/**
  * Created by Ilya Volynin on 02.10.2018 at 16:51.
  *
  * launch: sbt "runMain multipleStreams.server.WSServerEntry"
  */
object WSServerEntry extends StreamWrapperApp2 {

  val CORE_COUNT = 2

  val countNum = 1000

  def timeCheck(duration: FiniteDuration)(implicit logger: Logger): Unit = {
    logger.warn("{} elements passed in {}", countNum, duration.toMillis)
  }

  def decider(router: ActorRef, reqId: UUID)(implicit logger: Logger): Supervision.Decider = {
    case e: JsonParseException ⇒
      router ! OutgoingMessage(InvalidBody(e.getMessage))
      Supervision.Resume
    case NonFatal(e) ⇒
      logger.error(e.getMessage, e)
      router ! OutgoingMessage(GeneralException(e.getMessage))
      Supervision.Stop
  }

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    val routerSupervisor = as.actorOf(Props[RouterSupervisor], "routerSupervisor")
    val stateHolder = as.actorOf(Props(new StateHolder(routerSupervisor)), "stateHolder")
    val sharedKS = KillSwitches.shared(s"kill-switch")

    def flow(reqId: UUID)(implicit logger: Logger): Flow[Message, Message, Any] = {
      val elementRouter = as.actorOf(Props(new ElementRouter(routerSupervisor, stateHolder)), name = s"route-$reqId")
      val incoming = Flow[Message]
        .watchTermination()((_, futDone: Future[Done]) =>
          futDone.onComplete {
            case Success(_) ⇒
//                            logger.warn("stream related to {} has finished",elementRouter)
              stateHolder ! RemoveSubscriber(elementRouter)
            case Failure(t) ⇒ logger.error(s"router flow failed for req: $reqId")
          })
        .collect {
          case tm: TextMessage ⇒ tm.textStream
        }
        .mapAsync(CORE_COUNT * 2 - 1)(in ⇒ in.runFold("")(_ + _)
          .map(bytesToBeanIn))
        .zipWithIndex
        .timedIntervalBetween(_._2 % countNum == 0, timeCheck).map(_._1)
        .map(IncomingMessage(_, reqId, elementRouter))
        .to(Sink.actorRef[IncomingMessage](elementRouter, PoisonPill))
        .withAttributes(ActorAttributes.supervisionStrategy(decider(elementRouter, reqId)))
      val outgoing: Source[Message, NotUsed] =
        Source.actorRef[OutgoingMessage](100000, OverflowStrategy.fail)
          .mapMaterializedValue { outActor =>
            // give the user actor a way to send messages out
            elementRouter ! Connected(outActor)
            NotUsed
          }.keepAlive(10.seconds, () => OutgoingMessage(Pong(Random.nextInt(100))))
          .mapAsync(CORE_COUNT * 2 - 1)(o => Future.successful(outgoingToTextMessageStrict(o.obj)))
      Flow.fromSinkAndSource(incoming, outgoing).via(sharedKS.flow)
    }

    val route: HttpRequest => HttpResponse = {
      case req@HttpRequest(HttpMethods.GET, Uri.Path("/ws_api"), headers: immutable.Seq[HttpHeader], _, _) =>
        req.header[UpgradeToWebSocket] match {
          case Some(upgrade) =>
            val reqId = UUID.randomUUID()
            upgrade.handleMessages(flow(reqId))
          case None => HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case r: HttpRequest =>
        r.discardEntityBytes() // important to drain incoming HTTP Entity stream
        HttpResponse(404, entity = "Unknown resource!")
    }
    val bindingFuture = Http().bindAndHandleSync(route, "localhost", 9000)
    println(s"Server online at http://localhost:9000/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture.flatMap {
      routerSupervisor ! PoisonPill
      sharedKS.shutdown
      _.unbind()
    }
  }
}
