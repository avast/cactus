package com.avast.cactus

import com.avast.cactus.CactusMacros.{AnnotationsMap, ClassesNames, newConverter, typesEqual}

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
        CactusMacros.isPrimitive(c)(fieldType) || t.asType.fullName == ClassesNames.Protobuf.ByteString || t.asType.fullName == ClassesNames.Protobuf.Enum
      }
    }

    override def getQuery(c: whitebox.Context)(gpb: c.Tree, fieldNameUpper: String, fieldType: c.universe.Type): Option[c.Tree] = {
      if (isPrimitiveType(c)(fieldType)) {
        None // https://developers.google.com/protocol-buffers/docs/proto3#scalar  - scalar types don't have `has` method anymore
      } else {
        V2.getQuery(c)(gpb, fieldNameUpper, fieldType) // custom types have the `has` method as before
      }
    }

    def newOneOfConverterToSealedTrait(c: whitebox.Context)
                                      (from: c.universe.Type, oneOfType: FieldType.OneOf[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
      import c.universe._
      import oneOfType._

      val gpbClassSymbol = from.typeSymbol.asClass

      CactusMacros.init(c)(gpbClassSymbol)

      if (CactusMacros.Debug) println(s"Generating ONE-OF converter from $gpbClassSymbol to ${classType.resultType}, GPB ONE-OF '$name'")

      val getCaseMethod = gpbClassSymbol.typeSignature.decls.collectFirst {
        case m if m.isMethod && m.name.toString == s"get${name}Case" => m.asMethod
      }.getOrElse(c.abort(c.enclosingPosition, s"Could not locate method get${name}Case inside $gpbClassSymbol, needed for ONE-OF ${classType.resultType}"))

      val implsSeq = impls.toSeq

      val getters = implsSeq
        .map(_.name.toString)
        .map("get" + _)
        .map { n =>
          gpbClassSymbol.typeSignature.decls.collectFirst {
            case m if m.isMethod && m.name.toString == n => m.asMethod
          }.getOrElse(c.abort(c.enclosingPosition, s"Could not locate method $n inside $gpbClassSymbol, needed for ONE-OF ${classType.resultType}"))
        }

      val enumValues = implsSeq
        .map(n => CactusMacros.splitByUppers(n.name.toString)
          .map(_.toUpperCase).mkString("_"))
        .map(TermName(_))

      val enumClass = {
        val enumClassName = s"${gpbClassSymbol.fullName}.${name}Case"
        val cl = CactusMacros.extractType(c)(s"$enumClassName.${name.toUpperCase}_NOT_SET.asInstanceOf[$enumClassName]")

        cl.typeSymbol.asClass.companion
      }

      val options = enumValues zip (implsSeq zip getters)

      val cases = options.map { case (enum, (ccl, getter)) =>
        CactusMacros.getCtorParamType(c)(ccl) match {
          case Some(cpt) => // case class
            val value = CactusMacros.convertIfNeeded(c)(c.Expr[String](q"fieldPath"), getter.returnType, cpt)(q"wholeGpb.$getter")
            cq""" $enumClass.$enum => $value.map(${ccl.companion}.apply)  """

          case None => // case object
            cq""" $enumClass.$enum => Good(${ccl.module})  """
        }
      } :+
        cq""" $enumClass.${TermName(name.toUpperCase + "_NOT_SET")} => Bad(One(OneOfValueNotSetFailure(fieldPath + "." + $name))) """

      val f =
        q""" {
             (fieldPath: String, wholeGpb: $from) => wholeGpb.$getCaseMethod match {
                case ..$cases
             }
        }
       """

      if (CactusMacros.Debug) println(s"ONE-OF converter from $gpbClassSymbol to ${classType.resultType}: $f")

      f
    }

    def newOneOfConverterToGpb(c: whitebox.Context)
                              (gpbType: c.universe.Type, gpbSetters: Iterable[c.universe.MethodSymbol])
                              (oneOfType: FieldType.OneOf[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
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

      val fields = implsSeq
        .map { t =>
          t.typeSignature.decls.collectFirst {
            case m if m.isMethod && m.asMethod.isPrimaryConstructor => m.asMethod.paramLists.flatten.headOption
          }.getOrElse(CactusMacros.terminateWithInfo(c)(s"Could not extract value field name from $t, needed for ONE-OF $oneOfTypeSymbol"))
        }

      val options = implsSeq zip (setters zip fields)

      val cases = options.map {
        case (ccl, (setter, Some(field))) => // case class
          val fieldName = field.name.toTermName

          val setterArgType = CactusMacros.getParamType(c)(setter)

          val value = CactusMacros.convertIfNeeded(c)(c.Expr[String](q"fieldPath"), field.typeSignature, setterArgType)(q"v.$fieldName")

          cq" v: $ccl => $value.map(builder.$setter)"

        case (ccl, (setter, None)) => // case object
          val setterArgType = CactusMacros.getParamType(c)(setter)

          if (setterArgType.typeSymbol.fullName != CactusMacros.ClassesNames.Protobuf.Empty) {
            CactusMacros.terminateWithInfo(c)(s"ONE-OF trait implementations has to have 'google.protobuf.Empty' as counterpart in GPB; has $setterArgType")
          }

          cq" _: ${ccl.module} => Good(builder.$setter(_root_.com.google.protobuf.Empty.getDefaultInstance()))"
      }

      val f =
        q""" (fieldPath: String, field: $classType) => {
          field match {
            case ..$cases
          }
        }
       """

      if (CactusMacros.Debug) println(s"ONE-OF converter from $oneOfTypeSymbol to $gpbClassSymbol: $f")

      f
    }

    def extractNameOfOneOf(c: whitebox.Context)(fieldNameUpper: String, fieldAnnotations: AnnotationsMap): Option[String] = {
      // has to be annotated with GpbOneOf and optionally with GpbName
      if (fieldAnnotations.exists(_._1 == classOf[GpbOneOf].getName)) {
        Option {
          fieldAnnotations.collectFirst {
            case (name, params) if name == classOf[GpbName].getName => params("value")
          }.getOrElse(fieldNameUpper)
        }
      } else None
    }

    def newConverterScalaToJavaMap(c: whitebox.Context)
                                  (srcType: c.universe.Type, dstType: c.universe.Type)
                                  (implicit converters: mutable.Map[String, c.Tree]): c.Tree = {
      import c.universe._

      val srcTypeArgs = srcType.typeArgs
      val (srcKeyType, srcValueType) = (srcTypeArgs.head, srcTypeArgs.tail.head)

      val dstTypeArgs = dstType.typeArgs
      val (dstKeyType, dstValueType) = (dstTypeArgs.head, dstTypeArgs.tail.head)

      val keyField = if (typesEqual(c)(srcKeyType, dstKeyType)) {
        q" Good(key) "
      } else {
        newConverter(c)(srcKeyType, dstKeyType) {
          q" (fieldPath: String, a: $srcKeyType) => ${CactusMacros.CaseClassToGpb.processEndType(c)(q"a", c.Expr[String](q"fieldPath"), Map(), srcKeyType)(dstKeyType, q" identity  ", "")} "
        }

        q" CactusMacros.AToB[$srcKeyType, $dstKeyType](fieldPath)(key) "
      }

      val valueField = if (typesEqual(c)(srcValueType, dstValueType)) {
        q" Good(value) "
      } else {
        newConverter(c)(srcValueType, dstValueType) {
          q" (fieldPath: String, a: $srcValueType) => ${CactusMacros.CaseClassToGpb.processEndType(c)(q"a", c.Expr[String](q"fieldPath"), Map(), srcValueType)(dstValueType, q" identity  ", "")} "
        }

        q" CactusMacros.AToB[$srcValueType, $dstValueType](fieldPath)(value) "
      }

      q"""
            (fieldPath: String, sm: $srcType) => {
                sm.map { case (key, value) =>
                   withGood($keyField, $valueField)(_ -> _)
                }.combined.map(_.toMap.asJava)
            }
         """
    }

    def newConverterJavaToScalaMap(c: whitebox.Context)
                                  (srcType: c.universe.Type, dstType: c.universe.Type)
                                  (implicit converters: mutable.Map[String, c.Tree]): c.Tree = {
      import c.universe._

      val srcTypeArgs = srcType.typeArgs
      val (srcKeyType, srcValueType) = (srcTypeArgs.head, srcTypeArgs.tail.head)

      val dstTypeArgs = dstType.typeArgs
      val (dstKeyType, dstValueType) = (dstTypeArgs.head, dstTypeArgs.tail.head)

      val keyField = if (typesEqual(c)(srcKeyType, dstKeyType)) {
        q" Good(key).orBad[Every[com.avast.cactus.CactusFailure]] "
      } else {
        newConverter(c)(srcKeyType, dstKeyType) {
          q" (fieldPath: String, t: $srcKeyType) => ${CactusMacros.GpbToCaseClass.processEndType(c)(TermName("key"), c.Expr[String](q"fieldPath"), Map(), "nameInGpb", dstKeyType)(None, q" t ", srcKeyType)} "
        }

        q" CactusMacros.AToB[$srcKeyType, $dstKeyType](fieldPath)(key) "
      }

      val valueField = if (typesEqual(c)(srcValueType, dstValueType)) {
        q" Good(value).orBad[Every[com.avast.cactus.CactusFailure]] "
      } else {
        newConverter(c)(srcValueType, dstValueType) {
          q" (fieldPath: String, t: $srcValueType) => ${CactusMacros.GpbToCaseClass.processEndType(c)(TermName("key"), c.Expr[String](q"fieldPath"), Map(), "nameInGpb", dstValueType)(None, q" t ", srcValueType)} "
        }

        q" CactusMacros.AToB[$srcValueType, $dstValueType](fieldPath)(value) "
      }

      q"""
            (fieldPath: String, sm: $srcType) => {
                sm.asScala.map { case (key, value) =>
                    $keyField -> $valueField
                }.toSeq.map { case(key, or) => withGood(key, or)(_ -> _) }.combined.map(_.toMap)
            }
         """
    }
  }

}
