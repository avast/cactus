package com.avast.cactus.v3

import com.avast.cactus.ResultOrErrors
import com.avast.cactus.internal._
import com.google.protobuf.{Struct, Value, ListValue => GpbListValue, NullValue => GpbNullValue}

import scala.collection.compat._
import scala.jdk.CollectionConverters._

trait ValueOneOf

object ValueOneOf {

  private[cactus] def apply(fieldPath: String, v: Value): ResultOrErrors[ValueOneOf] = v.getKindCase match {
    case Value.KindCase.KIND_NOT_SET => Right(EmptyValue)
    case Value.KindCase.NULL_VALUE => Right(NullValue(v.getNullValue))
    case Value.KindCase.NUMBER_VALUE => Right(NumberValue(v.getNumberValue))
    case Value.KindCase.STRING_VALUE => Right(StringValue(v.getStringValue))
    case Value.KindCase.BOOL_VALUE => Right(BooleanValue(v.getBoolValue))
    case Value.KindCase.LIST_VALUE => listValue2SeqConverter(fieldPath)(v.getListValue).map(ListValue)
    case Value.KindCase.STRUCT_VALUE =>
      val scalaMap = v.getStructValue.getFieldsMap.asScala
      scalaMap
        .map { case (key, value) => ValueOneOf.apply(fieldPath, value).map(key -> _) }
        .toList
        .combined
        .map(_.toMap)
        .map(StructValue)
  }

  private[cactus] def toGpbValue(vof: ValueOneOf): Value = vof match {
    case EmptyValue => Value.newBuilder().clear().build()
    case NullValue(v) => Value.newBuilder().setNullValue(v).build()
    case NumberValue(v) => Value.newBuilder().setNumberValue(v).build()
    case StringValue(v) => Value.newBuilder().setStringValue(v).build()
    case BooleanValue(v) => Value.newBuilder().setBoolValue(v).build()
    case StructValue(v) =>
      Value.newBuilder().setStructValue(Struct.newBuilder().putAllFields(v.view.mapValues(toGpbValue).toMap.asJava)).build()
    case ListValue(v) => Value.newBuilder().setListValue(GpbListValue.newBuilder().addAllValues(v.map(toGpbValue).asJava)).build()
  }

  case object EmptyValue extends ValueOneOf

  case class NullValue(value: GpbNullValue) extends ValueOneOf

  case class NumberValue(value: Double) extends ValueOneOf

  case class StringValue(value: String) extends ValueOneOf

  case class BooleanValue(value: Boolean) extends ValueOneOf

  case class StructValue(value: Map[String, ValueOneOf]) extends ValueOneOf

  case class ListValue(value: Seq[ValueOneOf]) extends ValueOneOf

}
