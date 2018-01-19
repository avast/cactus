package com.avast.cactus.v3

import com.avast.cactus.GpbName
import com.google.protobuf.{ByteString, Message}

import scala.language.experimental.macros

case class AnyValue(typeUrl: String, @GpbName("value") bytes: ByteString)

object AnyValue {
  private val DefaultTypePrefix: String = "type.googleapis.com"

  def of[Gpb <: Message](gpb: Gpb): AnyValue = {
    AnyValue(
      typeUrl = s"$DefaultTypePrefix/${gpb.getDescriptorForType.getFullName}",
      bytes = gpb.toByteString
    )
  }
}
