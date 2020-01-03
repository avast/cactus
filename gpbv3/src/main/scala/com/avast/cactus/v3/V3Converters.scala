package com.avast.cactus.v3

import java.time.{Duration, Instant}

import com.avast.cactus.Converter
import com.avast.cactus.internal._
import com.google.protobuf.{
  BoolValue,
  ByteString,
  BytesValue,
  DoubleValue,
  Empty,
  FloatValue,
  Int32Value,
  Int64Value,
  ListValue,
  StringValue,
  Struct,
  Duration => GpbDuration,
  Timestamp => GpbTimestamp
}

import scala.collection.compat._
import scala.jdk.CollectionConverters._

trait V3Converters {
  implicit val listValue2SeqConverter: Converter[com.google.protobuf.ListValue, Seq[ValueOneOf]] = Converter.checked {
    (fieldPath, listValue) =>
      listValue.getValuesList.asScala.toList.map(ValueOneOf.apply(fieldPath, _)).combined.map(_.toSeq)
  }

  implicit val seq2ListValueConverter: Converter[Seq[ValueOneOf], com.google.protobuf.ListValue] = Converter { values =>
    ListValue.newBuilder().addAllValues(values.map(ValueOneOf.toGpbValue).asJava).build()
  }

  implicit val struct2MapConverter: Converter[com.google.protobuf.Struct, Map[String, ValueOneOf]] = Converter.checked {
    (fieldPath, struct) =>
      struct.getFieldsMap.asScala.view
        .mapValues(ValueOneOf.apply(fieldPath, _))
        .map { case (key, value) => value.map(key -> _) }
        .toList
        .combined
        .map(_.toMap)
  }

  implicit val map2StructConverter: Converter[Map[String, ValueOneOf], com.google.protobuf.Struct] = Converter { m =>
    Struct.newBuilder().putAllFields(m.view.mapValues(ValueOneOf.toGpbValue).toMap.asJava).build()
  }

  // wrappers to their content type (and back)

  implicit val EmptyToUnit: Converter[Empty, Unit] = Converter(_ => ())
  implicit val UnitToEmpty: Converter[Unit, Empty] = Converter(_ => Empty.getDefaultInstance)

  implicit val doubleValue2Double: Converter[DoubleValue, Double] = Converter(_.getValue)
  implicit val stringValue2String: Converter[StringValue, String] = Converter(_.getValue)
  implicit val floatValue2Float: Converter[FloatValue, Float] = Converter(_.getValue)
  implicit val boolValue2Boolean: Converter[BoolValue, Boolean] = Converter(_.getValue)
  implicit val int64Value2Long: Converter[Int64Value, Long] = Converter(_.getValue)
  implicit val int32Value2Int: Converter[Int32Value, Int] = Converter(_.getValue)
  implicit val bytesValue2ByteString: Converter[BytesValue, ByteString] = Converter(_.getValue)
  implicit val gpbDuration2Duration: Converter[GpbDuration, Duration] = Converter { v =>
    Duration.ofSeconds(v.getSeconds, v.getNanos)
  }
  implicit val gpbTimestamp2Instant: Converter[GpbTimestamp, Instant] = Converter { v =>
    Instant.ofEpochSecond(v.getSeconds, v.getNanos)
  }

  implicit val double2DoubleValue: Converter[Double, DoubleValue] = Converter(DoubleValue.newBuilder().setValue(_).build())
  implicit val string2stringValue: Converter[String, StringValue] = Converter(StringValue.newBuilder().setValue(_).build())
  implicit val float2floatValue: Converter[Float, FloatValue] = Converter(FloatValue.newBuilder().setValue(_).build())
  implicit val boolean2boolValue: Converter[Boolean, BoolValue] = Converter(BoolValue.newBuilder().setValue(_).build())
  implicit val long2int64Value: Converter[Long, Int64Value] = Converter(Int64Value.newBuilder().setValue(_).build())
  implicit val int2int32Value: Converter[Int, Int32Value] = Converter(Int32Value.newBuilder().setValue(_).build())
  implicit val byteString2bytesValue: Converter[ByteString, BytesValue] = Converter(BytesValue.newBuilder().setValue(_).build())
  implicit val duration2gpbDuration: Converter[Duration, GpbDuration] = Converter { d =>
    GpbDuration.newBuilder().setSeconds(d.getSeconds).setNanos(d.getNano).build()
  }
  implicit val instant2gpbTimestamp: Converter[Instant, GpbTimestamp] = Converter { i =>
    GpbTimestamp.newBuilder().setSeconds(i.getEpochSecond).setNanos(i.getNano).build()
  }

  // wrappers to A (and back)

  implicit def doubleValueToAnything[A](implicit c: Converter[Double, A]): Converter[DoubleValue, A] = c.compose[DoubleValue]
  implicit def stringValueToAnything[A](implicit c: Converter[String, A]): Converter[StringValue, A] = c.compose[StringValue]
  implicit def floatValueToAnything[A](implicit c: Converter[Float, A]): Converter[FloatValue, A] = c.compose[FloatValue]
  implicit def boolValueToAnything[A](implicit c: Converter[Boolean, A]): Converter[BoolValue, A] = c.compose[BoolValue]
  implicit def int64ValueToAnything[A](implicit c: Converter[Long, A]): Converter[Int64Value, A] = c.compose[Int64Value]
  implicit def int32ValueToAnything[A](implicit c: Converter[Int, A]): Converter[Int32Value, A] = c.compose[Int32Value]
  implicit def bytesValueToAnything[A](implicit c: Converter[ByteString, A]): Converter[BytesValue, A] = c.compose[BytesValue]

  implicit def anythingToDoubleValue[A](implicit c: Converter[A, Double]): Converter[A, DoubleValue] = c.andThen[DoubleValue]
  implicit def anythingToStringValue[A](implicit c: Converter[A, String]): Converter[A, StringValue] = c.andThen[StringValue]
  implicit def anythingToFloatValue[A](implicit c: Converter[A, Float]): Converter[A, FloatValue] = c.andThen[FloatValue]
  implicit def anythingToBoolValue[A](implicit c: Converter[A, Boolean]): Converter[A, BoolValue] = c.andThen[BoolValue]
  implicit def anythingToInt64Value[A](implicit c: Converter[A, Long]): Converter[A, Int64Value] = c.andThen[Int64Value]
  implicit def anythingToInt32Value[A](implicit c: Converter[A, Int]): Converter[A, Int32Value] = c.andThen[Int32Value]
  implicit def anythingToBytesValue[A](implicit c: Converter[A, ByteString]): Converter[A, BytesValue] = c.andThen[BytesValue]

  // special converters

  implicit def liftToOption[A](implicit converter: AnyValueConverter[A]): Converter[Option[AnyValue], Option[A]] = {
    Converter.checked { (path, a) =>
      a.map(converter.apply(path)) match {
        case Some(result) => result.map(Some(_))
        case None => Right(None)
      }
    }
  }
}
