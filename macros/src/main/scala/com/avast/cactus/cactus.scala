package com.avast

import com.avast.cactus.v3.AnyValue
import com.google.protobuf.{Message, MessageLite}
import org.scalactic.Or

import scala.language.experimental.macros

package object cactus {

  type CactusFailures = org.scalactic.Every[CactusFailure]
  type ResultOrError[A] = A Or CactusFailures

  implicit class GpbToCaseClassConverter[Gpb <: MessageLite](val gpb: Gpb) extends AnyVal {
    def asCaseClass[CaseClass: Converter[Gpb, ?]]: ResultOrError[CaseClass] = {
      implicitly[Converter[Gpb, CaseClass]].apply("_")(gpb)
    }
  }

  implicit class CaseClassToGpbConverter[CaseClass](val caseClass: CaseClass) extends AnyVal {
    def asGpb[Gpb <: MessageLite: Converter[CaseClass, ?]]: ResultOrError[Gpb] =
      implicitly[Converter[CaseClass, Gpb]].apply("_")(caseClass)
  }

  implicit class AnyValueParser(val anyValue: AnyValue) extends AnyVal {
    def asGpb[Gpb <: Message: AnyValueConverter]: ResultOrError[Gpb] = {
      implicitly[AnyValueConverter[Gpb]].apply("_")(anyValue)
    }
  }

}
