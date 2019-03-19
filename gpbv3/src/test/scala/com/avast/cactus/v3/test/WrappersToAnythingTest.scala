package com.avast.cactus.v3.test
import com.avast.cactus.{checkDoesNotCompile, checkCompiles, Converter}
import com.avast.cactus.v3.TestMessageV3.Data4
import org.scalatest.FunSuite

class WrappersToAnythingTest extends FunSuite {

  test("wrappers to anything") {
    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[String, Exception] = Converter(_ => ???)
        |implicitly[Converter[StringValue, Exception]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[StringValue, Exception]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Double, Exception] = Converter(_ => ???)
        |implicitly[Converter[DoubleValue, Exception]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[DoubleValue, Exception]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Float, Exception] = Converter(_ => ???)
        |implicitly[Converter[FloatValue, Exception]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[FloatValue, Exception]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Boolean, Exception] = Converter(_ => ???)
        |implicitly[Converter[BoolValue, Exception]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[BoolValue, Exception]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Long, Exception] = Converter(_ => ???)
        |implicitly[Converter[Int64Value, Exception]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[Int64Value, Exception]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Int, Exception] = Converter(_ => ???)
        |implicitly[Converter[Int32Value, Exception]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[Int32Value, Exception]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[ByteString, Exception] = Converter(_ => ???)
        |implicitly[Converter[BytesValue, Exception]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[BytesValue, Exception]]
      """.stripMargin
    }
  }

  test("anything to wrappers") {
    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Exception, String] = Converter(_ => ???)
        |implicitly[Converter[Exception, StringValue]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[Exception, StringValue]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Exception, Double] = Converter(_ => ???)
        |implicitly[Converter[Exception, DoubleValue]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[Exception, DoubleValue]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Exception, Float] = Converter(_ => ???)
        |implicitly[Converter[Exception, FloatValue]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[Exception, FloatValue]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Exception, Boolean] = Converter(_ => ???)
        |implicitly[Converter[Exception, BoolValue]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[Exception, BoolValue]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Exception, Long] = Converter(_ => ???)
        |implicitly[Converter[Exception, Int64Value]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[Exception, Int64Value]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Exception, Int] = Converter(_ => ???)
        |implicitly[Converter[Exception, Int32Value]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[Exception, Int32Value]]
      """.stripMargin
    }

    checkCompiles {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicit val c: Converter[Exception, ByteString] = Converter(_ => ???)
        |implicitly[Converter[Exception, BytesValue]]
      """.stripMargin
    }

    checkDoesNotCompile {
      """
        |import com.avast.cactus._
        |import com.avast.cactus.v3._
        |import com.google.protobuf._
        |
        |implicitly[Converter[Exception, BytesValue]]
      """.stripMargin
    }
  }

  test("convert case class to GPB and back - StringValue to case class field conversion") {
    import com.avast.cactus.v3._

    checkDoesNotCompile {
      """
        |val original = CaseClass(fieldString = "ahoj", fieldOption = Some(Sha256("ahoj2")))
        |val Right(converted) = original.asGpb[Data4]
      """.stripMargin
    }

    {
      implicit val c: Converter[String, Sha256] = Converter(Sha256)
      implicit val c2: Converter[Sha256, String] = Converter(_.hash)

      val original = CaseClass(fieldString = "ahoj", fieldOption = Some(Sha256("ahoj2")))

      val Right(converted) = original.asGpb[Data4]

      assertResult(Right(original))(converted.asCaseClass[CaseClass])
    }
  }

  test("convert case class to GPB and back - StringValue to array of bytes") {
    import com.avast.cactus.v3._

    checkDoesNotCompile {
      """
        |val original = CaseClass2(fieldString = "ahoj", fieldOption = Some("ahoj2".getBytes))
        |val Right(converted) = original.asGpb[Data4]
      """.stripMargin
    }

    {
      implicit val c: Converter[Seq[Byte], String] = Converter(bytes => new String(bytes.toArray))
      implicit val c2: Converter[String, Seq[Byte]] = Converter(_.getBytes)

      val original = CaseClass2(fieldString = "ahoj", fieldOption = Some("ahoj2".getBytes))

      val Right(converted) = original.asGpb[Data4]

      assertResult(Right(original))(converted.asCaseClass[CaseClass2])
    }
  }
}

case class Sha256(hash: String)

case class CaseClass(fieldString: String, fieldOption: Option[Sha256])
case class CaseClass2(fieldString: String, fieldOption: Option[Seq[Byte]])
