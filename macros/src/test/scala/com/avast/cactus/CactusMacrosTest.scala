package com.avast.cactus

import com.avast.cactus.TestMessage.{Data, Data2}
import com.google.protobuf.ByteString
import org.scalactic.{Bad, Good}
import org.scalatest.FunSuite

import scala.collection.JavaConverters._
import scala.collection.immutable

class CactusMacrosTest extends FunSuite {

  // user specified converters
  implicit val StringToByteStringConverter: Converter[String, ByteString] = Converter((b: String) => ByteString.copyFromUtf8(b))
  implicit val ByteStringToStringConverter: Converter[ByteString, String] = Converter((b: ByteString) => b.toStringUtf8)

  implicit val doubleToStringConverter: Converter[Double, String] = Converter(_.toString)
  //TODO improve message
  implicit val stringToDoubleConverter: Converter[String, Double] = Converter(_.toDouble)

  implicit def vectorToString[A]: Converter[Vector[A], String] = Converter(_.mkString(","))

  implicit val stringToVectorInt: Converter[String, Vector[Integer]] = Converter(_.split(",").map(_.toInt).map(int2Integer).toVector)

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

    val expected = CaseClassA("ahoj", 9, Some(13), ByteString.EMPTY, List("a"), CaseClassB(0.9, "text"), Some(CaseClassB(0.9, "text")), None, List("a", "b"), Vector(3, 6), List())
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
    val caseClass = CaseClassC("ahoj", 9, Some(13), ByteString.EMPTY, Vector("a"), CaseClassB(0.9, "text"), Some(CaseClassB(0.9, "text")), None, List(3.5, 6.7), "3,6", List())

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
      .build()


    caseClass.asGpb[Data] match {
      case Good(e) if e == expectedGpb => // ok
    }
  }

  test("case class to GPB and back") {
    val original = CaseClassC("ahoj", 9, Some(13), ByteString.EMPTY, Vector("a"), CaseClassB(0.9, "text"), Some(CaseClassB(0.9, "text")), None, List(3.5, 6.7), "3,6", Vector())

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
                      fieldOptionIntegersEmpty: List[Int])

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
                      fieldStrings: List[Double],
                      fieldOptionIntegers: String,
                      fieldOptionIntegersEmpty: Seq[Int])
