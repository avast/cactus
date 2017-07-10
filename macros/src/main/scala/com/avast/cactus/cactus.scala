package com.avast

import com.avast.cactus.v3.AnyValue
import com.google.protobuf.{Message, MessageLite}
import org.scalactic.{Every, Or}

import scala.language.experimental.macros
import scala.reflect.ClassTag

package object cactus {

  implicit class GpbToCaseClassConverter[Gpb <: MessageLite](val gpb: Gpb) extends AnyVal {
    def asCaseClass[CaseClass](implicit gpbCt: ClassTag[Gpb]): CaseClass Or Every[CactusFailure] = macro CactusMacros.convertGpbToCaseClass[CaseClass]
  }

  implicit class CaseClassToGpbConverter[CaseClass](val caseClass: CaseClass) extends AnyVal {
    def asGpb[Gpb <: MessageLite](implicit caseClassCt: ClassTag[CaseClass]): Gpb Or Every[CactusFailure] = macro CactusMacros.convertCaseClassToGpb[Gpb]
  }

  implicit class AnyValueParser(val anyValue: AnyValue) extends AnyVal {
    def asGpb[Gpb <: Message]: Gpb Or Every[CactusFailure] = macro ProtoVersion.V3.tryParseAny[Gpb]
  }

}
