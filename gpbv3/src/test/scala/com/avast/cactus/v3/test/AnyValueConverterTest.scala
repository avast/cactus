package com.avast.cactus.v3.test

import java.time.Instant

import cats.data.NonEmptyList
import com.avast.cactus.v3.TestMessageV3.{ExtensionsMessage, MessageInsideAnyField}
import com.avast.cactus.v3._
import com.avast.cactus.{Converter, ResultOrErrors, UnknownFailure, _}
import com.google.protobuf.{Any, ByteString, InvalidProtocolBufferException, Timestamp => GpbTimestamp}
import org.scalatest.FunSuite

class AnyValueConverterTest extends FunSuite {
  test("convert to GPB and back") {
    val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

    val orig = ExtClass(Instant.ofEpochSecond(12345), AnyValue.of(innerMessage))

    val expected =
      ExtensionsMessage.newBuilder().setTimestamp(GpbTimestamp.newBuilder().setSeconds(12345)).setAny(Any.pack(innerMessage)).build()

    val Right(converted) = orig.asGpb[ExtensionsMessage]

    assertResult(expected)(converted)

    val actual = converted.asCaseClass[ExtClass]

    assertResult(Right(orig))(actual)
    assertResult(Right(innerMessage))(actual.flatMap(_.any.asGpb[MessageInsideAnyField]))
  }

  test("convert to GPB and back if any is optional") {
    val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

    val orig = ExtClassOpt(Instant.ofEpochSecond(12345), Some(AnyValue.of(innerMessage)))

    val expected = ExtensionsMessage
      .newBuilder()
      .setTimestamp(GpbTimestamp.newBuilder().setSeconds(12345))
      .setAny(Any.pack(innerMessage))
      .build()

    val Right(converted) = orig.asGpb[ExtensionsMessage]

    assertResult(expected)(converted)

    val Right(actual) = expected.asCaseClass[ExtClassOpt]

    assertResult(orig)(actual)
    assertResult(Some(Right(innerMessage)))(actual.any.map(_.asGpb[MessageInsideAnyField]))
  }

  test("convert to GPB and back if any is optional and not set") {
    val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

    val orig = ExtClassOpt(Instant.ofEpochSecond(12345), None)

    val expected = ExtensionsMessage.newBuilder().setTimestamp(GpbTimestamp.newBuilder().setSeconds(12345)).build()

    val Right(converted) = orig.asGpb[ExtensionsMessage]

    assertResult(expected)(converted)

    val Right(actual) = expected.asCaseClass[ExtClassOpt]

    assertResult(orig)(actual)
    assertResult(None)(actual.any.map(_.asGpb[MessageInsideAnyField]))
  }

  test("convert to GPB and back incl. inner") {
    val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

    // to case class:

    val msg = ExtensionsMessage
      .newBuilder()
      .setTimestamp(GpbTimestamp.newBuilder().setSeconds(12345))
      .setAny(Any.pack(innerMessage))
      .build()

    val extClass: ResultOrErrors[ExtClass] = msg.asCaseClass[ExtClass]

    val Right(innerClass: InnerClass) = extClass
      .flatMap(_.any.asGpb[MessageInsideAnyField]) // Any -> GPB
      .flatMap(_.asCaseClass[InnerClass]) // GPB -> case class

    assertResult(InnerClass(42, "ahoj"))(innerClass)

    // to GPB:

    val Right(extensionMessage: ExtensionsMessage) = InnerClass(42, "ahoj")
      .asGpb[MessageInsideAnyField]
      .map(miaf => ExtClass(Instant.ofEpochSecond(12345), AnyValue.of(miaf)))
      .flatMap(_.asGpb[ExtensionsMessage])

    assertResult(msg)(extensionMessage)
  }

  test("failure when parsing trash") {
    val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

    val anyValue = AnyValue.of(innerMessage).copy(bytes = ByteString.copyFromUtf8("+ěščřžýáííé")) // damaged data

    val Left(NonEmptyList(UnknownFailure(fieldPath, cause), List())) = anyValue.asGpb[MessageInsideAnyField]

    assertResult("anyValue")(fieldPath)
    assert(cause.isInstanceOf[InvalidProtocolBufferException])

    //

    val gpb = ExtensionsMessage
      .newBuilder()
      .setTimestamp(GpbTimestamp.newBuilder().setSeconds(12345))
      .setAny(Any.pack(innerMessage).toBuilder.setValue(ByteString.copyFromUtf8("+ěščřžýáííé")).build())
      .build()

    val cc = gpb.asCaseClass[ExtClass]

    assertResult(Right(ExtClass(Instant.ofEpochSecond(12345), `anyValue`)))(cc)

    val Left(NonEmptyList(UnknownFailure(fieldPath2, cause2), List())) = cc.flatMap(_.any.asGpb[MessageInsideAnyField])

    assertResult("any")(fieldPath2)
    assert(cause2.isInstanceOf[InvalidProtocolBufferException])
  }

  test("forwarding Converter[AnyValue, A] to AnyValueConverter[A]") {
    val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

    // to case class:

    val msg = ExtensionsMessage
      .newBuilder()
      .setTimestamp(GpbTimestamp.newBuilder().setSeconds(12345))
      .setAny(Any.pack(innerMessage))
      .build()

    val Right(extClass) = msg.asCaseClass[ExtClass]

    val conv = implicitly[Converter[AnyValue, MessageInsideAnyField]]
    val Right(miaf) = conv.apply("")(extClass.any)

    assertResult(innerMessage)(miaf)
  }

  test("convert AnyValue directly to case class") {
    val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

    // to case class:

    val msg = ExtensionsMessage
      .newBuilder()
      .setTimestamp(GpbTimestamp.newBuilder().setSeconds(12345))
      .setAny(Any.pack(innerMessage))
      .build()

    val extClass = msg.asCaseClass[ExtClass]

    implicit val conv: AnyValueConverter[InnerClass] = AnyValueConverter.deriveToCaseClass[MessageInsideAnyField, InnerClass]

    val Right(innerClass: InnerClass) = extClass
      .flatMap(_.any.asCaseClass[InnerClass])

    assertResult(InnerClass(42, "ahoj"))(innerClass)
  }

  test("needs manually derived converter to convert AnyValue directly to case class") {
    checkDoesNotCompile {
      """
        |val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()
        |
        |val msg = ExtensionsMessage
        |  .newBuilder()
        |  .setTimestamp(GpbTimestamp.newBuilder().setSeconds(12345))
        |  .setAny(Any.pack(innerMessage))
        |  .build()
        |
        |val extClass = msg.asCaseClass[ExtClass]
        |
        |val Right(innerClass: InnerClass) = extClass.flatMap(_.any.asCaseClass[InnerClass])
      """.stripMargin
    }
  }
}
