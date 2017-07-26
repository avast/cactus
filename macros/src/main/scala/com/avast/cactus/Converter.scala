package com.avast.cactus

import com.avast.cactus.v3.V3Converters

import scala.annotation.implicitNotFound
import scala.collection.JavaConverters._
import scala.reflect.ClassTag

@implicitNotFound("Could not find an instance of Converter from ${A} to ${B}, try to import or define one")
trait Converter[A, B] {
  def apply(fieldPath: String)(a: A): B
}

object Converter extends V3Converters with OptionalConverters {

  def apply[A, B](f: String => A => B): Converter[A, B] = new Converter[A, B] {
    override def apply(fieldPath: String)(a: A): B = f(fieldPath)(a)
  }

  // primitive types conversions:

  // this converter is necessary otherwise we get strange compilation errors
  implicit val string2StringConverter: Converter[String, java.lang.String] = Converter( _ => identity)

  implicit val byte2ByteConverter: Converter[Byte, java.lang.Byte] = Converter(_ => byte2Byte)
  implicit val short2ShortConverter: Converter[Short, java.lang.Short] = Converter(_ => short2Short)
  implicit val char2CharacterConverter: Converter[Char, java.lang.Character] = Converter(_ => char2Character)
  implicit val int2IntegerConverter: Converter[Int, java.lang.Integer] = Converter(_ => int2Integer)
  implicit val long2LongConverter: Converter[Long, java.lang.Long] = Converter(_ => long2Long)
  implicit val float2FloatConverter: Converter[Float, java.lang.Float] = Converter(_ => float2Float)
  implicit val double2DoubleConverter: Converter[Double, java.lang.Double] = Converter(_ => double2Double)
  implicit val boolean2BooleanConverter: Converter[Boolean, java.lang.Boolean] = Converter(_ => boolean2Boolean)

  implicit val Byte2byteConverter: Converter[java.lang.Byte, Byte] = Converter(_ => Byte2byte)
  implicit val Short2shortConverter: Converter[java.lang.Short, Short] = Converter(_ => Short2short)
  implicit val Character2charConverter: Converter[java.lang.Character, Char] = Converter(_ => Character2char)
  implicit val Integer2intConverter: Converter[java.lang.Integer, Int] = Converter(_ => Integer2int)
  implicit val Long2longConverter: Converter[java.lang.Long, Long] = Converter(_ => Long2long)
  implicit val Float2floatConverter: Converter[java.lang.Float, Float] = Converter(_ => Float2float)
  implicit val Double2doubleConverter: Converter[java.lang.Double, Double] = Converter(_ => Double2double)
  implicit val Boolean2booleanConverter: Converter[java.lang.Boolean, Boolean] = Converter(_ => Boolean2boolean)

  // v3 conversions inherited from V3Converters

  // conversions generators:

  implicit def vectorToList[A, B](implicit aToBConverter: Converter[A, B]): Converter[Vector[A], List[B]] = Converter(fp => _.map(aToBConverter.apply(fp)).toList)

  implicit def vectorToList[A]: Converter[Vector[A], List[A]] = Converter(_ => _.toList)

  implicit def vectorToArray[A: ClassTag]: Converter[Vector[A], Array[A]] = Converter(_ => _.toArray)
}
