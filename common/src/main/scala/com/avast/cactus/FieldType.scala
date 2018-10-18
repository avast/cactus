package com.avast.cactus


sealed trait FieldType[MethodSymbol, ClassSymbol, Type]

object FieldType {

  case class Normal[MethodSymbol, ClassSymbol, Type](getter: MethodSymbol, setter: MethodSymbol) extends FieldType[MethodSymbol, ClassSymbol, Type]

  case class Enum[MethodSymbol, ClassSymbol, Type](getter: MethodSymbol, classType: Type , impls: Set[ClassSymbol]) extends FieldType[MethodSymbol, ClassSymbol, Type]

  case class OneOf[MethodSymbol, ClassSymbol, Type](name: String, classType: Type , impls: Set[ClassSymbol]) extends FieldType[MethodSymbol, ClassSymbol, Type]

}
