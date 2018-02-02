package com.avast.cactus.v3.test

import java.time.{Duration, Instant}

import com.avast.cactus._
import com.avast.cactus.v3._
import com.avast.cactus.v3.TestMessageV3
import com.avast.cactus.v3.TestMessageV3._
import com.avast.cactus.v3.ValueOneOf.NumberValue
import com.google.protobuf.{Any, BoolValue, ByteString, BytesValue, DoubleValue, FloatValue, Int32Value, Int64Value, InvalidProtocolBufferException, ListValue, StringValue, Struct, Value, Duration => GpbDuration, Timestamp => GpbTimestamp}
import org.scalactic.{Bad, Good, One}
import org.scalatest.FunSuite

import scala.collection.JavaConverters._
import scala.collection.immutable

class CactusMacrosTestV3 extends FunSuite {

  // user specified converters
  implicit val StringToByteStringConverter: Converter[String, ByteString] = Converter((b: String) => ByteString.copyFromUtf8(b))
  implicit val ByteStringToStringConverter: Converter[ByteString, String] = Converter((b: ByteString) => b.toStringUtf8)

  implicit val StringWrapperToStringConverter: Converter[StringWrapperClass, String] = Converter((b: StringWrapperClass) => b.value)
  implicit val StringToStringWrapperConverter: Converter[String, StringWrapperClass] = Converter((b: String) => StringWrapperClass(b))

  implicit val JavaIntegerListStringConverter: Converter[java.util.List[Integer], String] = Converter(_.asScala.mkString(", "))
  implicit val StringJavaIntegerListConverter: Converter[String, java.lang.Iterable[_ <: Integer]] = Converter(_.split(", ").map(_.toInt).map(int2Integer).toSeq.asJava)

  implicit val StringIntConverter: Converter[String, Int] = Converter(_.toInt)
  implicit val IntStringConverter: Converter[Int, String] = Converter(_.toString)

  // these are not needed, but they are here to be sure it won't cause trouble to the user
  implicit val ByteArrayToByteStringConverter: Converter[Array[Byte], ByteString] = Converter((b: Array[Byte]) => ByteString.copyFrom(b))
  implicit val ByteStringToByteArrayConverter: Converter[ByteString, Array[Byte]] = Converter((b: ByteString) => b.toByteArray)

  test("GPB to case class") {
    val text = "textěščřžýáíé"

    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8(text))
      .build()

    val map = Map("first" -> "1", "second" -> "2")
    val map2 = Map("first" -> 1, "second" -> 2)

    val dataRepeated = Seq(gpbInternal, gpbInternal, gpbInternal)

    val gpb = TestMessageV3.Data.newBuilder()
      .setFieldString("ahoj")
      .setFieldIntName(9)
      //.setFieldOption(13) -> will have 0 value
      .setFieldBlob(ByteString.EMPTY)
      .setFieldGpb(gpbInternal)
      .setFieldGpb2(gpbInternal)
      .setFieldGpb3(Data5.newBuilder().addAllFieldGpb(dataRepeated.asJava).build())
      .setFieldGpbOption(gpbInternal)
      .addAllFieldGpbRepeated(dataRepeated.asJava)
      .addFieldGpb2RepeatedRecurse(Data3.newBuilder().addAllFieldGpb(dataRepeated.asJava).setFooInt(9).build())
      .addAllFieldStrings(Seq("a", "b").asJava)
      .addAllFieldStringsName(Seq("a").asJava)
      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
      .addAllFieldIntegers2(Seq(1, 2).map(int2Integer).asJava)
      .addAllFieldMap(map.map { case (key, value) => TestMessageV3.MapMessage.newBuilder().setKey(key).setValue(value).build() }.asJava)
      .addAllFieldMap2(map.map { case (key, value) => TestMessageV3.MapMessage.newBuilder().setKey(key).setValue(value.toString).build() }.asJava)
      .build()

    val caseClassB = CaseClassB(0.9, text)

    val caseClassD = Seq(CaseClassD(Seq(caseClassB, caseClassB, caseClassB), OneOfNamed2.FooInt(9)))
    val caseClassF = CaseClassF(Seq(caseClassB, caseClassB, caseClassB), None)

    val expected = CaseClassA("ahoj", 9, Some(0), ByteString.EMPTY, List("a"), caseClassB, caseClassB, caseClassF, Some(caseClassB), None, Seq(caseClassB, caseClassB, caseClassB), caseClassD, List("a", "b"), Vector(3, 6), List(), "1, 2", map, map2)

    assertResult(Good(expected))(gpb.asCaseClass[CaseClassA])
  }

  test("GPB to case class multiple failures") {
    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    // fields commented out are REQUIRED
    val gpb = TestMessageV3.Data.newBuilder()
      .setFieldOption(13)
      .setFieldBlob(ByteString.EMPTY)
      //      .setFieldGpb(gpbInternal)
      .setFieldGpbOption(gpbInternal)
      .addAllFieldStringsName(Seq("a").asJava)
      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
      .addFieldGpb2RepeatedRecurse(Data3.newBuilder().build())
      .setFieldGpb3(Data5.newBuilder().build())
      .build()

    val expected = List("gpb.fieldGpb", "gpb.fieldGpb2").map(MissingFieldFailure).sortBy(_.toString) :+ OneOfValueNotSetFailure("gpb.fieldGpb2RepeatedRecurse.NamedOneOf")

    gpb.asCaseClass[CaseClassA] match {
      case Bad(e) =>
        assertResult(expected)(e.toList.sortBy(_.toString))

      case Good(_) => fail("Should fail")
    }
  }

  test("Case class to GPB") {
    val map = Map("first" -> "1", "second" -> "2")
    val map2 = Map("first" -> 1, "second" -> 2)

    val caseClassB = CaseClassB(0.9, "text")

    val caseClassD = Seq(CaseClassD(Seq(caseClassB, caseClassB, caseClassB), OneOfNamed2.FooInt(9)))
    val caseClassF = CaseClassF(Seq(caseClassB, caseClassB, caseClassB), None)

    val caseClass = CaseClassA("ahoj", 9, Some(13), ByteString.EMPTY, List("a"), caseClassB, caseClassB, caseClassF, Some(caseClassB), None, Seq(caseClassB, caseClassB, caseClassB), caseClassD, List("a", "b"), Vector(3, 6), List(), "1, 2", map, map2)

    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    val dataRepeated = Seq(gpbInternal, gpbInternal, gpbInternal)

    val expectedGpb = TestMessageV3.Data.newBuilder()
      .setFieldString("ahoj")
      .setFieldIntName(9)
      .setFieldOption(13)
      .setFieldBlob(ByteString.EMPTY)
      .setFieldGpb(gpbInternal)
      .setFieldGpb2(gpbInternal)
      .setFieldGpb3(Data5.newBuilder().addAllFieldGpb(dataRepeated.asJava).build())
      .setFieldGpbOption(gpbInternal)
      .addAllFieldGpbRepeated(dataRepeated.asJava)
      .addFieldGpb2RepeatedRecurse(Data3.newBuilder().addAllFieldGpb(dataRepeated.asJava).setFooInt(9).build())
      .addAllFieldStrings(Seq("a", "b").asJava)
      .addAllFieldStringsName(Seq("a").asJava)
      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
      .addAllFieldMap(map.map { case (key, value) => TestMessageV3.MapMessage.newBuilder().setKey(key).setValue(value).build() }.asJava)
      .addAllFieldMap2(map2.map { case (key, value) => TestMessageV3.MapMessage.newBuilder().setKey(key).setValue(value.toString).build() }.asJava)
      .addAllFieldIntegers2(Seq(1, 2).map(int2Integer).asJava)
      .build()

    caseClass.asGpb[Data] match {
      case Good(e) if e == expectedGpb => // ok
    }
  }

  test("convert case class to GPB and back") {
    val map = Map("first" -> "1", "second" -> "2")

    val original = CaseClassC(StringWrapperClass("ahoj"), 9, Some(13), ByteString.EMPTY, Vector("a"), CaseClassB(0.9, "text"), Some(CaseClassB(0.9, "text")), None, Array("a", "b"), Vector(3, 6), List(), map)

    val Good(converted) = original.asGpb[Data]

    assertResult(Good(original))(converted.asCaseClass[CaseClassC])
  }

  test("convert case class with ignored field to GPB and back") {
    val original = CaseClassE(fieldString = "ahoj", fieldOption = Some("ahoj2"))

    val Good(converted) = original.asGpb[Data4]

    assertResult(Good(original))(converted.asCaseClass[CaseClassE])
  }

  test("gpb3 map to GPB and back") {
    val original = CaseClassG(fieldString = "ahoj",
      fieldOption = Some("ahoj2"),
      fieldMap = Map("one" -> 1, "two" -> 2),
      fieldMap2 = Map("one" -> CaseClassMapInnerMessage("str", 42))
    )

    val Good(converted) = original.asGpb[Data4]

    assertResult(Good(original))(converted.asCaseClass[CaseClassG])
  }

  test("extensions from GPB and back") {
    val gpb = ExtensionsMessage.newBuilder()
      .setBoolValue(BoolValue.newBuilder().setValue(true))
      .setInt32Value(Int32Value.newBuilder().setValue(123))
      .setInt64Value(Int64Value.newBuilder().setValue(456))
      .setFloatValue(FloatValue.newBuilder().setValue(123.456f))
      .setBytesValue(BytesValue.newBuilder().setValue(ByteString.copyFromUtf8("+ěščřžýáíé")))
      .setDuration(GpbDuration.newBuilder().setSeconds(123).setNanos(456))
      .setTimestamp(GpbTimestamp.newBuilder().setSeconds(123).setNanos(456))
      .setListValue(ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(456.789)))
      .setListValue2(ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(456.789)))
      .setListValue3(ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(456.789)))
      .setStruct(Struct.newBuilder().putFields("mapKey", Value.newBuilder().setNumberValue(42).build()))
      .build()

    val expected = CaseClassExtensions(
      boolValue = BoolValue.newBuilder().setValue(true).build(),
      int32Value = Int32Value.newBuilder().setValue(123).build(),
      longValue = Int64Value.newBuilder().setValue(456).build(),
      floatValue = Some(FloatValue.newBuilder().setValue(123.456f).build()),
      doubleValue = None,
      stringValue = None,
      bytesValue = BytesValue.newBuilder().setValue(ByteString.copyFromUtf8("+ěščřžýáíé")).build(),
      duration = GpbDuration.newBuilder().setSeconds(123).setNanos(456).build(),
      timestamp = GpbTimestamp.newBuilder().setSeconds(123).setNanos(456).build(),
      listValue = ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(456.789)).build(),
      listValue2 = Seq(NumberValue(456.789)),
      listValue3 = Some(Seq(NumberValue(456.789))),
      listValue4 = None,
      struct = Map("mapKey" -> NumberValue(42))
    )

    val Good(converted) = gpb.asCaseClass[CaseClassExtensions]

    assertResult(expected)(converted)

    assertResult(Good(gpb))(converted.asGpb[ExtensionsMessage])
  }

  test("extensions from GPB and back - scala types") {
    val gpb = ExtensionsMessage.newBuilder()
      .setBoolValue(BoolValue.newBuilder().setValue(true))
      .setInt32Value(Int32Value.newBuilder().setValue(123))
      .setInt64Value(Int64Value.newBuilder().setValue(456))
      .setFloatValue(FloatValue.newBuilder().setValue(123.456f))
      .setBytesValue(BytesValue.newBuilder().setValue(ByteString.copyFromUtf8("+ěščřžýáíé")))
      .setDuration(GpbDuration.newBuilder().setSeconds(123).setNanos(456))
      .setTimestamp(GpbTimestamp.newBuilder().setSeconds(123).setNanos(456))
      .setListValue(ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(456.789)))
      .build()

    val expected = CaseClassExtensionsScala(
      boolValue = true,
      int32Value = 123,
      longValue = 456,
      floatValue = Some(123.456f),
      doubleValue = None,
      stringValue = None,
      bytesValue = ByteString.copyFromUtf8("+ěščřžýáíé"),
      duration = Duration.ofSeconds(123, 456),
      timestamp = Instant.ofEpochSecond(123, 456),
      listValue = Seq(NumberValue(456.789))
    )

    val Good(converted) = gpb.asCaseClass[CaseClassExtensionsScala]

    assertResult(expected)(converted)

    assertResult(Good(gpb))(converted.asGpb[ExtensionsMessage])
  }

  test("any extension to GPB and back") {
    val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

    val orig = ExtClass(Instant.ofEpochSecond(12345), AnyValue.of(innerMessage))

    val expected = ExtensionsMessage.newBuilder().setTimestamp(GpbTimestamp.newBuilder().setSeconds(12345)).setAny(Any.pack(innerMessage)).build()

    val Good(converted) = orig.asGpb[ExtensionsMessage]

    assertResult(expected)(converted)

    val actual = converted.asCaseClass[ExtClass]

    assertResult(Good(orig))(actual)
    assertResult(Good(innerMessage))(actual.flatMap(_.any.asGpb[MessageInsideAnyField]))
  }

  test("any extension failure when parsing trash") {
    val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

    val anyValue = AnyValue.of(innerMessage).copy(bytes = ByteString.copyFromUtf8("+ěščřžýáííé")) // damaged data

    val Bad(One(UnknownFailure(fieldPath, cause))) = anyValue.asGpb[MessageInsideAnyField]

    assertResult("anyValue")(fieldPath)
    assert(cause.isInstanceOf[InvalidProtocolBufferException])

    //

    val gpb = ExtensionsMessage.newBuilder()
      .setTimestamp(GpbTimestamp.newBuilder().setSeconds(12345))
      .setAny(Any.pack(innerMessage).toBuilder.setValue(ByteString.copyFromUtf8("+ěščřžýáííé")).build())
      .build()

    val cc = gpb.asCaseClass[ExtClass]

    assertResult(Good(ExtClass(Instant.ofEpochSecond(12345), `anyValue`)))(cc)

    val Bad(One(UnknownFailure(fieldPath2, cause2))) = cc.flatMap(_.any.asGpb[MessageInsideAnyField])

    assertResult("any")(fieldPath2)
    assert(cause2.isInstanceOf[InvalidProtocolBufferException])
  }
}

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

case class CaseClassD(fieldGpb: Seq[CaseClassB], @GpbOneOf @GpbName("NamedOneOf") oneOfNamed: OneOfNamed2)

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

sealed trait OneOfNamed

object OneOfNamed {

  case class FooInt(value: Int) extends OneOfNamed

  case class FooString(value: String) extends OneOfNamed

}


sealed trait OneOfNamed2

object OneOfNamed2 {

  case class FooInt(value: Int) extends OneOfNamed2

  case class FooString(value: String) extends OneOfNamed2

  case class FooBytes(value: String) extends OneOfNamed2

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
case class CaseClassE(fieldString: String, @GpbIgnored fieldIgnored: String = "hello", fieldOption: Option[String], @GpbIgnored fieldIgnored2: String = "hello")

case class CaseClassG(fieldString: String,
                      fieldOption: Option[String],
                      fieldMap: Map[String, Int],
                      fieldMap2: Map[String, CaseClassMapInnerMessage])

case class CaseClassMapInnerMessage(fieldString: String, fieldInt: Int)

case class StringWrapperClass(value: String)

object CaseClassA

// this is here to prevent reappearing of bug with companion object
