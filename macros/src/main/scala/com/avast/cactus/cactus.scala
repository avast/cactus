package com.avast

import com.google.protobuf.MessageLite
import org.scalactic.{Every, Or}

import scala.language.experimental.macros
import scala.reflect.ClassTag

package object cactus {

  implicit class GpbToCaseClassConverter[Gpb <: MessageLite](val gpb: Gpb) extends AnyVal {
    def asCaseClass[CaseClass](implicit gpbCt: ClassTag[Gpb]): CaseClass Or Every[CactusFailure] = macro CactusMacros.convertGpbToCaseClass[CaseClass]
  }

  implicit class CaseClassToGpbConverter[CaseClass](val caseClass: CaseClass) extends AnyVal {
    def asGpb[Gpb <: MessageLite](implicit caseClassCt: ClassTag[CaseClass]): Either[CactusFailure, Gpb] = macro CactusMacros.convertCaseClassToGpb[Gpb]
  }

}
