package com.avast.cactus

import org.scalatest.FunSuite

class ConverterMethodsTest extends FunSuite {
  test("map") {
    case class A(value: Int)
    case class B(value: String)
    case class C(values: String)

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convSeqAtoC: Converter[A, C] = convAtoB.map(b => C(b.value + "42"))

    assertResult(Right(C("1842")))(convSeqAtoC.apply("fieldName")(A(18)))
  }

  test("andThen") {
    case class A(value: Int)
    case class B(value: String)
    case class C(values: String)

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convBtoC = Converter[B, C] { b =>
      C(b.value + "42")
    }
    val convAtoC: Converter[A, C] = convAtoB.andThen(convBtoC)

    assertResult(Right(C("1842")))(convAtoC.apply("fieldName")(A(18)))
  }

  test("contraMap") {
    case class A(value: Int)
    case class B(value: String)
    case class AA(value: Double)

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convAAtoB: Converter[AA, B] = convAtoB.contraMap(aa => A(aa.value.toInt))

    assertResult(Right(B("42")))(convAAtoB.apply("fieldName")(AA(42.3)))
  }

  test("compose") {
    case class A(value: Int)
    case class B(value: String)
    case class AA(value: Double)

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convAAtoA = Converter[AA, A] { aa =>
      A(aa.value.toInt)
    }
    val convAAtoB: Converter[AA, B] = convAtoB.compose(convAAtoA)

    assertResult(Right(B("42")))(convAAtoB.apply("fieldName")(AA(42.3)))
  }
}
