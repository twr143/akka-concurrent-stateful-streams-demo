package multipleStreams

import java.nio.charset.StandardCharsets
import java.util.UUID
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}
import scala.collection.mutable.ListBuffer

/**
  * Created by Ilya Volynin on 02.10.2018 at 16:53.
  */
object Model {

  case class Item(id: Int, name: String)

  sealed trait Incoming

  sealed trait Outgoing

  case class Login(username: String, password: String) extends Incoming

  case class Ping(seq: Int) extends Incoming

  case object SubscribeChanges extends Incoming

  case object UnsubscribeChanges extends Incoming

  case class AddItem(item: Item) extends Incoming

  case class RemoveItem(id: Int) extends Incoming

  case class LoginSuccessful(usertype: String, reqId: UUID) extends Outgoing

  case class LoginFailed(login: String) extends Outgoing

  case class InvalidBody(msg: String) extends Outgoing

  case class GeneralException(msg: String) extends Outgoing

  case class Pong(seq: Int) extends Outgoing

  case class ItemAdded(item: Item, reqId: UUID) extends Outgoing

  case class ItemRemoved(id: Int) extends Outgoing

  case class RemoveFailed(id: Int) extends Outgoing

  case class ItemList(items: ListBuffer[Int], size: Int) extends Outgoing

  case class NotAuthorized(command: String, reqId: UUID) extends Outgoing

  case object NotSubscribed extends Outgoing

  case object SubscribedChanges extends Outgoing

  case object UnsubscribedChanges extends Outgoing

  implicit val codecIn: JsonValueCodec[Incoming] = JsonCodecMaker.make[Incoming](CodecMakerConfig(adtLeafClassNameMapper =
    (JsonCodecMaker.simpleClassName _).andThen(JsonCodecMaker.enforce_snake_case), discriminatorFieldName = Some("$type")))

  implicit val codecOut: JsonValueCodec[Outgoing] = JsonCodecMaker.make[Outgoing](CodecMakerConfig(adtLeafClassNameMapper =
    (JsonCodecMaker.simpleClassName _).andThen(JsonCodecMaker.enforce_snake_case), discriminatorFieldName = Some("$type")))
}
