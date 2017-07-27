package com.avast.cactus

import com.avast.cactus.CactusMacros.{AnnotationsMap, ClassesNames, newConverter, typesEqual}
import org.scalactic.{Every, Or}

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
        CactusMacros.isPrimitive(c)(fieldType) || t.asType.fullName == ClassesNames.Protobuf.ByteString
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
        val ctorType = getCtorParamType(c)(ccl)
        val value = CactusMacros.convertIfNeeded(c)(c.Expr[String](q"fieldPath"),getter.returnType, ctorType)(q"wholeGpb.$getter")

        cq""" $enumClass.$enum => Good(${ccl.companion}.apply($value))  """
      } :+
        cq""" $enumClass.${TermName(name.toUpperCase + "_NOT_SET")} => Bad(One(OneOfValueNotSetFailure($name))) """

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
            case m if m.isMethod && m.asMethod.isPrimaryConstructor => m.asMethod.paramLists.head.head
          }.getOrElse(c.abort(c.enclosingPosition, s"Could not extract value field name from $t, needed for ONE-OF $oneOfTypeSymbol"))
        }

      val options = implsSeq zip (setters zip fields)

      val cases = options.map { case (ccl, (setter, field)) =>
        val fieldName = field.name.toTermName

        val setterArgType = getParamType(c)(setter)

        val value = CactusMacros.convertIfNeeded(c)(c.Expr[String](q"fieldPath"), field.typeSignature, setterArgType)(q"v.$fieldName")

        cq" v: $ccl => builder.$setter($value)"
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
        q" key "
      } else {
        newConverter(c)(srcKeyType, dstKeyType) {
          q" (fieldPath: String, a: $srcKeyType) => ${CactusMacros.CaseClassToGpb.processEndType(c)(q"a", Map(), srcKeyType)(dstKeyType, q" identity  ", "")} "
        }

        q" CactusMacros.AToB[$srcKeyType, $dstKeyType](fieldPath)(key) "
      }

      val valueField = if (typesEqual(c)(srcValueType, dstValueType)) {
        q" value "
      } else {
        newConverter(c)(srcValueType, dstValueType) {
          q" (fieldPath: String, a: $srcValueType) => ${CactusMacros.CaseClassToGpb.processEndType(c)(q"a", Map(), srcValueType)(dstValueType, q" identity  ", "")} "
        }

        q" CactusMacros.AToB[$srcValueType, $dstValueType](fieldPath)(value) "
      }

      q"""
            (fieldPath: String, sm: $srcType) => {
                val map: Map[$dstKeyType, $dstValueType] = sm.map { case (key, value) =>
                    $keyField -> $valueField
                }

                map.asJava
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
        val wrappedDstType = CactusMacros.GpbToCaseClass.wrapDstType(c)(dstKeyType)

        newConverter(c)(srcKeyType, wrappedDstType) {
          q" (fieldPath: String, t: $srcKeyType) => ${CactusMacros.GpbToCaseClass.processEndType(c)(TermName("key"),c.Expr[String](q"fieldPath"), Map(), "nameInGpb", dstKeyType)(None, q" t ", srcKeyType)} "
        }

        q" CactusMacros.AToB[$srcKeyType, $wrappedDstType](fieldPath)(key) "
      }

      val valueField = if (typesEqual(c)(srcValueType, dstValueType)) {
        q" Good(value).orBad[Every[com.avast.cactus.CactusFailure]] "
      } else {
        val wrappedDstType = CactusMacros.GpbToCaseClass.wrapDstType(c)(dstValueType)

        newConverter(c)(srcValueType, wrappedDstType) {
          q" (fieldPath: String, t: $srcValueType) => ${CactusMacros.GpbToCaseClass.processEndType(c)(TermName("key"),c.Expr[String](q"fieldPath"), Map(), "nameInGpb", dstValueType)(None, q" t ", srcValueType)} "
        }

        q" CactusMacros.AToB[$srcValueType, $wrappedDstType](fieldPath)(value) "
      }

      q"""
            (fieldPath: String, sm: $srcType) => {
                sm.asScala.map { case (key, value) =>
                    $keyField -> $valueField
                }.toSeq.map{ case(key, or) => withGood(key, or)(_ -> _) }.combined.map(_.toMap)
            }
         """
    }

    def tryParseAny[Gpb: c.WeakTypeTag](c: whitebox.Context): c.Expr[Gpb Or Every[CactusFailure]] = {
      import c.universe._

      val dstClassSymbol = weakTypeOf[Gpb]

      val variable = CactusMacros.getVariable[Gpb](c)

      val gpbTypeName = q"${dstClassSymbol.companion}.getDefaultInstance.getDescriptorForType.getFullName"

      // TODO what if parsing fails

      c.Expr[Gpb Or Every[CactusFailure]] {
        q"""
            {
               import com.avast.cactus.CactusFailure
               import com.avast.cactus.CactusMacros._

               import org.scalactic._
               import org.scalactic.Accumulation._

               import scala.util.Try
               import scala.collection.JavaConverters._

               if ($variable.typeUrl == "type.googleapis.com/" +$gpbTypeName) {
                  Good(${dstClassSymbol.companion}.parseFrom($variable.bytes))
               } else {
                  Bad(One(WrongAnyTypeFailure($variable.typeUrl, "type.googleapis.com/" + $gpbTypeName)))
               }

            }
         """
      }
    }

    private def splitByUppers(s: String): Array[String] = {
      s.split("(?=\\p{Upper})")
    }
  }

  private def getParamType(c: whitebox.Context)(setter: c.universe.MethodSymbol): c.universe.Type = {
    setter.paramLists.head.head.typeSignature
  }

  private def getCtorParamType(c: whitebox.Context)(ccl: c.universe.ClassSymbol): c.universe.Type = {
    ccl.typeSignature.decls.collectFirst {
      case m if m.isMethod && m.asMethod.isPrimaryConstructor => getParamType(c)(m.asMethod)
    }.getOrElse(c.abort(c.enclosingPosition, s"Could not locate parameter of ctor for $ccl, it is probably a bug"))
  }
}
