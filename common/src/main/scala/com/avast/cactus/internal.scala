package com.avast.cactus

import cats.Applicative
import cats.data._
import cats.syntax.either._

import scala.collection.compat.{Factory, _}
import scala.language.higherKinds

object internal {

  implicit lazy val validatedNelApplicative: Applicative[ValidatedNel[CactusFailure, *]] = Validated.catsDataApplicativeErrorForValidated

  def CollAToCollB[A, B, Coll[X] <: Iterable[X]](fieldPath: String, coll: Coll[A])(
      implicit factory: Factory[B, Coll[B]],
      aToBConverter: Converter[A, B]): ResultOrErrors[Coll[B]] = {
    import cats.instances.either._
    import cats.instances.list._
    import cats.syntax.traverse._

    coll
      .map(a => aToBConverter.apply(fieldPath)(a))
      .toList
      .sequence
      .map(_.to(factory))
  }

  def AToB[A, B](fieldPath: String)(a: A)(implicit aToBConverter: Converter[A, B]): ResultOrErrors[B] = {
    aToBConverter.apply(fieldPath)(a)
  }

  implicit class ResultsListOps[R](val e: List[ResultOrErrors[R]]) {
    def combined: ResultOrErrors[List[R]] = {
      import cats.instances.either._
      import cats.instances.list._
      import cats.syntax.all._

      // this implementation is more effective than using Validated from cats

      val (lefts, rights) = e.separate

      if (lefts.isEmpty) {
        Right(rights): ResultOrErrors[List[R]]
      } else {
        val failures = lefts.foldLeft(List.empty[CactusFailure]) { case (acc, NonEmptyList(head, tail)) => (acc :+ head) ++ tail }

        Left(NonEmptyList.fromListUnsafe(failures)): ResultOrErrors[List[R]]
      }
    }
  }

  implicit class ResultOs[R](val r: ResultOrErrors[R]) extends AnyVal {
    def liftToOption: ResultOrErrors[Option[R]] = {
      r.map(Option(_)).recover { case _ => None }
    }
  }
}
