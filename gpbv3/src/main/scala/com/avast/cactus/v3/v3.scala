package com.avast.cactus

import com.google.protobuf.Message

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

package object v3 extends CactusCommonImplicits with V3Converters {

  implicit class AnyValueParser(val anyValue: AnyValue) extends AnyVal {
    def asGpb[Gpb <: Message: AnyValueConverter]: ResultOrErrors[Gpb] = macro anyValueAsGpbMethod[Gpb]
    def asCaseClass[CaseClass: AnyValueConverter]: ResultOrErrors[CaseClass] = macro anyValueAsCCMethod[CaseClass]
  }

  def anyValueAsGpbMethod[Gpb: c.WeakTypeTag](c: whitebox.Context)(conv: c.Tree): c.Expr[ResultOrErrors[Gpb]] = {
    import c.universe._

    val gpbType = weakTypeOf[Gpb]

    val variable = CactusMacros.getVariable(c)
    val variableName = variable.symbol.asTerm.fullName.split('.').last

    c.Expr[ResultOrErrors[Gpb]] {
      q" implicitly[AnyValueConverter[$gpbType]].apply($variableName)($variable) "
    }
  }

  def anyValueAsCCMethod[CaseClass: c.WeakTypeTag](c: whitebox.Context)(conv: c.Tree): c.Expr[ResultOrErrors[CaseClass]] = {
    import c.universe._

    val caseClassType = weakTypeOf[CaseClass]

    val variable = CactusMacros.getVariable(c)
    val variableName = variable.symbol.asTerm.fullName.split('.').last

    c.Expr[ResultOrErrors[CaseClass]] {
      q" implicitly[AnyValueConverter[$caseClassType]].apply($variableName)($variable) "
    }
  }

  implicit class AnyValueConverterOps[A](val conv: AnyValueConverter[A]) {
    final def map[B](f: A => B): Converter[AnyValue, B] = new Converter[AnyValue, B] {
      override def apply(fieldPath: String)(a: AnyValue): ResultOrErrors[B] = conv.apply(fieldPath)(a).map(f)
    }

    final def andThen[B](implicit c: Converter[A, B]): Converter[AnyValue, B] = new Converter[AnyValue, B] {
      override def apply(fieldPath: String)(a: AnyValue): ResultOrErrors[B] = conv.apply(fieldPath)(a).flatMap(c.apply(fieldPath))
    }

    final def contraMap[AA](f: AA => AnyValue): Converter[AA, A] =
      Converter(f).andThen(Converter.checked { (path, av) =>
        conv.apply(path)(av)
      })

    final def compose[AA](implicit f: Converter[AA, AnyValue]): Converter[AA, A] =
      f.andThen(Converter.checked { (path, av) =>
        conv.apply(path)(av)
      })
  }
}
