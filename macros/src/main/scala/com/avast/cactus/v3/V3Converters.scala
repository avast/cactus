package com.avast.cactus.v3

import java.time.{Duration, Instant}

import com.avast.cactus.Converter
import com.google.protobuf.{Duration => GpbDuration, Timestamp => GpbTimestamp, _}
import org.scalactic.Accumulation._

import scala.collection.JavaConverters._

trait V3Converters {
  implicit lazy val listValue2SeqConverter: Converter[com.google.protobuf.ListValue, Seq[ValueOneOf]] = Converter.checked { (fieldPath, listValue) =>
    listValue.getValuesList.asScala.map(ValueOneOf.apply(fieldPath, _)).toIterable.combined.map(_.toSeq)
  }

  implicit lazy val seq2ListValueConverter: Converter[Seq[ValueOneOf], com.google.protobuf.ListValue] = Converter { values =>
    ListValue.newBuilder().addAllValues(values.map(ValueOneOf.toGpbValue).asJava).build()
  }

  implicit lazy val struct2MapConverter: Converter[com.google.protobuf.Struct, Map[String, ValueOneOf]] = Converter.checked { (fieldPath, struct) =>
    struct.getFieldsMap.asScala.mapValues(ValueOneOf.apply(fieldPath, _)).map { case (key, value) => value.map(key -> _) }.combined.map(_.toMap)
  }

  implicit lazy val map2StructConverter: Converter[Map[String, ValueOneOf], com.google.protobuf.Struct] = Converter { m =>
    Struct.newBuilder().putAllFields(m.mapValues(ValueOneOf.toGpbValue).asJava).build()
  }

  implicit lazy val doubleValue2Double: Converter[DoubleValue, Double] = Converter(_.getValue)
  implicit lazy val stringValue2String: Converter[StringValue, String] = Converter(_.getValue)
  implicit lazy val floatValue2Float: Converter[FloatValue, Float] = Converter(_.getValue)
  implicit lazy val boolValue2Boolean: Converter[BoolValue, Boolean] = Converter(_.getValue)
  implicit lazy val int64Value2Long: Converter[Int64Value, Long] = Converter(_.getValue)
  implicit lazy val int32Value2Int: Converter[Int32Value, Int] = Converter(_.getValue)
  implicit lazy val bytesValue2ByteString: Converter[BytesValue, ByteString] = Converter(_.getValue)
  implicit lazy val gpbDuration2Duration: Converter[GpbDuration, Duration] = Converter { v => Duration.ofSeconds(v.getSeconds, v.getNanos) }
  implicit lazy val gpbTimestamp2Instant: Converter[GpbTimestamp, Instant] = Converter { v => Instant.ofEpochSecond(v.getSeconds, v.getNanos) }

  implicit lazy val double2DoubleValue: Converter[Double, DoubleValue] = Converter(DoubleValue.newBuilder().setValue(_).build())
  implicit lazy val string2stringValue: Converter[String, StringValue] = Converter(StringValue.newBuilder().setValue(_).build())
  implicit lazy val float2floatValue: Converter[Float, FloatValue] = Converter(FloatValue.newBuilder().setValue(_).build())
  implicit lazy val boolean2boolValue: Converter[Boolean, BoolValue] = Converter(BoolValue.newBuilder().setValue(_).build())
  implicit lazy val long2int64Value: Converter[Long, Int64Value] = Converter(Int64Value.newBuilder().setValue(_).build())
  implicit lazy val int2int32Value: Converter[Int, Int32Value] = Converter(Int32Value.newBuilder().setValue(_).build())
  implicit lazy val byteString2bytesValue: Converter[ByteString, BytesValue] = Converter(BytesValue.newBuilder().setValue(_).build())
  implicit lazy val duration2gpbDuration: Converter[Duration, GpbDuration] = Converter { d =>
    GpbDuration.newBuilder().setSeconds(d.getSeconds).setNanos(d.getNano).build()
  }
  implicit lazy val instant2gpbTimestamp: Converter[Instant, GpbTimestamp] = Converter { i =>
    GpbTimestamp.newBuilder().setSeconds(i.getEpochSecond).setNanos(i.getNano).build()
  }
}
