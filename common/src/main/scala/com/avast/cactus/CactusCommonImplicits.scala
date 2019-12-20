package com.avast.cactus

import com.google.protobuf.MessageLite

import scala.language.experimental.macros

trait CactusCommonImplicits {

  implicit class GpbToCaseClassConverter[Gpb <: MessageLite](val gpb: Gpb) {
    def asCaseClass[CaseClass: Converter[Gpb, ?]]: ResultOrErrors[CaseClass] =
      macro CactusMacros.asCaseClassMethod[CaseClass]
  }

  implicit class CaseClassToGpbConverter[CaseClass](val caseClass: CaseClass) {
    def asGpb[Gpb <: MessageLite: Converter[CaseClass, ?]]: ResultOrErrors[Gpb] =
      macro CactusMacros.asGpbMethod[Gpb]
  }

}
