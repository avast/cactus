package com.avast.cactus.v2

import com.avast.bytes.Bytes
import com.avast.cactus.TestMessageV2.MapMessage.MapInnerMessage
import com.avast.cactus.TestMessageV2._
import com.avast.cactus._
import com.google.protobuf.ByteString
import org.scalactic.{Bad, Good}
import org.scalatest.FunSuite

import scala.collection.JavaConverters._
import scala.collection.immutable

class CactusMacrosTestV2 extends FunSuite {

  // user specified converters
  implicit val StringToByteStringConverter: Converter[String, ByteString] = Converter(_ =>(b: String) => ByteString.copyFromUtf8(b))
  implicit val ByteStringToStringConverter: Converter[ByteString, String] = Converter(_ =>(b: ByteString) => b.toStringUtf8)

  implicit val StringWrapperToStringConverter: Converter[StringWrapperClass, String] = Converter(_ =>(b: StringWrapperClass) => b.value)
  implicit val StringToStringWrapperConverter: Converter[String, StringWrapperClass] = Converter(_ =>(b: String) => StringWrapperClass(b))

  implicit val JavaIntegerListStringConverter: Converter[java.util.List[Integer], String] = Converter(_ =>_.asScala.mkString(", "))
  implicit val StringJavaIntegerListConverter: Converter[String, java.lang.Iterable[_ <: Integer]] = Converter(_ =>_.split(", ").map(_.toInt).map(int2Integer).toSeq.asJava)

  implicit val MapInnerMessageIntConverter: Converter[MapInnerMessage, Int] = Converter(_ =>_.getFieldInt)
  implicit val IntMapInnerMessageConverter: Converter[Int, MapInnerMessage] = Converter(_ =>MapInnerMessage.newBuilder().setFieldString("str").setFieldInt(_).build())

  // these are not needed, but they are here to be sure it won't cause trouble to the user
  implicit val ByteArrayToByteStringConverter: Converter[Array[Byte], ByteString] = Converter(_ =>(b: Array[Byte]) => ByteString.copyFrom(b))
  implicit val ByteStringToByteArrayConverter: Converter[ByteString, Array[Byte]] = Converter(_ =>(b: ByteString) => b.toByteArray)

  test("GPB to case class") {
    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    val map = Map("first" -> CaseClassMapInnerMessage("str", 1), "second" -> CaseClassMapInnerMessage("str", 2))
    val map2 = Map("first" -> 1, "second" -> 2)

    val dataRepeated = Seq(gpbInternal, gpbInternal, gpbInternal)

    val gpb = TestMessageV2.Data.newBuilder()
      .setFieldString("ahoj")
      .setFieldIntName(9)
      //.setFieldOption(13) -> will become None
      .setFieldBlob(ByteString.EMPTY)
      .setFieldGpb(gpbInternal)
      .setFieldGpbOption(gpbInternal)
      .addAllFieldGpbRepeated(dataRepeated.asJava)
      .addFieldGpb2RepeatedRecurse(Data3.newBuilder().addAllFieldGpb(dataRepeated.asJava).build())
      .addAllFieldStrings(Seq("a", "b").asJava)
      .addAllFieldStringsName(Seq("a").asJava)
      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
      .addAllFieldIntegers2(Seq(1, 2).map(int2Integer).asJava)
      .addAllFieldMap(map.map { case (key, value) =>
        TestMessageV2.MapMessage.newBuilder().setKey(key).setValue(MapInnerMessage.newBuilder().setFieldInt(value.fieldInt).setFieldString(value.fieldString)).build()
      }.asJava)
      .addAllFieldMap2(map.map { case (key, value) =>
        TestMessageV2.MapMessage.newBuilder().setKey(key).setValue(MapInnerMessage.newBuilder().setFieldInt(value.fieldInt).setFieldString(value.fieldString)).build()
      }.asJava)
      .build()

    val caseClassB = CaseClassB(0.9, "text")

    val caseClassD = Seq(CaseClassD(Seq(caseClassB, caseClassB, caseClassB)))

    val expected = CaseClassA("ahoj", 9, None, ByteString.EMPTY, List("a"), caseClassB, Some(caseClassB), None, Seq(caseClassB, caseClassB, caseClassB), caseClassD, List("a", "b"), Vector(3, 6), List(), "1, 2", map, map2)

    assertResult(Good(expected))(gpb.asCaseClass[CaseClassA])
  }

  test("GPB to case class multiple failures") {
    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
//      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    // fields commented out are REQUIRED
    val gpb = TestMessageV2.Data.newBuilder()
      //      .setFieldString("ahoj")
      //      .setFieldIntName(9)
      .setFieldOption(13)
      .setFieldBlob(ByteString.EMPTY)
      .setFieldGpb(gpbInternal)
      .setFieldGpbOption(gpbInternal)
      .addFieldGpb2RepeatedRecurse(Data3.newBuilder().addFieldGpb(gpbInternal).build())
      //      .addAllFieldStrings(Seq("a", "b").asJava)
      .addAllFieldStringsName(Seq("a").asJava)
      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
      .build()

    val expected = List("gpb.fieldString", "gpb.fieldIntName", "gpb.fieldGpb.fieldBlob", "gpb.fieldGpb2RepeatedRecurse.fieldGpb.fieldBlob").map(MissingFieldFailure).sortBy(_.toString)

    gpb.asCaseClass[CaseClassA] match {
      case Bad(e) =>
        assertResult(expected)(e.toList.sortBy(_.toString))

      case Good(_) => fail("Should fail")
    }
  }

  test("Case class to GPB") {
    val map = Map("first" -> CaseClassMapInnerMessage("str", 1), "second" -> CaseClassMapInnerMessage("str", 2))
    val map2 = Map("first" -> 1, "second" -> 2)

    val caseClassB = CaseClassB(0.9, "text")

    val caseClassD = Seq(CaseClassD(Seq(caseClassB, caseClassB, caseClassB)))

    val caseClass = CaseClassA("ahoj", 9, Some(13), ByteString.EMPTY, List("a"), caseClassB, Some(caseClassB), None, Seq(caseClassB, caseClassB, caseClassB), caseClassD, List("a", "b"), Vector(3, 6), List(), "1, 2", map, map2)

    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    val dataRepeated = Seq(gpbInternal, gpbInternal, gpbInternal)

    val expectedGpb = TestMessageV2.Data.newBuilder()
      .setFieldString("ahoj")
      .setFieldIntName(9)
      .setFieldOption(13)
      .setFieldBlob(ByteString.EMPTY)
      .setFieldGpb(gpbInternal)
      .setFieldGpbOption(gpbInternal)
      .addAllFieldGpbRepeated(dataRepeated.asJava)
      .addFieldGpb2RepeatedRecurse(Data3.newBuilder().addAllFieldGpb(dataRepeated.asJava).build())
      .addAllFieldStrings(Seq("a", "b").asJava)
      .addAllFieldStringsName(Seq("a").asJava)
      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
      .addAllFieldMap(map.map { case (key, value) =>
        TestMessageV2.MapMessage.newBuilder().setKey(key).setValue(MapInnerMessage.newBuilder().setFieldInt(value.fieldInt).setFieldString(value.fieldString)).build()
      }.asJava)
      .addAllFieldMap2(map.map { case (key, value) =>
        TestMessageV2.MapMessage.newBuilder().setKey(key).setValue(MapInnerMessage.newBuilder().setFieldInt(value.fieldInt).setFieldString(value.fieldString)).build()
      }.asJava)
      .addAllFieldIntegers2(Seq(1, 2).map(int2Integer).asJava)
      .build()

    caseClass.asGpb[Data] match {
      case Good(e) if e == expectedGpb => // ok
    }
  }

  test("convert case class to GPB and back") {
    val map = Map("first" -> CaseClassMapInnerMessage("str", 1), "second" -> CaseClassMapInnerMessage("str", 2))

    val original = CaseClassC(StringWrapperClass("ahoj"), 9, Some(13), ByteString.EMPTY, Vector("a"), CaseClassB(0.9, "text"), Some(CaseClassB(0.9, "text")), None, Array("a", "b"), Vector(3, 6), List(), map)

    val Good(converted) = original.asGpb[Data]

    assertResult(Good(original))(converted.asCaseClass[CaseClassC])
  }

  test("convert case class with ignored field to GPB and back") {
    val original = CaseClassE(fieldString = "ahoj", fieldOption = Some("ahoj2"), fieldBytes = Bytes.copyFromUtf8("hello"))

    val Good(converted) = original.asGpb[Data4]

    assertResult(Good(original))(converted.asCaseClass[CaseClassE])
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
                      fieldMap: Map[String, CaseClassMapInnerMessage],
                      @GpbName("fieldMap2")
                      @GpbMap(key = "key", value = "value")
                      fieldMapDiffType: Map[String, Int])

case class CaseClassMapInnerMessage(fieldString: String, fieldInt: Int)

case class CaseClassB(fieldDouble: Double, @GpbName("fieldBlob") fieldString: String)

case class CaseClassD(fieldGpb: Seq[CaseClassB])

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
                      fieldMap: Map[String, CaseClassMapInnerMessage]) {

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
                      @GpbIgnored fieldIgnored2: String = "hello",
                      fieldBytes: Bytes)

case class StringWrapperClass(value: String)

object CaseClassA

// this is here to prevent reappearing of bug with companion object
