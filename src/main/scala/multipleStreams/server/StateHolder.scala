package multipleStreams.server
import java.util.UUID
import akka.actor.{Actor, ActorRef, Terminated}
import ch.qos.logback.classic.Logger
import multipleStreams.Model._
import WSServerEntry._
import ElementRouter.IncomingMessage
import RouterSupervisor.Notification
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * Created by Ilya Volynin on 13.02.2019 at 19:08.
  *
  * This is supposed o be a persistent class, left as Actor for simplicity
  */
class StateHolder(routerManager: ActorRef)(implicit logger: Logger) extends Actor {
  import StateHolder._

  def receive: PartialFunction[Any, Unit] = {
    case IncomingMessage(o, reqId, routerActor) =>
      sender() ! Some(o).map(businessLogic(reqId, routerActor, routerManager)).get
    case RemoveSubscriber(s) =>
      subscribersToRemove += s
  }
}

object StateHolder {

  val subscribers = mutable.Set[ActorRef]()

  val subscribersToRemove = mutable.Set[ActorRef]()

  val items: mutable.ListBuffer[Item] = ListBuffer()

  val adminLoggedInMap = mutable.Map[java.util.UUID, Boolean]()

  def businessLogic(reqId: UUID, routerActor: ActorRef, routerManager: ActorRef)(implicit logger: Logger): PartialFunction[Incoming, Outgoing] = {
    case Login(login, password) if login == "admin" && password == "admin" ⇒
      adminLoggedInMap += (reqId -> true)
      //      logger.warn("adminLoggedInMap at login = {}, reqId = {}", adminLoggedInMap, reqId.toString, new Object)
      LoginSuccessful(usertype = "admin", reqId)
    case Login(login, password) if login == "user" && password == "user" ⇒
      LoginSuccessful(usertype = "user", reqId)
    case Login(login, _) ⇒ LoginFailed(login)
    case Ping(seq) => Pong(seq)
    case SubscribeChanges =>
      subscribers += routerActor
      ItemList(items.map(_.id).take(500), items.size)
    case UnsubscribeChanges =>
      subscribers -= routerActor
      UnsubscribedChanges
    case AddItem(t) => if (adminLoggedInMap(reqId)) {
      insert(items, t)
      val added = ItemAdded(t, reqId)
      sendNotification(routerManager, routerActor, added)
      added
    }
    else
      NotAuthorized("AddItem", reqId)
    case RemoveItem(id) if adminLoggedInMap(reqId) =>
      val i = findItemIndex(items, id)
      if (i > -1) {
        items.remove(i)
        val removed = ItemRemoved(id)
        sendNotification(routerManager, routerActor, removed)
        removed
      } else
        RemoveFailed(id)
    case _: RemoveItem => NotAuthorized("RemoveItem", reqId)
  }

  def insert(list: ListBuffer[Item], value: Item) = {
    list.append(value)
  }

  def findItemIndex(list: ListBuffer[Item], id: Int): Int = {
    list.indexWhere(_.id == id)
  }

  case class RemoveSubscriber(subscriber: ActorRef)

  def sendNotification(routerManager: ActorRef, routerActor: ActorRef, message: Outgoing)(implicit logger: Logger): Unit = {
    val common = subscribers.intersect(subscribersToRemove)
    //    if (subscribersToRemove.nonEmpty)
    //    logger.warn("subscribers before size {}, subs to rem {}", subscribers.size, subscribersToRemove.size)
    subscribers --= common
    subscribersToRemove --= common
    routerManager ! Notification(subscribers - routerActor, message)
  }
}

