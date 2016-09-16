package com.avast.cactus

import com.avast.cactus.TestMessage.{Data, Data2}
import com.google.protobuf.TextFormat
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
      .setFieldGpb(gpbInternal)
      .setFieldGpbOption(gpbInternal)
      .addAllFieldStrings(Seq("a", "b").asJava)
      .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
      .build()


    val expected = CaseClassA("ahoj", 9, Some(13), CaseClassB(0.9), Some(CaseClassB(0.9)), None, List("a", "b"), Some(List(3, 6)))
    assertResult(Right(expected))(gpb.asCaseClass[CaseClassA])
  }

  test("Case class to GPB") {
    val caseClass = CaseClassA("ahoj", 9, Some(13), CaseClassB(0.9), Some(CaseClassB(0.9)), None, List("a", "b"), Some(List(3, 6)))

    val Right(result) = caseClass.asGpb[Data]

    println(s"Data {\n${TextFormat.printToString(result)}\n}")
  }
}

case class CaseClassA(field: String,
                      @GpbName("fieldIntName")
                      fieldInt: Int,
                      fieldOption: Option[Int],
                      fieldGpb: CaseClassB,
                      fieldGpbOption: Option[CaseClassB],
                      fieldGpbOptionEmpty: Option[CaseClassB],
                      fieldStringsList: immutable.Seq[String],
                      fieldOptionIntegersList: Option[List[Int]]) {
}

case class CaseClassB(fieldDouble: Double)
