package com.avast.cactus

import com.avast.bytes.Bytes
import com.avast.bytes.gpb.ByteStringBytes
import com.google.protobuf.{ByteString, BytesValue}

package object bytes {
  implicit lazy val bytes2bytesValue: Converter[Bytes, BytesValue] = Converter {
    case b: ByteStringBytes => BytesValue.newBuilder().setValue(b.underlying()).build()
    case b: Bytes => BytesValue.newBuilder().setValue(ByteString.copyFrom(b.toReadOnlyByteBuffer)).build()
  }

  implicit lazy val bytesValue2Bytes: Converter[BytesValue, Bytes] = Converter(v => ByteStringBytes.wrap(v.getValue))

}
