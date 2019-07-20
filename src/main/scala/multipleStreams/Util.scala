package multipleStreams
import java.nio.charset.StandardCharsets
import akka.http.scaladsl.model.ws.TextMessage
import com.github.plokhotnyuk.jsoniter_scala.core._
import multipleStreams.Model._

/**
  * Created by Ilya Volynin on 12.11.2018 at 10:48.
  */
object Util {

  def convertArrayOfBytesToString(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)

  def wToArray[A](x: A)(implicit codec: JsonValueCodec[A]) = writeToArray(x)

  def rFromArrayIn(buf: Array[Byte]): Incoming = readFromArray[Incoming](buf)

  def rFromArrayOut(buf: Array[Byte]): Outgoing = readFromArray[Outgoing](buf)

  def incomingToTextMessageStrict = (wToArray[Incoming] _).andThen(convertArrayOfBytesToString).andThen(TextMessage.apply)

  def outgoingToTextMessageStrict = (wToArray[Outgoing] _).andThen(convertArrayOfBytesToString).andThen(TextMessage.apply)

  def inToBytes: String => Array[Byte] = _.getBytes("UTF-8")

  def bytesToBeanIn = inToBytes.andThen(rFromArrayIn)

  def bytesToBeanOut = inToBytes.andThen(rFromArrayOut)

}
