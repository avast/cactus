package com.avast.cactus

import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom
import scala.language.experimental.macros
import scala.language.{higherKinds, implicitConversions}
import scala.reflect.macros._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object CactusMacros {

  private val OptPattern = "Option\\[(.*)\\]".r

  implicit def CollAToCollB[A, B, T[X] <: TraversableLike[X, T[X]]](coll: T[A])(implicit cbf: CanBuildFrom[T[A], B, T[B]], aToBConverter: A => B): T[B] = {
    coll.map(aToBConverter)
  }

  implicit def TryAToTryB[A, B](ta: Try[A])(implicit aToBConverter: A => B): Try[B] = {
    ta.map(aToBConverter)
  }

  implicit def OptAToOptB[A, B](ta: Option[A])(implicit aToBConverter: A => B): Option[B] = {
    ta.map(aToBConverter)
  }

  def tryToOption[A](t: Try[A]): Option[A] = t match {
    case Success(a) => Option(a)
    case Failure(e) if e.isInstanceOf[CactusException] => None
    case Failure(NonFatal(e)) => throw e
  }

  def convertGpbToCaseClassT[A: c.WeakTypeTag](c: whitebox.Context): c.Expr[Either[CactusFailure, A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    c.Expr[Either[CactusFailure, A]] {
      q""" {
          import com.avast.cactus.CactusException
          import com.avast.cactus.CactusFailure
          import com.avast.cactus.CactusMacros._

          import scala.util.Try
          import scala.collection.JavaConverters._

          try {
            Right(${GpbToCaseClass.createConverter(c)(tpe, q" ${TermName("gpb")} ")})
          } catch {
            case e: CactusException => Left(e.failure)
          }
         }
        """
    }
  }

  private object GpbToCaseClass {

    def createConverter(c: whitebox.Context)(tpe: c.universe.Type, gpb: c.Tree): c.Tree = {
      import c.universe._

      if (!tpe.typeSymbol.isClass) {
        c.abort(c.enclosingPosition, s"Provided type $tpe is not a class")
      }

      val classSymbol = tpe.typeSymbol.asClass

      if (!classSymbol.isCaseClass) {
        c.abort(c.enclosingPosition, s"Provided type $tpe is not a case class")
      }

      val ctor = tpe.decls.collectFirst {
        case m: MethodSymbol if m.isPrimaryConstructor => m
      }.get

      val fields = ctor.paramLists.flatten

      val params = fields.map { field =>
        val fieldName = field.name.decodedName.toTermName
        val returnType = field.typeSignature

        val annotsTypes = field.annotations.map(_.tree.tpe.toString)
        val annotsParams = field.annotations.map {
          _.tree.children.tail.map { case q" $name = $value " =>
            name.toString() -> c.eval[String](c.Expr(q"$value"))
          }.toMap
        }

        val annotations = annotsTypes.zip(annotsParams).toMap

        val gpbNameAnnotations = annotations.find { case (key, _) => key == classOf[GpbName].getName }

        val nameInGpb = gpbNameAnnotations.flatMap { case (_, par) =>
          par.get("value")
        }.map(_.toString()).getOrElse(fieldName.toString)

        val upper = firstUpper(nameInGpb.toString)
        val query = TermName(s"has$upper")
        val getter = TermName(s"get$upper")

        val value = processEndType(c)(fieldName, returnType)(q"$gpb.$query", q"$gpb.$getter")

        c.Expr(q"$fieldName = $value")
      }

      q" { ${TermName(classSymbol.name.toString)}(..$params) } "
    }


    private def processEndType(c: whitebox.Context)
                              (name: c.universe.TermName, returnType: c.universe.Type)
                              (query: c.universe.Tree, getter: c.universe.Tree): c.Tree = {
      import c.universe._

      val typeSymbol = returnType.typeSymbol
      val resultType = returnType.resultType

      resultType.toString match {
        case OptPattern(t) => // Option[T]
          val typeArg = resultType.typeArgs.head // it's an Option, so it has 1 type arg

          q"CactusMacros.OptAToOptB(CactusMacros.tryToOption(Try(${processEndType(c)(name, typeArg)(query, getter)})))"

        case t if typeSymbol.isClass && typeSymbol.asClass.isCaseClass => // case class
          q" if ($query) ${createConverter(c)(returnType, q"$getter ")} else throw CactusException(MissingFieldFailure(${name.toString})) "


        case t if typeSymbol.isClass && typeSymbol.asClass.baseClasses.map(_.name.toString).contains("TraversableLike") => // collection
          // collections don't have the "has" method, test size instead
          q" if ($getter.size() > 0) $getter.asScala.toList else throw CactusException(MissingFieldFailure(${name.toString})) "

        case t => // plain type
          q" if ($query) $getter else throw CactusException(MissingFieldFailure(${name.toString})) "
      }
    }
  }

  private object CaseClassToGpb {

  }

  private def firstUpper(s: String): String = {
    s.charAt(0).toUpper + s.substring(1)
  }

}
