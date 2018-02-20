package com.avast

import cats.data.NonEmptyList
import cats.syntax.either._
import org.scalactic.{Every, Or}

import scala.language.experimental.macros

package object cactus {
  type EveryCactusFailure = Every[CactusFailure]

  type CactusFailures = NonEmptyList[CactusFailure]
  type ResultOrErrors[A] = Either[CactusFailures, A]

  implicit class EitherToOr[L, R](val e: Either[NonEmptyList[L], R]) {
    def toOr: R Or Every[L] = {
      Or.from {
        e.leftMap { nel =>
          Every.from(nel.toList).getOrElse(sys.error("NEL could not be empty :-/"))
        }
      }
    }
  }

  implicit class OrToEither[R](val o: R Or EveryCactusFailure) {
    def toEitherNEL: ResultOrErrors[R] = {
      o.badMap(f => NonEmptyList.fromList(f.toList).getOrElse(sys.error("Every could not be empty :-/"))).toEither
    }
  }

  private[cactus] def checkDoesNotCompile(code: String): Unit = macro TestMacros.checkDoesNotCompile

  private[cactus] def checkCompiles(code: String): Unit = macro TestMacros.checkCompiles
}
