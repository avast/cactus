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

  test("flatMap") {
    case class A(value: Int)
    case class B(value: String)
    case class C(values: String)

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convSeqAtoC: Converter[A, C] = convAtoB.flatMap(b => Right(C(b.value + "42")))

    assertResult(Right(C("1842")))(convSeqAtoC.apply("fieldName")(A(18)))
  }

  test("flatMap with Converter") {
    case class A(value: Int)
    case class B(value: String)
    case class C(values: String)

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convBtoC = Converter[B, C] { b =>
      C(b.value + "42")
    }
    val convSeqAtoC: Converter[A, C] = convAtoB.flatMap(convBtoC)

    assertResult(Right(C("1842")))(convSeqAtoC.apply("fieldName")(A(18)))
  }

  test("mapSeq") {
    case class A(value: Int)
    case class B(value: String)
    case class C(values: Seq[String])

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convSeqAtoC: Converter[Seq[A], C] = convAtoB.mapSeq(sb => C(sb.map(_.value)))

    assertResult(Right(C(Seq("1", "2", "3"))))(convSeqAtoC.apply("fieldName")(Seq(A(1), A(2), A(3))))
  }

  test("flatMapSeq") {
    case class A(value: Int)
    case class B(value: String)
    case class C(values: Seq[String])

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convSeqAtoC: Converter[Seq[A], C] = convAtoB.flatMapSeq(sb => Right(C(sb.map(_.value))))

    assertResult(Right(C(Seq("1", "2", "3"))))(convSeqAtoC.apply("fieldName")(Seq(A(1), A(2), A(3))))
  }

  test("flatMapSeq with Converter") {
    case class A(value: Int)
    case class B(value: String)
    case class C(values: Seq[String])

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convBtoC = Converter[Seq[B], C] { sb =>
      C(sb.map(_.value))
    }
    val convSeqAtoC: Converter[Seq[A], C] = convAtoB.flatMapSeq(convBtoC)

    assertResult(Right(C(Seq("1", "2", "3"))))(convSeqAtoC.apply("fieldName")(Seq(A(1), A(2), A(3))))
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

  test("contraFlatMap") {
    case class A(value: Int)
    case class B(value: String)
    case class AA(value: Double)

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convAAtoB: Converter[AA, B] = convAtoB.contraFlatMap(aa => Right(A(aa.value.toInt)))

    assertResult(Right(B("42")))(convAAtoB.apply("fieldName")(AA(42.3)))
  }

  test("contraFlatMap with Converter") {
    case class A(value: Int)
    case class B(value: String)
    case class AA(value: Double)

    val convAtoB = Converter[A, B] { a =>
      B(a.value.toString)
    }
    val convAAtoA = Converter[AA, A] { aa =>
      A(aa.value.toInt)
    }
    val convAAtoB: Converter[AA, B] = convAtoB.contraFlatMap(convAAtoA)

    assertResult(Right(B("42")))(convAAtoB.apply("fieldName")(AA(42.3)))
  }
}
