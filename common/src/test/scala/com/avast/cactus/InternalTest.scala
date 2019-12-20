package com.avast.cactus

import cats.data.NonEmptyList
import com.avast.cactus.internal._
import org.scalatest.FunSuite
class InternalTest extends FunSuite {
  test("ResultsListOps.combined") {
    val failures = List(
      Right("a"),
      Right("b"),
      Left(NonEmptyList.of(MissingFieldFailure("abc"))),
      Left(NonEmptyList.of[CactusFailure](MissingFieldFailure("bcd"), OneOfValueNotSetFailure("efg")))
    )

    assertResult {
      Left(NonEmptyList.of(MissingFieldFailure("abc"), MissingFieldFailure("bcd"), OneOfValueNotSetFailure("efg")))
    }(failures.combined)
  }
}
