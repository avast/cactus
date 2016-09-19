package com.avast.cactus

import com.avast.cactus.TestMessage.{Data, Data2}
import com.google.protobuf.ByteString
import org.scalatest.FunSuite

import scala.collection.JavaConverters._
import scala.collection.immutable

class CactusMacrosTest extends FunSuite {
  test("GPB to case class") {
    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
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

    val expected = CaseClassA("ahoj", 9, Some(13), ByteString.EMPTY, List("a"), CaseClassB(0.9), Some(CaseClassB(0.9)), None, List("a", "b"), Some(List(3, 6)), None)
    assertResult(Right(expected))(gpb.asCaseClass[CaseClassA])
  }

  test("Case class to GPB") {
    val caseClass = CaseClassA("ahoj", 9, Some(13), ByteString.EMPTY, List("a"), CaseClassB(0.9), Some(CaseClassB(0.9)), None, List("a", "b"), Some(List(3, 6)), None)

    val gpbInternal = Data2.newBuilder()
      .setFieldDouble(0.9)
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
      .build()


    assertResult(Right(expectedGpb))(caseClass.asGpb[Data])
  }

  test("case class to GPB and back") {
    val original = CaseClassA("ahoj", 9, Some(13), ByteString.EMPTY, List("a"), CaseClassB(0.9), Some(CaseClassB(0.9)), None, List("a", "b"), Some(List(3, 6)), None)

    val Right(converted) = original.asGpb[Data]

    assertResult(Right(original))(converted.asCaseClass[CaseClassA])
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
                      fieldStringsList: immutable.Seq[String],
                      fieldOptionIntegersList: Option[List[Int]],
                      fieldOptionIntegersEmptyList: Option[List[Int]])

case class CaseClassB(fieldDouble: Double)
