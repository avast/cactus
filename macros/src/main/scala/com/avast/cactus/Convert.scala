package com.avast.cactus

import scala.annotation.implicitNotFound

@implicitNotFound("Could not find an instance of Convert typeclass from ${A} to ${B}, try to import or define one")
trait Convert[A, B] {
  def apply(a: A): B
}

object Convert {

  def apply[A, B](f: A => B): Convert[A, B] = new Convert[A, B] {
    override def apply(a: A): B = f(a)
  }

  // standard types converters:

  implicit val byte2ByteConvert: Convert[Byte, java.lang.Byte] = Convert(byte2Byte)
  implicit val short2ShortConvert: Convert[Short, java.lang.Short] = Convert(short2Short)
  implicit val char2CharacterConvert: Convert[Char, java.lang.Character] = Convert(char2Character)
  implicit val int2IntegerConvert: Convert[Int, java.lang.Integer] = Convert(int2Integer)
  implicit val long2LongConvert: Convert[Long, java.lang.Long] = Convert(long2Long)
  implicit val float2FloatConvert: Convert[Float, java.lang.Float] = Convert(float2Float)
  implicit val double2DoubleConvert: Convert[Double, java.lang.Double] = Convert(double2Double)
  implicit val boolean2BooleanConvert: Convert[Boolean, java.lang.Boolean] = Convert(boolean2Boolean)

  implicit val Byte2byteConvert: Convert[java.lang.Byte, Byte] = Convert(Byte2byte)
  implicit val Short2shortConvert: Convert[java.lang.Short, Short] = Convert(Short2short)
  implicit val Character2charConvert: Convert[java.lang.Character, Char] = Convert(Character2char)
  implicit val Integer2intConvert: Convert[java.lang.Integer, Int] = Convert(Integer2int)
  implicit val Long2longConvert: Convert[java.lang.Long, Long] = Convert(Long2long)
  implicit val Float2floatConvert: Convert[java.lang.Float, Float] = Convert(Float2float)
  implicit val Double2doubleConvert: Convert[java.lang.Double, Double] = Convert(Double2double)
  implicit val Boolean2booleanConvert: Convert[java.lang.Boolean, Boolean] = Convert(Boolean2boolean)

}
