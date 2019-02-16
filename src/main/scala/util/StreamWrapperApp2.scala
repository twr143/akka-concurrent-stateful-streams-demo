package util
import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by Ilya Volynin on 20.11.2018 at 16:56.
  */
trait StreamWrapperApp2 {

  def body(args: Array[String])(implicit as: ActorSystem, mat: ActorMaterializer, ec: ExecutionContext, logger: Logger): Future[Any]

  def main(args: Array[String]): Unit = {
    implicit val as: ActorSystem = ActorSystem()
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContext = as.dispatcher
    implicit lazy val root: Logger = LoggerFactory.getLogger(s"${this.getClass.getCanonicalName}".replace("$", "")).asInstanceOf[ch.qos.logback.classic.Logger]
    root.setLevel(Level.WARN)
    try Await.result(body(args), 60.minutes)
    finally as.terminate()
  }
}
