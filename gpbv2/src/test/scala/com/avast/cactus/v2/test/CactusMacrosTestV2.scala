package com.avast.cactus.v2.test

import cats.data.NonEmptyList
import com.avast.cactus.v2.TestMessageV2.MapMessage.MapInnerMessage
import com.avast.cactus.v2.TestMessageV2.MessageWithRawEnum.EnumMapEntry
import com.avast.cactus.v2.TestMessageV2._
import com.avast.cactus.v2._
import com.avast.cactus.{Converter, _}
import com.google.protobuf.ByteString
import org.scalatest.FunSuite

import scala.collection.JavaConverters._
import scala.collection.immutable

class CactusMacrosTestV2 extends FunSuite {

  private val thrownIllegalArgumentException = new IllegalArgumentException(s"Numbers > 0 required in ''")

  // user specified converters
  implicit val StringToByteStringConverter: Converter[String, ByteString] = Converter((b: String) => ByteString.copyFromUtf8(b))
  implicit val ByteStringToStringConverter: Converter[ByteString, String] = Converter((b: ByteString) => b.toStringUtf8)

  implicit val StringWrapperToStringConverter: Converter[StringWrapperClass, String] = Converter((b: StringWrapperClass) => b.value)
  implicit val StringToStringWrapperConverter: Converter[String, StringWrapperClass] = Converter((b: String) => StringWrapperClass(b))

  // totally bad implementation of converter; don't use!!!
  implicit val JavaIntegerListStringConverter: Converter[java.util.List[Integer], String] = Converter.checked { (fieldPath, ints) =>
    val i2 = ints.asScala

    if (i2.forall(_ > 0)) {
      Right(i2.mkString(", "))
    } else {
      // THIS IS TOTALLY BAD PRACTICE AND YOU WILL COME TO HELL IF YOU USE THIS IN REAL CODE
      throw thrownIllegalArgumentException
    }
  }
  implicit val StringJavaIntegerListConverter: Converter[String, java.lang.Iterable[_ <: Integer]] = Converter(
    _.split(", ").map(_.toInt).map(int2Integer).toSeq.asJava)

  implicit val MapInnerMessageIntConverter: Converter[MapInnerMessage, Int] = Converter(_.getFieldInt)
  implicit val IntMapInnerMessageConverter: Converter[Int, MapInnerMessage] = Converter(
    MapInnerMessage.newBuilder().setFieldString("str").setFieldInt(_).build())

  // these are not needed, but they are here to be sure it won't cause trouble to the user
  implicit val ByteArrayToByteStringConverter: Converter[Array[Byte], ByteString] = Converter((b: Array[Byte]) => ByteString.copyFrom(b))
  implicit val ByteStringToByteArrayConverter: Converter[ByteString, Array[Byte]] = Converter((b: ByteString) => b.toByteArray)

  test("GPB to case class") {
    val gpbInternal = Data2
      .newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    val map = Map("first" -> CaseClassMapInnerMessage("str", 1), "second" -> CaseClassMapInnerMessage("str", 2))
    val map2 = Map("first" -> 1, "second" -> 2)

    val dataRepeated = Seq(gpbInternal, gpbInternal, gpbInternal)

    val gpb = TestMessageV2.Data
      .newBuilder()
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
      .addAllFieldMap(map.map {
        case (key, value) =>
          TestMessageV2.MapMessage
            .newBuilder()
            .setKey(key)
            .setValue(MapInnerMessage.newBuilder().setFieldInt(value.fieldInt).setFieldString(value.fieldString))
            .build()
      }.asJava)
      .addAllFieldMap2(map.map {
        case (key, value) =>
          TestMessageV2.MapMessage
            .newBuilder()
            .setKey(key)
            .setValue(MapInnerMessage.newBuilder().setFieldInt(value.fieldInt).setFieldString(value.fieldString))
            .build()
      }.asJava)
      .build()

    val caseClassB = CaseClassB(0.9, "text")

    val caseClassD = Seq(CaseClassD(Seq(caseClassB, caseClassB, caseClassB)))

    val expected = CaseClassA(
      fieldString = "ahoj",
      fieldInt = 9,
      fieldOption = None,
      fieldBlob = ByteString.EMPTY,
      fieldStrings2 = List("a"),
      fieldGpb = caseClassB,
      fieldGpbOption = Some(caseClassB),
      fieldGpbOptionEmpty = None,
      fieldGpbRepeated = Seq(caseClassB, caseClassB, caseClassB),
      fieldGpb2RepeatedRecurse = caseClassD,
      fieldStrings = List("a", "b"),
      fieldOptionIntegers = Vector(3, 6),
      fieldOptionIntegersEmpty = List(),
      fieldIntegersString = "1, 2",
      fieldMap = map,
      fieldMapDiffType = map2
    )

    assertResult(Right(expected))(gpb.asCaseClass[CaseClassA])
  }

  test("GPB to case class multiple failures") {
    val gpbInternal = Data2
      .newBuilder()
      .setFieldDouble(0.9)
      //      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    // fields commented out are REQUIRED
    val gpb = TestMessageV2.Data
      .newBuilder()
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
      .addAllFieldIntegers2(Seq(-3, -6).map(int2Integer).asJava) // converter is intentionally screwed to throw an exception while there are negative numbers present
      .build()

    val expected = List(
      "gpb.fieldString",
      "gpb.fieldIntName",
      "gpb.fieldGpb.fieldBlob",
      "gpb.fieldGpb2RepeatedRecurse.fieldGpb.fieldBlob"
    ).map(MissingFieldFailure).sortBy(_.toString).:+(UnknownFailure("gpb.fieldIntegers2", thrownIllegalArgumentException))

    gpb.asCaseClass[CaseClassA] match {
      case Left(e) =>
        assertResult(expected)(e.toList.sortBy(_.toString))

      case Right(_) => fail("Should fail")
    }
  }

  test("Case class to GPB") {
    val map = Map("first" -> CaseClassMapInnerMessage("str", 1), "second" -> CaseClassMapInnerMessage("str", 2))
    val map2 = Map("first" -> 1, "second" -> 2)

    val caseClassB = CaseClassB(0.9, "text")

    val caseClassD = Seq(CaseClassD(Seq(caseClassB, caseClassB, caseClassB)))

    val caseClass = CaseClassA(
      fieldString = "ahoj",
      fieldInt = 9,
      fieldOption = Some(13),
      fieldBlob = ByteString.EMPTY,
      fieldStrings2 = List("a"),
      fieldGpb = caseClassB,
      fieldGpbOption = Some(caseClassB),
      fieldGpbOptionEmpty = None,
      fieldGpbRepeated = Seq(caseClassB, caseClassB, caseClassB),
      fieldGpb2RepeatedRecurse = caseClassD,
      fieldStrings = List("a", "b"),
      fieldOptionIntegers = Vector(3, 6),
      fieldOptionIntegersEmpty = List(),
      fieldIntegersString = "1, 2",
      fieldMap = map,
      fieldMapDiffType = map2
    )

    val gpbInternal = Data2
      .newBuilder()
      .setFieldDouble(0.9)
      .setFieldBlob(ByteString.copyFromUtf8("text"))
      .build()

    val dataRepeated = Seq(gpbInternal, gpbInternal, gpbInternal)

    val expectedGpb = TestMessageV2.Data
      .newBuilder()
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
      .addAllFieldMap(map.map {
        case (key, value) =>
          TestMessageV2.MapMessage
            .newBuilder()
            .setKey(key)
            .setValue(MapInnerMessage.newBuilder().setFieldInt(value.fieldInt).setFieldString(value.fieldString))
            .build()
      }.asJava)
      .addAllFieldMap2(map.map {
        case (key, value) =>
          TestMessageV2.MapMessage
            .newBuilder()
            .setKey(key)
            .setValue(MapInnerMessage.newBuilder().setFieldInt(value.fieldInt).setFieldString(value.fieldString))
            .build()
      }.asJava)
      .addAllFieldIntegers2(Seq(1, 2).map(int2Integer).asJava)
      .build()

    caseClass.asGpb[Data] match {
      case Right(e) if e == expectedGpb => // ok
    }
  }

  test("case class to GPB with error") {
    val map = Map("first" -> CaseClassMapInnerMessage("str", 1), "second" -> CaseClassMapInnerMessage("str", 2))
    val map2 = Map("first" -> 1, "second" -> 2)

    val caseClassB = CaseClassB(0.9, "text")

    val caseClassD = Seq(CaseClassD(Seq(caseClassB, caseClassB, caseClassB)))

    val wrongIntegersString = "1 2" // expected format is ', ' concatenated numbers

    val caseClassA = CaseClassA(
      fieldString = "ahoj",
      fieldInt = 9,
      fieldOption = Some(13),
      fieldBlob = ByteString.EMPTY,
      fieldStrings2 = List("a"),
      fieldGpb = caseClassB,
      fieldGpbOption = Some(caseClassB),
      fieldGpbOptionEmpty = None,
      fieldGpbRepeated = Seq(caseClassB, caseClassB, caseClassB),
      fieldGpb2RepeatedRecurse = caseClassD,
      fieldStrings = List("a", "b"),
      fieldOptionIntegers = Vector(3, 6),
      fieldOptionIntegersEmpty = List(),
      fieldIntegersString = wrongIntegersString,
      fieldMap = map,
      fieldMapDiffType = map2
    )

    val Left(NonEmptyList(UnknownFailure(fieldPath, cause), List())) = caseClassA.asGpb[Data]

    assertResult("caseClassA.fieldIntegersString")(fieldPath)
    assert(cause.isInstanceOf[NumberFormatException])
  }

  test("convert case class to GPB and back") {
    val map = Map("first" -> CaseClassMapInnerMessage("str", 1), "second" -> CaseClassMapInnerMessage("str", 2))

    val original = CaseClassC(
      fieldString = StringWrapperClass("ahoj"),
      fieldInt = 9,
      fieldOption = Some(13),
      fieldBlob = ByteString.EMPTY,
      fieldStrings2 = Vector("a"),
      fieldGpb = CaseClassB(0.9, "text"),
      fieldGpbOption = Some(CaseClassB(0.9, "text")),
      fieldGpbOptionEmpty = None,
      fieldStrings = Array("a", "b"),
      fieldOptionIntegers = Vector(3, 6),
      fieldOptionIntegersEmpty = List(),
      fieldMap = map
    )

    val Right(converted) = original.asGpb[Data]

    assertResult(Right(original))(converted.asCaseClass[CaseClassC])
  }

  test("convert case class with ignored field to GPB and back") {
    val original = CaseClassE(fieldString = "ahoj", fieldOption = Some("ahoj2"))

    val Right(converted) = original.asGpb[Data4]

    assertResult(Right(original))(converted.asCaseClass[CaseClassE])
  }

  test("recursion detection") {
    /*
     * In case the recursion detection doesn't work, this code won't require a Converter -  macros generate
     * a Converter referring recursively to itself which will cause a StackOverflowError in runtime.
     * By doing this check we try to avoid it and detect the "recursion detection" is broken.
     * */
    checkDoesNotCompile(
      """
        import com.avast.cactus.v2._
        com.avast.cactus.v2.TestMessageV2.MessageWithEnum.getDefaultInstance.asCaseClass[com.avast.cactus.v2.test.MessageWithEnum]
        """
    )

    checkCompiles(
      """
        |import com.avast.cactus.v2._
        |import com.avast.cactus.v2.TestMessageV2
        |import TestMessageV2.MessageWithEnum.{TheEnum => GpbEnum}
        |
        |implicit val c: com.avast.cactus.Converter[GpbEnum, com.avast.cactus.v2.test.TheEnum] = com.avast.cactus.Converter {
        |  case GpbEnum.ONE => TheEnum.One
        |  case GpbEnum.TWO => TheEnum.Two
        |}
        |
        |TestMessageV2.MessageWithEnum.getDefaultInstance.asCaseClass[com.avast.cactus.v2.test.CaseClassWithEnum]
        |""".stripMargin
    )
  }

  test("seq[case class] to seq[gpb] and then gpb wrapper") {
    assertCompiles("""
                     |val value: Converter[Seq[CaseClassB], Data3] = {
                     |  implicitly[Converter[Seq[CaseClassB], Seq[Data2]]]
                     |    .map(sd2 => Data3.newBuilder().addAllFieldGpb(sd2.asJava).build())
                     |}
      """.stripMargin)
  }

  test("message with enum") {
    val gpb = MessageWithRawEnum
      .newBuilder()
      .setFieldString("ahoj")
      .setFieldEnum(TestEnum.FIRST)
      .setFieldEnumOption(TestEnum.SECOND)
      .addAllFieldMap(Map("first" -> TestEnum.FIRST, "second" -> TestEnum.SECOND).map {
        case (key, value) =>
          EnumMapEntry
            .newBuilder()
            .setKey(key)
            .setValue(value)
            .build()
      }.asJava)
      .build()

    val ccl = CaseClassWithRawEnum(
      fieldString = "ahoj",
      fieldEnum = TestEnum.FIRST,
      fieldEnumOption = Some(TestEnum.SECOND),
      fieldMap = Map("first" -> TestEnum.FIRST, "second" -> TestEnum.SECOND)
    )

    assertResult(Right(ccl))(gpb.asCaseClass[CaseClassWithRawEnum])
    assertResult(Right(gpb))(ccl.asGpb[MessageWithRawEnum])

    val gpb2 = MessageWithEnum.newBuilder().setTheEnum(MessageWithEnum.TheEnum.TWO).build()

    assertResult(Right(CaseClassWithEnum(Some(TheEnum.Two))))(gpb2.asCaseClass[CaseClassWithEnum])
    assertResult(Right(gpb2))(CaseClassWithEnum(Some(TheEnum.Two)).asGpb[MessageWithEnum])
  }

  test("message with unset optional enum") {
    val gpb = MessageWithRawEnum
      .newBuilder()
      .setFieldString("ahoj")
      .setFieldEnum(TestEnum.FIRST)
      // missing: .setFieldEnumOption(TestEnum.SECOND)
      .addAllFieldMap(Map("first" -> TestEnum.FIRST, "second" -> TestEnum.SECOND).map {
        case (key, value) =>
          EnumMapEntry
            .newBuilder()
            .setKey(key)
            .setValue(value)
            .build()
      }.asJava)
      .build()

    val ccl = CaseClassWithRawEnum(
      fieldString = "ahoj",
      fieldEnum = TestEnum.FIRST,
      fieldEnumOption = None,
      fieldMap = Map("first" -> TestEnum.FIRST, "second" -> TestEnum.SECOND)
    )

    assertResult(Right(ccl))(gpb.asCaseClass[CaseClassWithRawEnum])
    assertResult(Right(gpb))(ccl.asGpb[MessageWithRawEnum])

    val gpb2 = MessageWithEnum.newBuilder().build()

    assertResult(Right(CaseClassWithEnum(None)))(gpb2.asCaseClass[CaseClassWithEnum])
    assertResult(Right(gpb2))(CaseClassWithEnum(None).asGpb[MessageWithEnum])
  }

  test("message with unset required enum") {
    val gpb = MessageWithRawEnum
      .newBuilder()
      .setFieldString("ahoj")
      // missing: .setFieldEnum(TestEnum.FIRST)
      .setFieldEnumOption(TestEnum.SECOND)
      .addAllFieldMap(Map("first" -> TestEnum.FIRST, "second" -> TestEnum.SECOND).map {
        case (key, value) =>
          EnumMapEntry
            .newBuilder()
            .setKey(key)
            .setValue(value)
            .build()
      }.asJava)
      .build()

    assertResult {
      Left(NonEmptyList(MissingFieldFailure("gpb.fieldEnum"), List()))
    }(gpb.asCaseClass[CaseClassWithRawEnum])
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
                      @GpbIgnored fieldIgnored2: String = "hello")

case class StringWrapperClass(value: String)

// this is here to prevent reappearing of bug with companion object
object CaseClassA

sealed trait TheEnum

object TheEnum {

  case object One extends TheEnum

  case object Two extends TheEnum

}

case class CaseClassWithEnum(theEnum: Option[TheEnum])

case class CaseClassWithRawEnum(fieldString: String,
                                fieldEnum: TestEnum,
                                fieldEnumOption: Option[TestEnum],
                                @GpbMap(key = "key", value = "value") fieldMap: Map[String, TestEnum])
