package multipleStreams.client
/**
  * Created by Ilya Volynin on 11.11.2018 at 20:24.
  */
import akka.actor.ActorSystem
import akka.Done
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.unmarshalling.Unmarshal
import ch.qos.logback.classic.Logger
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArray, writeToArray}
import multipleStreams.Model._
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import multipleStreams.Util._
import scala.util.Success

// run in parallel:
// "runMain multipleStreams.client.WSClientFlowEntry 0 20" and
// "runMain multipleStreams.client.WSClientFlowEntry 21 40"
/*
  This test demonstrates handling notifications from other clients in addition to handling responses from server
  While the client creates the first half of the items it receives notifications from other clients.
  Then the client unsubscribes from notifications and receives server responses only
 */
object WSClientFlowEntry extends StreamWrapperApp2 {

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    if (args.length != 2) return Future.failed(new Exception("please provide first and last indexes of range for adding table as program arguments. Thanks."))
    //
    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach {
        case message: TextMessage.Strict =>
          bytesToBeanOut.andThen(_.toString).andThen(logger.warn)(message.text)
      }
    //
    val (start, end) = (args(0).toInt, args(1).toInt)
    val loginSource = Source.single(Login("admin", "admin"))
    val iterFirstHalf = Iterator.range(start, start + (end - start) / 2)
    val iterSecondHalf = Iterator.range(start + (end - start) / 2 + 1, end)
    val newItemsFirstHalf = Source.fromIterator(() => iterFirstHalf).map(i => AddItem(Item(i, "Item " + i * i)))
    val newItemsSecondHalf = Source.fromIterator(() => iterSecondHalf).map(i => AddItem(Item(i, "Item " + i * i)))
    val subscribeSource = Source.single(SubscribeChanges)
    val unSubscribeSource = Source.single(UnsubscribeChanges)
    val maybeSource = Source.maybe[Incoming]
    val aggSource = Source.combine[Incoming, Incoming](loginSource,
      subscribeSource, newItemsFirstHalf,
      unSubscribeSource, newItemsSecondHalf

      /*,maybeSource*/
      // if we wish infinite stream
    )(Concat(_)).map(incomingToTextMessageStrict )
    //
    val webSocketFlow = Http().webSocketClientFlow(
      WebSocketRequest("ws://localhost:9000/ws_api",
        scala.collection.immutable.Seq(Authorization(BasicHttpCredentials("ilya", "voly")))))
    //
    //
    //
    //
    val (upgradeResponse, closed) =
    aggSource //.concatMat(Source.maybe[Incoming])(Keep.right)
      //I've added throttle to extend time of execution to allow this client receiving notifications
      //from other clients running in parallel
      // The client closes once the source has been completed
      .throttle(1, 1.second, 1, ThrottleMode.shaping)
      //
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .run()
    upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        logger.warn(s"resp status ${upgrade.response}")
        Future.successful(Done)
      } else {
        Unmarshal(upgrade.response.entity).to[String].andThen {
          case Success(s) =>
            throw new RuntimeException(s"Connection failed: ${upgrade.response.status} $s")
        }
      }
    }.andThen { case tr => logger.warn(tr.toString) }
    closed.onComplete(_ => logger.warn("closed"))
    closed
  }
}