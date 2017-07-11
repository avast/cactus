package com.avast.cactus

import com.avast.bytes.Bytes
import com.avast.bytes.gpb.ByteStringBytes
import com.google.protobuf.ByteString

trait OptionalConverters {
  implicit val BytesToByteStringConverter: Converter[Bytes, ByteString] = Converter {
    case b: ByteStringBytes => ByteString.copyFrom(b.toReadOnlyByteBuffer)
    case b: Bytes => ByteString.copyFrom(b.toReadOnlyByteBuffer)
  }

  implicit val ByteStringToBytesConverter: Converter[ByteString, Bytes] = Converter((b: ByteString) => ByteStringBytes.wrap(b))

}
