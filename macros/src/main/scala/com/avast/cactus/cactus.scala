package com.avast

import com.avast.cactus.v3.AnyValue
import com.google.protobuf.{Message, MessageLite}
import org.scalactic.{Every, Or}

import scala.language.experimental.macros
import scala.language.reflectiveCalls
import scala.reflect.ClassTag

package object cactus {

  type CactusFailures = org.scalactic.Every[CactusFailure]
  type ResultOrError[A] = A Or CactusFailures

  implicit class GpbToCaseClassConverter[Gpb <: MessageLite](val gpb: Gpb) extends AnyVal {
    def asCaseClass[CaseClass: Converter[Gpb, ?]]: CaseClass Or Every[CactusFailure] = {
      implicitly[Converter[Gpb, CaseClass]].apply("_")(gpb)
    }
  }

  implicit class CaseClassToGpbConverter[CaseClass](val caseClass: CaseClass) extends AnyVal {
    def asGpb[Gpb <: MessageLite](implicit caseClassCt: ClassTag[CaseClass]): Gpb Or Every[CactusFailure] =
      macro CactusMacros.convertCaseClassToGpb[Gpb]
  }

  implicit class AnyValueParser(val anyValue: AnyValue) extends AnyVal {
    def asGpb[Gpb <: Message]: Gpb Or Every[CactusFailure] = macro ProtoVersion.V3.tryParseAny[Gpb]
  }

}
