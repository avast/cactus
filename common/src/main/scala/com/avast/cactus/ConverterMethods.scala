package com.avast.cactus

import cats.implicits._

trait ConverterMethods[A, B] {
  self: Converter[A, B] =>

  final def map[C](f: B => C): Converter[A, C] = new Converter[A, C] {
    override def apply(fieldPath: String)(a: A): ResultOrErrors[C] = self.apply(fieldPath)(a).map(f)
  }

  final def flatMap[C](c: Converter[B, C]): Converter[A, C] = new Converter[A, C] {
    override def apply(fieldPath: String)(a: A): ResultOrErrors[C] = self.apply(fieldPath)(a).flatMap(c.apply(fieldPath))
  }

  final def flatMap[C](c: B => ResultOrErrors[C]): Converter[A, C] = new Converter[A, C] {
    override def apply(fieldPath: String)(a: A): ResultOrErrors[C] = self.apply(fieldPath)(a).flatMap(c)
  }

  final def mapSeq[C](f: Seq[B] => C): Converter[Seq[A], C] = new Converter[Seq[A], C] {
    override def apply(fieldPath: String)(a: Seq[A]): ResultOrErrors[C] = {
      a.map(self.apply(fieldPath)).toList.sequence[ResultOrErrors, B].map(f)
    }
  }

  final def flatMapSeq[C](f: Converter[Seq[B], C]): Converter[Seq[A], C] = new Converter[Seq[A], C] {
    override def apply(fieldPath: String)(a: Seq[A]): ResultOrErrors[C] = {
      a.map(self.apply(fieldPath)).toList.sequence[ResultOrErrors, B].flatMap(f(fieldPath))
    }
  }

  final def flatMapSeq[C](f: Seq[B] => ResultOrErrors[C]): Converter[Seq[A], C] = new Converter[Seq[A], C] {
    override def apply(fieldPath: String)(a: Seq[A]): ResultOrErrors[C] = {
      a.map(self.apply(fieldPath)).toList.sequence[ResultOrErrors, B].flatMap(f)
    }
  }

  final def contraMap[AA](f: AA => A): Converter[AA, B] = Converter(f).flatMap(self)

  final def contraFlatMap[AA](f: AA => ResultOrErrors[A]): Converter[AA, B] = Converter.checked[AA, A]((_, aa) => f(aa)).flatMap(self)

  final def contraFlatMap[AA](f: Converter[AA, A]): Converter[AA, B] = f.flatMap(self)

}
