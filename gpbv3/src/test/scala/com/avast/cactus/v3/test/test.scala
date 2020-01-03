package com.avast.cactus.v3
import com.avast.cactus.Converter
import com.google.protobuf.ByteString

import scala.jdk.CollectionConverters._

package object test {

  // user specified converters
  implicit val StringToByteStringConverter: Converter[String, ByteString] = Converter((b: String) => ByteString.copyFromUtf8(b))
  implicit val ByteStringToStringConverter: Converter[ByteString, String] = Converter((b: ByteString) => b.toStringUtf8)

  implicit val StringWrapperToStringConverter: Converter[StringWrapperClass, String] = Converter((b: StringWrapperClass) => b.value)
  implicit val StringToStringWrapperConverter: Converter[String, StringWrapperClass] = Converter((b: String) => StringWrapperClass(b))

  implicit val JavaIntegerListStringConverter: Converter[java.util.List[Integer], String] = Converter(_.asScala.mkString(", "))
  implicit val StringJavaIntegerListConverter: Converter[String, java.lang.Iterable[_ <: Integer]] = Converter(
    _.split(", ").map(_.toInt).map(int2Integer).toSeq.asJava)

  implicit val StringIntConverter: Converter[String, Int] = Converter(_.toInt)
  implicit val IntStringConverter: Converter[Int, String] = Converter(_.toString)

}
