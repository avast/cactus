package com.avast.cactus
import scala.reflect.macros.whitebox

object EnumMacros {
  def newEnumConverterToSealedTrait(c: whitebox.Context)(protoVersion: ProtoVersion)(
      wholeGpbType: c.universe.Type,
      enumType: FieldType.Enum[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
    import c.universe._
    import enumType._

    val enumClass = getter.returnType.typeSymbol.asClass.companion
    CactusMacros.init(c)(enumClass)

    if (CactusMacros.Debug) println(s"Generating ENUM converter from $enumClass to ${traitType.resultType}")

    val implsSeq = traitImpls.toSeq

    val enumValues = implsSeq
      .map { n =>
        CactusMacros
          .splitByUppers(n.name.toString)
          .map(_.toUpperCase)
          .mkString("_")
      }
      .map(TermName(_))

    if (CactusMacros.Debug) println(s"$enumClass values: $enumValues")

    val cases = protoVersion.createEnumToSealedTraitCases(c)(fieldName, enumClass, enumValues, implsSeq)

    val f =
      q""" {
             (fieldPath: String, wholeGpb: $wholeGpbType) => (wholeGpb.${getter.name} match {
                case ..$cases
             }): Or[$traitType, _root_.com.avast.cactus.EveryCactusFailure]
        }
       """

    if (CactusMacros.Debug) println(s"ENUM converter from $enumClass to ${traitType.resultType}: $f")

    f
  }

  def newEnumConverterToGpb(c: whitebox.Context)(
      enumType: FieldType.Enum[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
    import c.universe._
    import enumType._

    val enumClass = getter.returnType.typeSymbol.asClass.companion
    CactusMacros.init(c)(enumClass)

    if (CactusMacros.Debug) println(s"Generating ENUM converter from ${traitType.resultType} to $enumClass")

    val implsSeq = traitImpls.toSeq

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
      case (enumValue, ccl) => // case object

        cq" _: ${ccl.module} => Good(builder.$setter($enumClass.$enumValue))"
    }

    val f =
      q""" (fieldPath: String, field: $traitType) => {
          field match {
            case ..$cases
          }
        }
       """

    if (CactusMacros.Debug) println(s"ENUM converter from $traitType to $enumClass: $f")

    f
  }
}
