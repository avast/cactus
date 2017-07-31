package com.avast.cactus

sealed trait CactusFailure {
  def message: String
}

case class MissingFieldFailure(fieldPath: String) extends CactusFailure {
  override def message: String = s"Missing required field '$fieldPath'"
}

case class OneOfValueNotSetFailure(oneOfName: String) extends CactusFailure {
  override def message: String = s"ONE-OF '$oneOfName' does not have set value"
}

case class WrongAnyTypeFailure(fieldPath: String, declared: String, required: String) extends CactusFailure {
  override def message: String = s"Declared type_url in message is '$declared', required '$required' (field '$fieldPath')"
}

case class UnknownFailure(fieldPath: String, error: Throwable)
    extends Exception(s"Unknown failure while converting '$fieldPath'", error)
    with CactusFailure {
  override def message: String = super.getMessage
}

case class CustomFailure(fieldPath: String, message: String, error: Throwable = null) extends Exception(message, error) with CactusFailure
