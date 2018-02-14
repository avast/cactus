package com.avast.cactus

import com.google.protobuf.Message
import org.scalactic.{Every, Or}

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

package object v3 extends CactusCommonImplicits with V3Converters {

  implicit class AnyValueParser(val anyValue: AnyValue) extends AnyVal {
    def asGpb[Gpb <: Message : AnyValueConverter]: ResultOrErrors[Gpb] = macro anyValueAsGpbMethod[Gpb]
  }

  def anyValueAsGpbMethod[Gpb: c.WeakTypeTag](c: whitebox.Context)(conv: c.Tree): c.Expr[Gpb Or Every[CactusFailure]] = {
    import c.universe._

    val gpbType = weakTypeOf[Gpb]

    val variable = CactusMacros.getVariable(c)
    val variableName = variable.symbol.asTerm.fullName.split('.').last

    c.Expr[Gpb Or Every[CactusFailure]] {
      q" implicitly[AnyValueConverter[$gpbType]].apply($variableName)($variable) "
    }
  }

}
