package com.avast.cactus

import com.avast.bytes.Bytes
import com.avast.bytes.gpb.ByteStringBytes
import com.google.protobuf.ByteString

package object bytes {
  implicit val BytesToByteStringConverter: Converter[Bytes, ByteString] = Converter {
    case b: ByteStringBytes => b.underlying()
    case b: Bytes => ByteString.copyFrom(b.toReadOnlyByteBuffer)
  }

  implicit val ByteStringToBytesConverter: Converter[ByteString, Bytes] = Converter((b: ByteString) => ByteStringBytes.wrap(b))

}
