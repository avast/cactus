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

    def newOneOfConverter(c: whitebox.Context)
                         (from: c.universe.Type, to: c.universe.Type)
                         (oneOfType: FieldType.OneOf[c.universe.MethodSymbol, c.universe.ClassSymbol])
                         (implicit converters: mutable.Map[String, c.universe.Tree]): c.Tree = {
      import c.universe._
      import oneOfType._

      val gpbClassSymbol = from.typeSymbol.asClass

      CactusMacros.init(c)(gpbClassSymbol)

      if (CactusMacros.Debug) println(s"Generating ONE-OF converter from ${from.typeSymbol} to ${to.typeSymbol}, GPB ONE-OF '$name'")

      val getCaseMethod = gpbClassSymbol.typeSignature.decls.collectFirst {
        case m if m.isMethod && m.name.toString == s"get${name}Case" => m.asMethod
      }.getOrElse(c.abort(c.enclosingPosition, s"Could not locate method get${name}Case inside $gpbClassSymbol, needed for ONE-OF ${to.typeSymbol}"))

      val implsSeq = impls.toSeq

      val getters = implsSeq
        .map(_.name.toString)
        .map("get" + _)
        .map { n =>
          gpbClassSymbol.typeSignature.decls.collectFirst {
            case m if m.isMethod && m.name.toString == n => m.asMethod
          }.getOrElse(c.abort(c.enclosingPosition, s"Could not locate method $n inside $gpbClassSymbol, needed for ONE-OF ${to.typeSymbol}"))
        }

      val enumValues = implsSeq
        .map(n => splitByUppers(n.name.toString)
          .map(_.toUpperCase).mkString("_"))
        .map(TermName(_))

      val enumClass = {
        val enumClassName = s"${gpbClassSymbol.fullName}.${name}Case"
        val cl = CactusMacros.extractType(c)(s"$enumClassName.${name.toUpperCase}_NOT_SET.asInstanceOf[$enumClassName]")

        cl.typeSymbol.asClass.companion
      }

      val options = enumValues zip (implsSeq zip getters)

      val cases = options.map { case (enum, (ccl, getter)) =>
        cq""" $enumClass.$enum => Good(${ccl.companion}.apply(wholeGpb.$getter))  """
      } :+
        cq""" $enumClass.${TermName(name.toUpperCase + "_NOT_SET")} => Bad(One(OneOfValueNotSetFailure($name))) """

      val f =
        q""" {
             (wholeGpb: $from) => wholeGpb.$getCaseMethod match {
                case ..$cases
             }
        }
       """

      if (CactusMacros.Debug) println(s"ONE-OF converter from ${from.typeSymbol} to ${to.typeSymbol}: $f")

      f
    }

    def extractNameOfOneOf(c: whitebox.Context)(typeSymbol: c.universe.TypeSymbol, fieldAnnotations: AnnotationsMap): Option[String] = {
      fieldAnnotations.collectFirst {
        case (name, params) if name == classOf[GpbOneOf].getName => params("value")
      }
    }

    private def splitByUppers(s: String): Array[String] = {
      s.split("(?=\\p{Upper})")
    }
  }

}
