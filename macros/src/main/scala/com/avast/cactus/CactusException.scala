package com.avast.cactus

case class CactusException(failure: CactusFailure) extends RuntimeException(failure.message)
