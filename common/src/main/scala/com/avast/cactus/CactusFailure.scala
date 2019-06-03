package com.avast.cactus

sealed trait CactusFailure {
  def message: String
}

case class MissingFieldFailure(fieldPath: String) extends CactusFailure {
  override val message: String = s"Missing required field '$fieldPath'"
}

case class OneOfValueNotSetFailure(oneOfName: String) extends CactusFailure {
  override val message: String = s"ONE-OF '$oneOfName' does not have set value"
}

case class EnumValueUnrecognizedFailure(enumName: String) extends CactusFailure {
  override val message: String = s"Enum '$enumName' value was UNRECOGNIZED"
}

case class WrongAnyTypeFailure(fieldPath: String, declared: String, required: String) extends CactusFailure {
  override val message: String = s"Declared type_url in message is '$declared', required '$required' (field '$fieldPath')"
}

case class UnknownFailure(fieldPath: String, error: Throwable)
    extends Exception(s"Unknown failure while converting '$fieldPath'", error)
    with CactusFailure {
  override val message: String = super.getMessage
}

case class InvalidValueFailure(fieldPath: String, value: String, error: Throwable = null)
    extends Exception(s"Invalid value of field '$fieldPath': $value", error)
    with CactusFailure {
  override val message: String = getMessage
}

case class CustomFailure(fieldPath: String, message: String, error: Throwable = null) extends Exception(message, error) with CactusFailure

case class CompositeFailure(errors: Seq[CactusFailure])
    extends Exception(s"Multiple failures:\n${errors.mkString("[", ", ", "]")}")
    with CactusFailure {
  override def message: String = s"Multiple failures has happened\n${errors.mkString("[", ", ", "]")}"
}
