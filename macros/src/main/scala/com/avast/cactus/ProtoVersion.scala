package com.avast.cactus

import com.avast.cactus.CactusMacros.{AnnotationsMap, ClassesNames}

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

    def newOneOfConverterToCaseClass(c: whitebox.Context)
                                    (from: c.universe.Type, oneOfType: FieldType.OneOf[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
      import c.universe._
      import oneOfType._

      val gpbClassSymbol = from.typeSymbol.asClass
      val oneOfTypeSymbol = classType.typeSymbol

      CactusMacros.init(c)(gpbClassSymbol)

      if (CactusMacros.Debug) println(s"Generating ONE-OF converter from $gpbClassSymbol to $oneOfTypeSymbol, GPB ONE-OF '$name'")

      val getCaseMethod = gpbClassSymbol.typeSignature.decls.collectFirst {
        case m if m.isMethod && m.name.toString == s"get${name}Case" => m.asMethod
      }.getOrElse(c.abort(c.enclosingPosition, s"Could not locate method get${name}Case inside $gpbClassSymbol, needed for ONE-OF $oneOfTypeSymbol"))

      val implsSeq = impls.toSeq

      val getters = implsSeq
        .map(_.name.toString)
        .map("get" + _)
        .map { n =>
          gpbClassSymbol.typeSignature.decls.collectFirst {
            case m if m.isMethod && m.name.toString == n => m.asMethod
          }.getOrElse(c.abort(c.enclosingPosition, s"Could not locate method $n inside $gpbClassSymbol, needed for ONE-OF $oneOfTypeSymbol"))
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

      if (CactusMacros.Debug) println(s"ONE-OF converter from $gpbClassSymbol to $oneOfTypeSymbol: $f")

      f
    }

    def newOneOfConverterToGpb(c: whitebox.Context)
                              (gpbType: c.universe.Type, gpbSetters: Iterable[c.universe.MethodSymbol])
                              (field: c.universe.Tree, oneOfType: FieldType.OneOf[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
      import c.universe._
      import oneOfType._

      val gpbClassSymbol = gpbType.typeSymbol.asClass
      val oneOfTypeSymbol = classType.typeSymbol

      val implsSeq = impls.toSeq

      val setters = implsSeq
        .map(_.name.toString)
        .map("set" + _)
        .map { n =>
          gpbSetters
            .find {
              _.name.toString == n
            }
            .getOrElse(c.abort(c.enclosingPosition, s"Could not locate method $n inside $gpbClassSymbol, needed for ONE-OF $oneOfTypeSymbol"))
        }

      val fieldNames = implsSeq
        .map { t =>
          t.typeSignature.decls.collectFirst {
            case m if m.isMethod && m.asMethod.isPrimaryConstructor => m.asMethod.paramLists.head.head.asTerm.name.toTermName
          }.getOrElse(c.abort(c.enclosingPosition, s"Could not extract value field name from $t, needed for ONE-OF $oneOfTypeSymbol"))
        }

      val options = implsSeq zip (setters zip fieldNames)

      val cases = options.map { case (ccl, (setter, fieldName)) =>
        cq" v: $ccl => builder.$setter(v.$fieldName)"
      }

      val f =
        q""" (field: $classType) => {
          field match {
            case ..$cases
          }
        }
       """

      if (CactusMacros.Debug) println(s"ONE-OF converter from $oneOfTypeSymbol to $gpbClassSymbol: $f")

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
