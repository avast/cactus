package com.avast

import scala.language.experimental.macros

package object cactus {

  implicit class GpbToCaseClassConverter(val gpb: MessageLite) extends AnyVal {
    def as[Out]: Either[CactusFailure, Out] = macro CactusMacros.convertGpbToCaseClassT[Out]
  }

}
