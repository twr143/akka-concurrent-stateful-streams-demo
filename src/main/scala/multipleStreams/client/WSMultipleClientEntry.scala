package multipleStreams.client
import akka.{Done, NotUsed}
import akka.actor.{ActorSystem, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.stream.scaladsl.{Concat, Flow, Keep, Sink, Source}
import ch.qos.logback.classic.Logger
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromArray, writeToArray}
import multipleStreams.Model._
import util.StreamWrapperApp2
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import multipleStreams.Util._
import scala.util.Success

/**
  * Created by Ilya Volynin on 19.11.2018 at 15:20.
  * run: "runMain multipleStreams.client.WSMultipleClientEntry"
  */
object WSMultipleClientEntry extends StreamWrapperApp2 {

  override def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any] = {
    //
    def outgoing(description: String): Sink[Message, Future[Done]] =
      Sink.foreach {
        case message: TextMessage.Strict =>
          val out = readFromArray[Outgoing](message.text.getBytes("UTF-8"))
          out match {
            case _: ItemAdded | _: ItemRemoved =>
            case theRest =>
              logger.warn(" {} {}", description, theRest, new Object)
          }
      }

    val loginSource = Source.single(Login("admin", "admin"))
    val subscribeSource = Source.single(SubscribeChanges)
    val unSubscribeSource = Source.single(UnsubscribeChanges)
    val maybeSource = Source.maybe[Incoming]
    val itemsPerClient = 1000

    def iterFirstHalf(startIdx: Int) = Iterator.range(startIdx * 1000, startIdx * 1000 + itemsPerClient)

    def newItemsSource(startIdx: Int) = Source.fromIterator(() => iterFirstHalf(startIdx)).map(i => AddItem(Item(i, "Item name " + i * i)))

    def removeItemsSource(startIdx: Int) =
      Source.fromIterator(() => iterFirstHalf(startIdx)).map(RemoveItem)

    def aggSource(index: Int) = Source.combine[Incoming, Incoming](loginSource,
      subscribeSource,
      newItemsSource(index),
      removeItemsSource(index)
    )(Concat(_)).map(incoming => TextMessage(writeToArray[Incoming](incoming))
    )

    def webSocketFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(
      WebSocketRequest("ws://localhost:9000/ws_api"))

    val r = Source.fromIterator(() => Iterator.range(7, 25))
      .flatMapMerge(10, i => aggSource(i) /*.throttle(100, 200.millis)*/ .viaMat(webSocketFlow)(Keep.right)
        .alsoToMat(outgoing("main:"))(Keep.both)
      ).runWith(Sink.ignore)
    val checkPromise = Promise[Done]()
    r.onComplete {
      case Success(_) =>
        //check the number of items left in the list
        Source.combine[Incoming, Incoming](loginSource, subscribeSource)(Concat(_)).map(incoming => TextMessage(writeToArray[Incoming](incoming)))
          .viaMat(webSocketFlow)(Keep.right).alsoToMat(outgoing("check:"))(Keep.both).runWith(Sink.ignore).onComplete { case Success(_) => checkPromise.complete(Success(Done)) }
    }
    checkPromise.future
  }

  def resultSink(implicit as: ActorSystem, ec: ExecutionContext, logger: Logger): Sink[List[Future[Done]], Future[Done]] =
    Sink.foreach {
      list: List[Future[Done]] =>
        logger.warn(s"reached resultSink, size ${list.size}")
        Future.sequence(list)
    }
}
