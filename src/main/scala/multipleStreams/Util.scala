package multipleStreams

import java.nio.charset.StandardCharsets

/**
  * Created by Ilya Volynin on 12.11.2018 at 10:48.
  */
object Util {
  implicit def convertArrayOfBytesToString(bytes: Array[Byte]): String = new String(bytes, StandardCharsets.UTF_8)

}
