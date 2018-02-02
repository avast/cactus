package com.avast.cactus.grpc

import org.scalatest.FunSuite
import io.grpc.Context

class ContextKeysTest extends FunSuite {
  test("reproducible keys") {
    val key = ContextKeys.get[String]("theHeader")
    val key2 = ContextKeys.get[String]("theHeader")

    val theValue = "theValue"
    val ctx = Context.current().withValue(key, theValue)

    assertResult(theValue)(key2.get(ctx))
    assertResult(key.get(ctx))(key2.get(ctx))
  }

  test("keys with different type") {
    val key = ContextKeys.get[String]("theHeader")
    val key2 = ContextKeys.get[Context]("theHeader")

    val ctx = Context.current().withValue(key, "theValue")

    //noinspection ComparingUnrelatedTypes
    assert(key.get(ctx) != key2.get(ctx))
  }
}
