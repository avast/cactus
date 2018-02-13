package com.avast

import cats.data.NonEmptyList
import cats.syntax.either._
import org.scalactic.{Every, Or}

package object cactus {
  private[cactus] type EveryCactusFailure = Every[CactusFailure]

  type CactusFailures = NonEmptyList[CactusFailure]
  type ResultOrErrors[A] = Either[CactusFailures, A]

  private[cactus] implicit class EitherToOr[L, R](val e: Either[NonEmptyList[L], R]) {
    def toOr: R Or Every[L] = {
      Or.from {
        e.leftMap { nel =>
          Every.from(nel.toList).getOrElse(sys.error("NEL could not be empty :-/"))
        }
      }
    }
  }

  private[cactus] implicit class OrToEither[R](val o: R Or EveryCactusFailure) {
    def toEitherNEL: ResultOrErrors[R] = {
      o.badMap(f => NonEmptyList.fromList(f.toList).getOrElse(sys.error("Every could not be empty :-/"))).toEither
    }
  }

}
