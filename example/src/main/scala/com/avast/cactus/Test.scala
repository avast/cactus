package com.avast.cactus

import java.util

case class CaseClassA(field: String,
                      @GpbName("fieldIntName")
                      fieldInt: Int,
                      fieldOption: Option[Int],
                      fieldGpb: CaseClassB,
                      fieldGpbOption: Option[CaseClassB],
                      fieldGpbOptionEmpty: Option[CaseClassB],
                      fieldListStrings: Seq[String],
                      fieldOptionListIntegers: Option[Seq[Int]]) {

  def confusingMethod: String = ""

  val confusingField: String = ""
}

case class CaseClassB(fieldDouble: Double)

class GpbA extends MessageLite {
  def getField: java.lang.String = "ahoj"

  def hasField: java.lang.Boolean = true

  def getFieldIntName: java.lang.Integer = 9

  def hasFieldIntName: java.lang.Boolean = true

  def getFieldOption: java.lang.Integer = 13

  def hasFieldOption: java.lang.Boolean = true

  val getFieldGpb: GpbB = new GpbB

  def hasFieldGpb: java.lang.Boolean = true

  val getFieldGpbOption: GpbB = new GpbB

  def hasFieldGpbOption: java.lang.Boolean = true

  def getFieldGpbOptionEmpty: GpbB = ???

  def hasFieldGpbOptionEmpty: java.lang.Boolean = false

  def getFieldListStrings: java.util.List[java.lang.String] = util.Arrays.asList("a", "b")

  def hasFieldListStrings: java.lang.Boolean = true

  def getFieldOptionListIntegers: java.util.List[java.lang.Integer] = util.Arrays.asList(3, 6)

  def hasFieldOptionListIntegers: java.lang.Boolean = true
}

class GpbB extends MessageLite {
  def getFieldDouble: java.lang.Double = 0.9

  def hasFieldDouble: java.lang.Boolean = true
}

object Test extends App {
  val gpb = new GpbA

  gpb.as[CaseClassA] match {
    case Right(inst) =>
      println(inst)
      assert(inst.toString == "CaseClassA(ahoj,9,Some(13),CaseClassB(0.9),Some(CaseClassB(0.9)),None,Buffer(a, b),Some(ArrayBuffer(3, 6)))")

    case Left(MissingFieldFailure(fieldName)) =>
      println(s"Missing required field: '$fieldName'")
  }

}
