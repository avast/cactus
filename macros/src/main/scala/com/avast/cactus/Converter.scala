package com.avast.cactus

import com.avast.cactus.v3.V3Converters
import org.scalactic.Good
import org.scalactic.Accumulation._

import scala.annotation.implicitNotFound
import scala.collection.JavaConverters._
import scala.reflect.ClassTag

@implicitNotFound("Could not find an instance of Converter from ${A} to ${B}, try to import or define one")
trait Converter[A, B] {
  def apply(fieldPath: String)(a: A): ResultOrError[B]
}

object Converter extends V3Converters with OptionalConverters {

  def checked[A, B](f: (String, A) => ResultOrError[B]): Converter[A, B] = new Converter[A, B] {
    override def apply(fieldPath: String)(a: A): ResultOrError[B] = f(fieldPath, a)
  }

  def apply[A, B](f: A => B): Converter[A, B] = new Converter[A, B] {
    override def apply(fieldPath: String)(a: A): ResultOrError[B] = Good(f(a))
  }

  // primitive types conversions:

  // this converter is necessary otherwise we get strange compilation errors
  implicit val string2StringConverter: Converter[String, java.lang.String] = Converter(identity)

  implicit val byte2ByteConverter: Converter[Byte, java.lang.Byte] = Converter(byte2Byte)
  implicit val short2ShortConverter: Converter[Short, java.lang.Short] = Converter(short2Short)
  implicit val char2CharacterConverter: Converter[Char, java.lang.Character] = Converter(char2Character)
  implicit val int2IntegerConverter: Converter[Int, java.lang.Integer] = Converter(int2Integer)
  implicit val long2LongConverter: Converter[Long, java.lang.Long] = Converter(long2Long)
  implicit val float2FloatConverter: Converter[Float, java.lang.Float] = Converter(float2Float)
  implicit val double2DoubleConverter: Converter[Double, java.lang.Double] = Converter(double2Double)
  implicit val boolean2BooleanConverter: Converter[Boolean, java.lang.Boolean] = Converter(boolean2Boolean)

  implicit val Byte2byteConverter: Converter[java.lang.Byte, Byte] = Converter(Byte2byte)
  implicit val Short2shortConverter: Converter[java.lang.Short, Short] = Converter(Short2short)
  implicit val Character2charConverter: Converter[java.lang.Character, Char] = Converter(Character2char)
  implicit val Integer2intConverter: Converter[java.lang.Integer, Int] = Converter(Integer2int)
  implicit val Long2longConverter: Converter[java.lang.Long, Long] = Converter(Long2long)
  implicit val Float2floatConverter: Converter[java.lang.Float, Float] = Converter(Float2float)
  implicit val Double2doubleConverter: Converter[java.lang.Double, Double] = Converter(Double2double)
  implicit val Boolean2booleanConverter: Converter[java.lang.Boolean, Boolean] = Converter(Boolean2boolean)

  // v3 conversions inherited from V3Converters

  // conversions generators:

  implicit def vectorToList[A, B](implicit aToBConverter: Converter[A, B]): Converter[Vector[A], List[B]] = new Converter[Vector[A], List[B]] {
    override def apply(fieldPath: String)(v: Vector[A]): ResultOrError[List[B]] = {
      v.map(aToBConverter.apply(fieldPath)).toList.combined
    }
  }

  implicit def vectorToList[A]: Converter[Vector[A], List[A]] = Converter(_.toList)

  implicit def vectorToArray[A: ClassTag]: Converter[Vector[A], Array[A]] = Converter(_.toArray)
}
