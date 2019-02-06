package com.avast.cactus.v3.test

import java.time.{Duration, Instant}

import com.avast.cactus.v3.TestMessageV3.TestEnum
import com.avast.cactus.v3.{AnyValue, ValueOneOf}
import com.avast.cactus.{GpbIgnored, GpbMap, GpbName, GpbOneOf}
import com.google.protobuf.{
  BoolValue,
  ByteString,
  BytesValue,
  DoubleValue,
  FloatValue,
  Int32Value,
  Int64Value,
  ListValue,
  StringValue,
  Duration => GpbDuration,
  Timestamp => GpbTimestamp
}

import scala.collection.JavaConverters._
import scala.collection.immutable

case class CaseClassA(fieldString: String,
                      @GpbName("fieldIntName")
                      fieldInt: Int,
                      fieldOption: Option[Int],
                      fieldBlob: ByteString,
                      @GpbName("fieldStringsName")
                      fieldStrings2: List[String],
                      fieldGpb: CaseClassB,
                      fieldGpb2: CaseClassB,
                      fieldGpb3: CaseClassF,
                      fieldGpbOption: Option[CaseClassB],
                      fieldGpbOptionEmpty: Option[CaseClassB],
                      fieldGpbRepeated: Seq[CaseClassB],
                      fieldGpb2RepeatedRecurse: Seq[CaseClassD],
                      fieldStrings: immutable.Seq[String],
                      fieldOptionIntegers: Vector[Int],
                      fieldOptionIntegersEmpty: List[Int],
                      @GpbName("fieldIntegers2")
                      fieldIntegersString: String,
                      @GpbMap(key = "key", value = "value")
                      fieldMap: Map[String, String],
                      @GpbName("fieldMap2")
                      @GpbMap(key = "key", value = "value")
                      fieldMapDiffType: Map[String, Int])

case class CaseClassB(fieldDouble: Double, @GpbName("fieldBlob") fieldString: String)

case class CaseClassD(fieldGpb: Seq[CaseClassB], @GpbOneOf @GpbName("NamedOneOf2") oneOfNamed: OneOfNamed3)

case class CaseClassF(fieldGpb: Seq[CaseClassB], @GpbOneOf namedOneOf: Option[OneOfNamed])

case class CaseClassExtensions(boolValue: BoolValue,
                               int32Value: Int32Value,
                               @GpbName("int64Value")
                               longValue: Int64Value,
                               floatValue: Option[FloatValue],
                               doubleValue: Option[DoubleValue],
                               stringValue: Option[StringValue],
                               bytesValue: BytesValue,
                               listValue: ListValue,
                               listValue2: Seq[ValueOneOf],
                               listValue3: Option[Seq[ValueOneOf]],
                               listValue4: Option[Seq[ValueOneOf]],
                               duration: GpbDuration,
                               timestamp: GpbTimestamp,
                               struct: Map[String, ValueOneOf])

case class CaseClassExtensionsScala(boolValue: Boolean,
                                    int32Value: Int,
                                    @GpbName("int64Value")
                                    longValue: Long,
                                    floatValue: Option[Float],
                                    doubleValue: Option[Double],
                                    stringValue: Option[String],
                                    bytesValue: ByteString,
                                    listValue: Seq[ValueOneOf],
                                    duration: Duration,
                                    timestamp: Instant)

case class ExtClass(timestamp: Instant, any: AnyValue)

//case class ExtClassOpt(timestamp: Instant, any: Option[AnyValue])

case class InnerClass(fieldInt: Int, fieldString: String)

sealed trait OneOfNamed

object OneOfNamed {

  case class FooInt(theInt: Int) extends OneOfNamed // to prove the name is not relevant

  case class FooString(value: String) extends OneOfNamed

}

sealed trait OneOfNamed3

object OneOfNamed3 {

  case class FooInt(value: Int) extends OneOfNamed3

  case class FooString(value: String) extends OneOfNamed3

  case class FooBytes(value: String) extends OneOfNamed3

  case object FooEmpty extends OneOfNamed3

}

case class CaseClassC(fieldString: StringWrapperClass,
                      @GpbName("fieldIntName")
                      fieldInt: Int,
                      fieldOption: Option[Int],
                      fieldBlob: ByteString,
                      @GpbName("fieldStringsName")
                      fieldStrings2: Vector[String],
                      fieldGpb: CaseClassB,
                      fieldGpbOption: Option[CaseClassB],
                      fieldGpbOptionEmpty: Option[CaseClassB],
                      fieldStrings: Array[String],
                      fieldOptionIntegers: Seq[Int],
                      fieldOptionIntegersEmpty: Seq[Int],
                      @GpbMap(key = "key", value = "value")
                      fieldMap: Map[String, String]) {

  // needed because of the array
  override def equals(obj: scala.Any): Boolean = obj match {
    case that: CaseClassC =>
      fieldString == that.fieldString &&
        fieldInt == that.fieldInt &&
        fieldOption == that.fieldOption &&
        fieldBlob == that.fieldBlob &&
        fieldStrings2 == that.fieldStrings2 &&
        fieldGpb == that.fieldGpb &&
        fieldGpbOption == that.fieldGpbOption &&
        fieldGpbOptionEmpty == that.fieldGpbOptionEmpty &&
        (fieldStrings sameElements that.fieldStrings) &&
        fieldOptionIntegers == that.fieldOptionIntegers &&
        fieldOptionIntegersEmpty == that.fieldOptionIntegersEmpty &&
        fieldMap == fieldMap

    case _ => false
  }
}

// the `fieldIgnored` field is very unusually placed, but it has to be tested too...
case class CaseClassE(fieldString: String,
                      @GpbIgnored fieldIgnored: String = "hello",
                      fieldOption: Option[String],
                      @GpbIgnored fieldIgnored2: String = "hello")

case class CaseClassG(fieldString: String,
                      fieldOption: Option[String],
                      fieldMap: Map[String, Int],
                      fieldMap2: Map[String, CaseClassMapInnerMessage])

case class CaseClassMapInnerMessage(fieldString: String, fieldInt: Int)

case class StringWrapperClass(value: String)

sealed trait TheEnum

object TheEnum {

  case object Unknown extends TheEnum

  case object One extends TheEnum

  case object Two extends TheEnum

}

case class CaseClassWithEnum(theEnumField: Option[TheEnum])

case class CaseClassWithRawEnum(fieldString: String,
                                fieldEnum: TestEnum,
                                fieldEnumOption: Option[TestEnum],
                                fieldMap: Map[String, TestEnum])

// this is here to prevent reappearing of bug with companion object
object CaseClassA
