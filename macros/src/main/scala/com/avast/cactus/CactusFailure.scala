package com.avast.cactus

sealed trait CactusFailure {
  def message: String
}

case class MissingFieldFailure(fieldName: String) extends CactusFailure {
  override def message: String = s"Missing required field '$fieldName'"
}

case class OneOfValueNotSetFailure(oneOfName: String) extends CactusFailure {
  override def message: String = s"ONE-OF '$oneOfName' does not have set value"
}
