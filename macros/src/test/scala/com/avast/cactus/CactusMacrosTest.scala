package com.avast.cactus

import com.avast.cactus.TestMessage.{Data, Data2}
import com.google.protobuf.ByteString
import org.scalatest.FunSuite

import scala.collection.JavaConverters._
import scala.collection.immutable

class CactusMacrosTest extends FunSuite {

  // user specified converters
  implicit val StringToByteStringConverter: Converter[String, ByteString] = Converter((b: String) => ByteString.copyFromUtf8(b))
  implicit val ByteStringToStringConverter: Converter[ByteString, String] = Converter((b: ByteString) => b.toStringUtf8)

  // these are not needed, but they are here to be sure it won't cause trouble to the user
  implicit val ByteArrayToByteStringConverter: Converter[Array[Byte], ByteString] = Converter((b: Array[Byte]) => ByteString.copyFrom(b))
  implicit val ByteStringToByteArrayConverter: Converter[ByteString, Array[Byte]] = Converter((b: ByteString) => b.toByteArray)

  test("GPB to case class") {
    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

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
      .build()

    val expected = CaseClassA("ahoj", 9, Some(13), ByteString.EMPTY, List("a"), CaseClassB(0.9, "text"), Some(CaseClassB(0.9, "text")), None, List("a", "b"), Some(Vector(3, 6)), None)
    assertResult(Right(expected))(gpb.asCaseClass[CaseClassA])
  }

//  test("Case class to GPB") {
//    val caseClass = CaseClassC("ahoj", 9, Some(13), ByteString.EMPTY, Vector("a"), CaseClassB(0.9, "text"), Some(CaseClassB(0.9, "text")), None, List("a", "b"), Some(List(3, 6)), None)
//
//    val gpbInternal = Data2.newBuilder()
//      .setFieldDouble(0.9)
//      .setFieldBlob(ByteString.copyFromUtf8("text"))
//      .build()
//
//    val expectedGpb = TestMessage.Data.newBuilder()
//      .setField("ahoj")
//      .setFieldIntName(9)
//      .setFieldOption(13)
//      .setFieldBlob(ByteString.EMPTY)
//      .setFieldGpb(gpbInternal)
//      .setFieldGpbOption(gpbInternal)
//      .addAllFieldStrings(Seq("a", "b").asJava)
//      .addAllFieldStringsName(Seq("a").asJava)
//      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
//      .build()
//
//
//    assertResult(Right(expectedGpb))(caseClass.asGpb[Data])
//  }
//
//  test("case class to GPB and back") {
//    val original = CaseClassC("ahoj", 9, Some(13), ByteString.EMPTY, Vector("a"), CaseClassB(0.9, "text"), Some(CaseClassB(0.9, "text")), None, List("a", "b"), Some(Vector(3, 6)), None)
//
//    val Right(converted) = original.asGpb[Data]
//
//    assertResult(Right(original))(converted.asCaseClass[CaseClassC])
//  }
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
                      fieldStringsList: immutable.Seq[String],
                      fieldOptionIntegersList: Option[Vector[Int]],
                      fieldOptionIntegersEmptyList: Option[List[Int]])

case class CaseClassB(fieldDouble: Double, @GpbName("fieldBlob") fieldString: String)

// this is clone of CaseClassA, but it contains different collections
case class CaseClassC(field: String,
                      @GpbName("fieldIntName")
                      fieldInt: Int,
                      fieldOption: Option[Int],
                      fieldBlob: ByteString,
                      @GpbName("fieldStringsName")
                      fieldStrings2: Vector[String],
                      fieldGpb: CaseClassB,
                      fieldGpbOption: Option[CaseClassB],
                      fieldGpbOptionEmpty: Option[CaseClassB],
                      fieldStringsList: Seq[String],
                      fieldOptionIntegersList: Option[Seq[Int]],
                      fieldOptionIntegersEmptyList: Option[Seq[Int]])
