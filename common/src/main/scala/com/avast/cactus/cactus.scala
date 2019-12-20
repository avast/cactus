package com.avast

import cats.data.NonEmptyList

import scala.language.experimental.macros

package object cactus {
  type CactusFailures = NonEmptyList[CactusFailure]
  type ResultOrErrors[A] = Either[CactusFailures, A]

  private[cactus] def checkDoesNotCompile(code: String): Unit = macro TestMacros.checkDoesNotCompile

  private[cactus] def checkCompiles(code: String): Unit = macro TestMacros.checkCompiles
}
