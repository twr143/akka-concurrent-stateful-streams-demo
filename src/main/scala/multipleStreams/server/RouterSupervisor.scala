package multipleStreams.server
/**
  * Created by Ilya Volynin on 28.10.2018 at 13:56.
  */
import akka.actor._
import multipleStreams.Model.Outgoing
import ElementRouter.{IncomingMessage, OutgoingMessage}
import multipleStreams.server.StateHolder._

import scala.collection.mutable

object RouterSupervisor {

  case object Join

  case class Notification(subscribers: mutable.Set[ActorRef], message: Outgoing)


}

class RouterSupervisor extends Actor {
  import RouterSupervisor._


  def receive = {
    case Join =>
      context.watch(sender())

    case Terminated(user) =>
    case msg: Notification =>
      msg.subscribers.foreach(_ ! OutgoingMessage(msg.message))
  }
}