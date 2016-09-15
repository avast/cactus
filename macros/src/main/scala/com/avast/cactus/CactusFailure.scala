package com.avast.cactus

sealed trait CactusFailure {
  def message: String
}

case class MissingFieldFailure(fieldName: String) extends CactusFailure {
  override def message: String = s"Missing required field '$fieldName'"
}
