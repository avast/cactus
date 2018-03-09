package com.avast.cactus

trait ConverterMethods[A, B] {
  self: Converter[A, B] =>

  final def map[C](f: B => C): Converter[A, C] = new Converter[A, C] {
    override def apply(fieldPath: String)(a: A): ResultOrErrors[C] = self.apply(fieldPath)(a).map(f)
  }

  final def andThen[C](c: Converter[B, C]): Converter[A, C] = new Converter[A, C] {
    override def apply(fieldPath: String)(a: A): ResultOrErrors[C] = self.apply(fieldPath)(a).flatMap(c.apply(fieldPath))
  }

  final def contraMap[AA](f: AA => A): Converter[AA, B] = Converter(f).andThen(self)

  final def compose[AA](f: Converter[AA, A]): Converter[AA, B] = f.andThen(self)

}
