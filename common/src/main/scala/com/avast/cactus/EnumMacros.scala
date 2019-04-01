package com.avast.cactus
import scala.reflect.macros.whitebox

object EnumMacros {
  def newEnumConverterToSealedTrait(c: whitebox.Context)(protoVersion: ProtoVersion)(
      fieldName: String,
      gpbEnumType: c.universe.Type,
      traitType: c.universe.Type,
      traitImpls: Set[c.universe.ClassSymbol]
  ): c.Tree = {
    import c.universe._

    val enumClass = gpbEnumType.typeSymbol.asClass.companion
    CactusMacros.init(c)(enumClass)

    if (CactusMacros.Debug) println(s"Generating ENUM converter from ${enumClass.fullName} to ${traitType.resultType}")

    val implsSeq = traitImpls.toSeq

    val expectedEnumValues = implsSeq
      .map { n =>
        CactusMacros
          .splitByUppers(n.name.toString)
          .map(_.toUpperCase)
          .mkString("_")
      }
      .map(TermName(_))

    val enumValues = CactusMacros.getJavaEnumImpls(c)(gpbEnumType.typeSymbol.asClass).map(_.name.toTermName)

    if (expectedEnumValues.toSet != enumValues.-(TermName("UNRECOGNIZED"))) { // skip potential UNRECOGNIZED value (GPB3)
      CactusMacros.terminateWithInfo(c)(
        s"""${enumClass.fullName} cannot be mapped to ${traitType.typeSymbol.fullName}; are those two types equal?
           |Expected values:  ${expectedEnumValues.map(_.toTermName).mkString(", ")}
           |Actual values:    ${enumValues.map(_.toTermName).mkString(", ")}""".stripMargin
      )
    }

    val cases = protoVersion.createEnumToSealedTraitCases(c)(fieldName, enumClass, expectedEnumValues, implsSeq)

    val f =
      q""" {
             (fieldPath: _root_.scala.Predef.String, gpbEnum: $gpbEnumType) => (gpbEnum match {
                case ..$cases
             }): _root_.org.scalactic.Or[$traitType, _root_.com.avast.cactus.EveryCactusFailure]
        }
       """

    if (CactusMacros.Debug) println(s"ENUM converter from $enumClass to ${traitType.resultType}:\n$f")

    f
  }

  def newEnumConverterToSealedTraitFromEnumType(c: whitebox.Context)(protoVersion: ProtoVersion)(
      enumType: FieldType.Enum[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
    import c.universe._
    import enumType._

    newEnumConverterToSealedTrait(c)(protoVersion)(fieldName, getter.returnType, traitType, traitImpls)
  }

  def newEnumConverterToGpb(c: whitebox.Context)(fieldName: String,
                                                 gpbEnumType: c.universe.Type,
                                                 traitType: c.universe.Type,
                                                 traitImpls: Set[c.universe.ClassSymbol]): c.Tree = {
    import c.universe._

    val enumClass = gpbEnumType.typeSymbol.asClass.companion
    CactusMacros.init(c)(enumClass)

    if (CactusMacros.Debug) println(s"Generating ENUM converter from ${traitType.resultType} to $enumClass")

    val implsSeq = traitImpls.toSeq

    val expectedEnumValues = implsSeq
      .map { n =>
        CactusMacros
          .splitByUppers(n.name.toString)
          .map(_.toUpperCase)
          .mkString("_")
      }
      .map(TermName(_))

    val enumValues = CactusMacros.getJavaEnumImpls(c)(gpbEnumType.typeSymbol.asClass).map(_.name.toTermName)

    if (expectedEnumValues.toSet != enumValues.-(TermName("UNRECOGNIZED"))) { // skip potential UNRECOGNIZED value (GPB3)
      CactusMacros.terminateWithInfo(c)(
        s"""${traitType.typeSymbol.fullName} cannot be mapped to ${enumClass.fullName}; are those two types equal?
           |Expected values:  ${expectedEnumValues.map(_.toTermName).mkString(", ")}
           |Actual values:    ${enumValues.map(_.toTermName).mkString(", ")}
           |""".stripMargin
      )
    }

    val options = expectedEnumValues zip implsSeq

    val cases = options.map {
      case (enumValue, ccl) => // case object

        cq" _: ${ccl.module} => Good($enumClass.$enumValue)"
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

  def newEnumConverterToGpbFromEnumType(c: whitebox.Context)(
      enumType: FieldType.Enum[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
    import c.universe._
    import enumType._

    newEnumConverterToGpb(c)(fieldName, getter.returnType, traitType, traitImpls)
  }
}
