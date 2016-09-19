package com.avast

import com.google.protobuf.{ByteString, MessageLite}

import scala.language.experimental.macros
import scala.reflect.ClassTag

package object cactus {

  implicit class GpbToCaseClassConverter[Gpb <: MessageLite](val gpb: Gpb) extends AnyVal {
    def asCaseClass[CaseClass](implicit gpbCt: ClassTag[Gpb]): Either[CactusFailure, CaseClass] = macro CactusMacros.convertGpbToCaseClass[CaseClass]
  }

  implicit class CaseClassToGpbConverter[CaseClass](val caseClass: CaseClass) extends AnyVal {
    def asGpb[Gpb <: MessageLite](implicit caseClassCt: ClassTag[CaseClass]): Either[CactusFailure, Gpb] = macro CactusMacros.convertCaseClassToGpb[Gpb]
  }

  // additional converters:

  implicit val StringToByteStringConverter: CactusConverter[String, ByteString] = CactusConverter((b: String) => ByteString.copyFromUtf8(b))
  implicit val ByteStringToStringConverter: CactusConverter[ByteString, String] = CactusConverter((b: ByteString) => b.toStringUtf8)

  implicit val ByteArrayToByteStringConverter: CactusConverter[Array[Byte], ByteString] = CactusConverter((b: Array[Byte]) => ByteString.copyFrom(b))
  implicit val ByteStringToByteArrayConverter: CactusConverter[ByteString, Array[Byte]] = CactusConverter((b: ByteString) => b.toByteArray)

}
