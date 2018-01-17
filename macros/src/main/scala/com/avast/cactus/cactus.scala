package com.avast

import com.avast.cactus.v3.AnyValue
import com.google.protobuf.{Message, MessageLite}
import org.scalactic.Or

import scala.language.experimental.macros
import scala.reflect.ClassTag

package object cactus {

  type CactusFailures = org.scalactic.Every[CactusFailure]
  type ResultOrError[A] = A Or CactusFailures

  implicit class GpbToCaseClassConverter[Gpb <: MessageLite](val gpb: Gpb) extends AnyVal {
    def asCaseClass[CaseClass: Converter[Gpb, ?]](implicit gpbCt: ClassTag[Gpb]): ResultOrError[CaseClass] = macro CactusMacros.asCaseClassMethod[CaseClass]
  }

  implicit class CaseClassToGpbConverter[CaseClass](val caseClass: CaseClass) extends AnyVal {
    def asGpb[Gpb <: MessageLite : Converter[CaseClass, ?]](implicit caseClassCt: ClassTag[CaseClass]): ResultOrError[Gpb] = macro CactusMacros.asGpbMethod[Gpb]
  }

  implicit class AnyValueParser(val anyValue: AnyValue) extends AnyVal {
    def asGpb[Gpb <: Message : AnyValueConverter]: ResultOrError[Gpb] = macro ProtoVersion.V3.anyValueAsGpbMethod[Gpb]
  }

}
