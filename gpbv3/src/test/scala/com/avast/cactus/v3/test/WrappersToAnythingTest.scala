package com.avast.cactus.v3.test
import com.avast.cactus._
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
}
