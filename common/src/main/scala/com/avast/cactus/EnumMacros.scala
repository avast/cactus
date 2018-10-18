package com.avast.cactus
import scala.reflect.macros.whitebox

object EnumMacros {
  def newEnumConverterToSealedTrait(c: whitebox.Context)(
    from: c.universe.Type,
    enumType: FieldType.Enum[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
    import c.universe._
    import enumType._

    val enumClass = getter.returnType.typeSymbol.asClass.companion
    val enumName = enumClass.name.toString

    CactusMacros.init(c)(enumClass)

    if (CactusMacros.Debug) println(s"Generating ENUM converter from $enumClass to ${classType.resultType}, GPB ENUM '$enumName'")

    val implsSeq = impls.toSeq

    val enumValues = implsSeq
      .map { n =>
        CactusMacros
          .splitByUppers(n.name.toString)
          .map(_.toUpperCase)
          .mkString("_")
      }
      .map(TermName(_))

    if (CactusMacros.Debug) println(s"$enumClass values: $enumValues")

    val options = enumValues zip implsSeq

    val cases = options.map {
      case (enumValue, ccl) => cq""" $enumClass.$enumValue => Some(${ccl.module}) """
    }

    val f =
      q""" {
             (fieldPath: String, wholeGpb: $from) => Good(wholeGpb.${getter.name} match {
                case ..$cases
             }): Or[$classType, _root_.com.avast.cactus.EveryCactusFailure]
        }
       """

    if (CactusMacros.Debug) println(s"ENUM converter from $enumClass to ${classType.resultType}: $f")

    f
  }

  def newOneOfConverterToGpb(c: whitebox.Context)(gpbType: c.universe.Type, gpbSetters: Iterable[c.universe.MethodSymbol])(
      oneOfType: FieldType.OneOf[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
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
          .getOrElse(c.abort(c.enclosingPosition, s"Could not locate method $n inside $gpbClassSymbol, needed for ENUM $oneOfTypeSymbol"))
      }

    val fields = implsSeq
      .map { t =>
        t.typeSignature.decls
          .collectFirst {
            case m if m.isMethod && m.asMethod.isPrimaryConstructor => m.asMethod.paramLists.flatten.headOption
          }
          .getOrElse(CactusMacros.terminateWithInfo(c)(s"Could not extract value field name from $t, needed for ENUM $oneOfTypeSymbol"))
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
          CactusMacros.terminateWithInfo(c)(
            s"ENUM trait implementations has to have 'google.protobuf.Empty' as counterpart in GPB; has $setterArgType")
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

    if (CactusMacros.Debug) println(s"ENUM converter from $oneOfTypeSymbol to $gpbClassSymbol: $f")

    f
  }
}
