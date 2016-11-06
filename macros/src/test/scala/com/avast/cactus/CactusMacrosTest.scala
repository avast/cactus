package com.avast.cactus

import com.avast.cactus.TestMessage._
import com.google.protobuf.ByteString
import org.scalactic.{Bad, Good}
import org.scalatest.FunSuite

import scala.collection.JavaConverters._
import scala.collection.immutable

class CactusMacrosTest extends FunSuite {

  // user specified converters
  implicit val StringToByteStringConverter: Converter[String, ByteString] = Converter((b: String) => ByteString.copyFromUtf8(b))
  implicit val ByteStringToStringConverter: Converter[ByteString, String] = Converter((b: ByteString) => b.toStringUtf8)

  implicit val StringWrapperToStringConverter: Converter[StringWrapperClass, String] = Converter((b: StringWrapperClass) => b.value)
  implicit val StringToStringWrapperConverter: Converter[String, StringWrapperClass] = Converter((b: String) => StringWrapperClass(b))

  implicit val JavaIntegerListStringConverter: Converter[java.util.List[Integer], String] = Converter(_.asScala.mkString(", "))
  implicit val StringJavaIntegerListConverter: Converter[String, java.util.List[Integer]] = Converter(_.split(", ").map(_.toInt).map(int2Integer).toSeq.asJava)

  // these are not needed, but they are here to be sure it won't cause trouble to the user
  implicit val ByteArrayToByteStringConverter: Converter[Array[Byte], ByteString] = Converter((b: Array[Byte]) => ByteString.copyFrom(b))
  implicit val ByteStringToByteArrayConverter: Converter[ByteString, Array[Byte]] = Converter((b: ByteString) => b.toByteArray)

  test("GPB to case class") {
    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    val map = Map("first" -> "1", "second" -> "2")

    val gpb = TestMessage.Data.newBuilder()
      .setField("ahoj")
      .setFieldIntName(9)
      .setFieldOption(13)
      .setFieldBlob(ByteString.EMPTY)
      .setFieldGpb(gpbInternal)
      .setFieldGpbOption(gpbInternal)
      .addAllFieldStrings(Seq("a", "b").asJava)
      .addAllFieldStringsName(Seq("a").asJava)
      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
      .addAllFieldIntegers2(Seq(1, 2).map(int2Integer).asJava)
      .addAllFieldMap(map.map { case (key, value) => TestMessage.MapMessage.newBuilder().setKey(key).setValue(value).build() }.asJava)
      .build()

    val expected = CaseClassA("ahoj", 9, Some(13), ByteString.EMPTY, List("a"), CaseClassB(0.9, "text"), Some(CaseClassB(0.9, "text")), None, List("a", "b"), Vector(3, 6), List(), "1, 2", map)
    assertResult(Good(expected))(gpb.asCaseClass[CaseClassA])
  }

  test("GPB to case class multiple failures") {
    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    // fields commented out are REQUIRED
    val gpb = TestMessage.Data.newBuilder()
      //      .setField("ahoj")
      //      .setFieldIntName(9)
      .setFieldOption(13)
      .setFieldBlob(ByteString.EMPTY)
      .setFieldGpb(gpbInternal)
      .setFieldGpbOption(gpbInternal)
      //      .addAllFieldStrings(Seq("a", "b").asJava)
      .addAllFieldStringsName(Seq("a").asJava)
      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
      .build()

    val expected = List("field", "fieldIntName").map(MissingFieldFailure).sortBy(_.toString)

    gpb.asCaseClass[CaseClassA] match {
      case Bad(e) =>
        assertResult(expected)(e.toList.sortBy(_.toString))

      case Good(_) => fail("Should fail")
    }
  }

  test("Case class to GPB") {
    val map = Map("first" -> "1", "second" -> "2")

    val caseClass = CaseClassA("ahoj", 9, Some(13), ByteString.EMPTY, List("a"), CaseClassB(0.9, "text"), Some(CaseClassB(0.9, "text")), None, List("a", "b"), Vector(3, 6), List(), "1, 2", map)

    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    val expectedGpb = TestMessage.Data.newBuilder()
      .setField("ahoj")
      .setFieldIntName(9)
      .setFieldOption(13)
      .setFieldBlob(ByteString.EMPTY)
      .setFieldGpb(gpbInternal)
      .setFieldGpbOption(gpbInternal)
      .addAllFieldStrings(Seq("a", "b").asJava)
      .addAllFieldStringsName(Seq("a").asJava)
      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
      .addAllFieldMap(map.map { case (key, value) => TestMessage.MapMessage.newBuilder().setKey(key).setValue(value).build() }.asJava)
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
}

case class CaseClassA(field: String,
                      @GpbName("fieldIntName")
                      fieldInt: Int,
                      fieldOption: Option[Int],
                      fieldBlob: ByteString,
                      @GpbName("fieldStringsName")
                      fieldStrings2: List[String],
                      fieldGpb: CaseClassB,
                      fieldGpbOption: Option[CaseClassB],
                      fieldGpbOptionEmpty: Option[CaseClassB],
                      fieldStrings: immutable.Seq[String],
                      fieldOptionIntegers: Vector[Int],
                      fieldOptionIntegersEmpty: List[Int],
                      @GpbName("fieldIntegers2")
                      fieldIntegersString: String,
                      @GpbMap(key = "key", value = "value")
                      fieldMap: Map[String, String])

case class CaseClassB(fieldDouble: Double, @GpbName("fieldBlob") fieldString: String)

case class CaseClassC(field: StringWrapperClass,
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
      field == that.field &&
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

case class StringWrapperClass(value: String)

object CaseClassA

// this is here to prevent reappearing of bug with companion object
