package com.avast.cactus.v3

import com.avast.cactus.{Converter, GpbName, ResultOrErrors}
import com.google.protobuf.{ByteString, Message}

import scala.language.experimental.macros

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
}
