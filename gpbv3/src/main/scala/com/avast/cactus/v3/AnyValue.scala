package com.avast.cactus.v3

import com.avast.cactus.{Converter, GpbName, ResultOrErrors}
import com.google.protobuf.{ByteString, Message}

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

case class AnyValue(typeUrl: String, @GpbName("value") bytes: ByteString)

object AnyValue {
  private val DefaultTypePrefix: String = "type.googleapis.com"

  def of(gpb: Message): AnyValue = {
    AnyValue(
      typeUrl = s"$DefaultTypePrefix/${gpb.getDescriptorForType.getFullName}",
      bytes = gpb.toByteString
    )
  }

  def of[Gpb <: Message, CaseClass](caseClass: CaseClass)(implicit converter: Converter[CaseClass, Gpb]): ResultOrErrors[AnyValue] = {
    converter.apply("_")(caseClass).map(of)
  }

  implicit def anyValueConverterFromGpb[Gpb <: Message]: Converter[Gpb, AnyValue] = macro generateAnyValueConverterFromGpb[Gpb]

  def generateAnyValueConverterFromGpb[Gpb <: Message : c.WeakTypeTag](c: whitebox.Context): c.Expr[Converter[Gpb, AnyValue]] = {
    import c.universe._

    val gpbType = weakTypeOf[Gpb]

    // this macro is meant for user GPB classes, not for Google ones (Any, Empty, ...) - fail the macro if it's the forbidden case
    if (gpbType.typeSymbol.fullName.startsWith("com.google.protobuf")) c.abort(c.enclosingPosition, s"Cannot convert $gpbType to AnyValue")

    c.Expr[Converter[Gpb, AnyValue]]{
      q"com.avast.cactus.Converter[$gpbType, com.avast.cactus.v3.AnyValue](com.avast.cactus.v3.AnyValue.of)"
    }
  }
}
