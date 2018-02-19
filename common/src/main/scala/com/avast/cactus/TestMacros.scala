package com.avast.cactus

import scala.reflect.macros.{ParseException, TypecheckException, whitebox}

/** This whole code is copied from ScalaTest. The reason why we cannot use the original code is the original macros use `c.typecheck` with
  * disabled implicit view which is something this library stands on. There is no way how to enable it so it seemed better to copy the code
  * here.
  */
object TestMacros {
  def checkCompiles(c: whitebox.Context)(code: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._

    // extract code snippet
    val codeStr = getCodeStringFromCodeExpression(c)("assertDoesNotCompile", code)

    c.typecheck(c.parse("{ " + codeStr + " }"), pt = c.universe.WildcardType, silent = false, withImplicitViewsDisabled = false, withMacrosDisabled = false) // parse and type check code snippet
    c.Expr(q"()")
  }

  def checkDoesNotCompile(c: whitebox.Context)(code: c.Expr[String]): c.Expr[Unit] = {
    import c.universe._

    // extract code snippet
    val codeStr = getCodeStringFromCodeExpression(c)("assertDoesNotCompile", code)

    try {
      c.typecheck(c.parse("{ " + codeStr + " }")) // parse and type check code snippet

      c.abort(c.enclosingPosition, "The code must NOT compile but it did")
    } catch {
      case _: TypecheckException =>
        c.Expr(q"()")

      case _: ParseException =>
        c.Expr(q"()")

      case t: Throwable =>
        throw t
    }
  }

  private def getCodeStringFromCodeExpression(c: whitebox.Context)(methodName: String, code: c.Expr[String]): String = {
    import c.universe._
    code.tree match {
      case Literal(Constant(codeStr)) => codeStr.toString // normal string literal
      case Select(
      Apply(
      Select(
      _,
      augmentStringTermName
      ),
      List(
      Literal(Constant(codeStr))
      )
      ),
      stripMarginTermName
      ) if augmentStringTermName.toString == "augmentString" && stripMarginTermName.toString == "stripMargin" => codeStr.toString.stripMargin // """xxx""".stripMargin string literal
      case _ => c.abort(c.enclosingPosition, methodName + " only works with String literals.")
    }
  }
}
