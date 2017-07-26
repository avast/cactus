package com.avast.cactus

import com.avast.bytes.Bytes
import com.avast.bytes.gpb.ByteStringBytes
import com.google.protobuf.{ByteString, BytesValue}

trait OptionalConverters {
  implicit val BytesToByteStringConverter: Converter[Bytes, ByteString] = Converter {_ =>{
    case b: ByteStringBytes => b.underlying()
    case b: Bytes => ByteString.copyFrom(b.toReadOnlyByteBuffer)}
  }

  implicit val ByteStringToBytesConverter: Converter[ByteString, Bytes] = Converter(_ => (b: ByteString) => ByteStringBytes.wrap(b))

  // v3:

  implicit val bytes2bytesValue: Converter[Bytes, BytesValue] = Converter {_ =>{
    case b: ByteStringBytes => BytesValue.newBuilder().setValue(b.underlying()).build()
    case b: Bytes => BytesValue.newBuilder().setValue(ByteString.copyFrom(b.toReadOnlyByteBuffer)).build()}
  }

  implicit val bytesValue2Bytes: Converter[BytesValue, Bytes] = Converter(_ =>v => ByteStringBytes.wrap(v.getValue))

}
