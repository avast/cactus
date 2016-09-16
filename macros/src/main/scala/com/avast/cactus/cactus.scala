package com.avast

import com.google.protobuf.MessageLite

import scala.language.experimental.macros
import scala.reflect.ClassTag

package object cactus {

  implicit class GpbToCaseClassConverter(val gpb: MessageLite) extends AnyVal {
    def asCaseClass[CaseClass]: Either[CactusFailure, CaseClass] = macro CactusMacros.convertGpbToCaseClass[CaseClass]
  }

  implicit class CaseClassToGpbConverter[CaseClass](val caseClass: CaseClass) extends AnyVal {
    def asGpb[Gpb <: MessageLite](implicit ct: ClassTag[CaseClass]): Either[CactusFailure, Gpb] = macro CactusMacros.convertCaseClassToGpb[Gpb]
  }

}
