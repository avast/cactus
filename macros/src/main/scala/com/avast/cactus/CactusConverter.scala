package com.avast.cactus

import scala.annotation.implicitNotFound

@implicitNotFound("Could not find CactusConverter from ${A} to ${B}, try to import or define one")
trait CactusConverter[A, B] extends (A => B)

object CactusConverter {
  def apply[A, B](f: A => B): CactusConverter[A, B] = new CactusConverter[A, B] {
    override def apply(a: A): B = f(a)
  }

  //implicit def noOpConverter[A]: CactusConverter[A, A] = CactusConverter(identity)

  implicit val string2StringConverter: CactusConverter[String, java.lang.String] = CactusConverter(identity)

  implicit val byte2ByteConverter: CactusConverter[Byte, java.lang.Byte] = CactusConverter(byte2Byte)
  implicit val short2ShortConverter: CactusConverter[Short, java.lang.Short] = CactusConverter(short2Short)
  implicit val char2CharacterConverter: CactusConverter[Char, java.lang.Character] = CactusConverter(char2Character)
  implicit val int2IntegerConverter: CactusConverter[Int, java.lang.Integer] = CactusConverter(int2Integer)
  implicit val long2LongConverter: CactusConverter[Long, java.lang.Long] = CactusConverter(long2Long)
  implicit val float2FloatConverter: CactusConverter[Float, java.lang.Float] = CactusConverter(float2Float)
  implicit val double2DoubleConverter: CactusConverter[Double, java.lang.Double] = CactusConverter(double2Double)
  implicit val boolean2BooleanConverter: CactusConverter[Boolean, java.lang.Boolean] = CactusConverter(boolean2Boolean)

  implicit val Byte2byteConverter: CactusConverter[java.lang.Byte, Byte] = CactusConverter(Byte2byte)
  implicit val Short2shortConverter: CactusConverter[java.lang.Short, Short] = CactusConverter(Short2short)
  implicit val Character2charConverter: CactusConverter[java.lang.Character, Char] = CactusConverter(Character2char)
  implicit val Integer2intConverter: CactusConverter[java.lang.Integer, Int] = CactusConverter(Integer2int)
  implicit val Long2longConverter: CactusConverter[java.lang.Long, Long] = CactusConverter(Long2long)
  implicit val Float2floatConverter: CactusConverter[java.lang.Float, Float] = CactusConverter(Float2float)
  implicit val Double2doubleConverter: CactusConverter[java.lang.Double, Double] = CactusConverter(Double2double)
  implicit val Boolean2booleanConverter: CactusConverter[java.lang.Boolean, Boolean] = CactusConverter(Boolean2boolean)

}
