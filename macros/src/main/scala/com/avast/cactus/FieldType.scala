package com.avast.cactus


sealed trait FieldType[MethodSymbol, ClassSymbol]

object FieldType {

  case class Normal[MethodSymbol, ClassSymbol](getter: MethodSymbol, setter: MethodSymbol) extends FieldType[MethodSymbol, ClassSymbol]

  case class OneOf[MethodSymbol, ClassSymbol](name: String, impls: Set[ClassSymbol]) extends FieldType[MethodSymbol, ClassSymbol]

}
