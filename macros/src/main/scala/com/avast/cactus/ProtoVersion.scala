package com.avast.cactus

import com.avast.cactus.CactusMacros.{AnnotationsMap, ClassesNames}

import scala.collection.mutable
import scala.reflect.macros.whitebox

private[cactus] sealed trait ProtoVersion {
  def getQuery(c: whitebox.Context)(gpb: c.Tree, fieldNameUpper: String, fieldType: c.universe.Type): Option[c.Tree]
}

private[cactus] object ProtoVersion {

  case object V2 extends ProtoVersion {

    override def getQuery(c: whitebox.Context)(gpb: c.Tree, fieldNameUpper: String, fieldType: c.universe.Type): Option[c.Tree] = {
      import c.universe._

      val query = TermName(s"has$fieldNameUpper")

      Some(q"$gpb.$query")
    }
  }

  case object V3 extends ProtoVersion {

    private def isPrimitiveType(c: whitebox.Context)(fieldType: c.universe.Type): Boolean = {
      fieldType.baseClasses.exists { t =>
        CactusMacros.isPrimitive(c)(fieldType) || t.asType.fullName == ClassesNames.ByteString
      }
    }

    override def getQuery(c: whitebox.Context)(gpb: c.Tree, fieldNameUpper: String, fieldType: c.universe.Type): Option[c.Tree] = {
      if (isPrimitiveType(c)(fieldType)) {
        None // https://developers.google.com/protocol-buffers/docs/proto3#scalar  - scalar types don't have `has` method anymore
      } else {
        V2.getQuery(c)(gpb, fieldNameUpper, fieldType) // custom types have the `has` method as before
      }
    }

    def newOneOfConverter(c: whitebox.Context)(from: c.universe.Type, to: c.universe.Type)(impls: Set[c.universe.ClassSymbol])
                         (implicit converters: mutable.Map[String, c.universe.Tree]): Unit = {
      import c.universe._


      if (CactusMacros.Debug) println(s"Generating ONE-OF converter from ${from.typeSymbol} to ${to.typeSymbol}")

      //      val r = q" (a: $from) =>  { ${processEndType(c)(q"a", fieldAnnotations, srcTypeArg)(dstTypeArg, dstTypeArg, q"identity", " a ")} } "

      c.abort(c.enclosingPosition, s"END - ${impls}")

      val r =
        q""" (a: $from) => {
           ss
        } """

      CactusMacros.newConverter(c)(from, to)(r)
    }

    def extractNameOfOneOf(c: whitebox.Context)(typeSymbol: c.universe.TypeSymbol, annotations: AnnotationsMap): Option[String] = {
      extractNameOfOneOf(typeSymbol.fullName.split("\\.").last)
        .orElse{
          annotations.collectFirst{
            case (name, params) if name == classOf[GpbOneOf].getName => params("name")
          }
        }
    }

    def extractNameOfOneOf(name: String): Option[String] = {
      if (name.startsWith("OneOf")) {
        Option(name.substring(5).split("(?=\\p{Upper})").map(_.toLowerCase).mkString("_"))
      } else None
    }
  }

}
