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

  def convertGpbToCaseClass[Gpb: c.WeakTypeTag](c: whitebox.Context): c.Expr[Either[CactusFailure, Gpb]] = {
    import c.universe._

    val tpe = weakTypeOf[Gpb]

    c.Expr[Either[CactusFailure, Gpb]] {
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

  def convertCaseClassToGpb[Gpb: c.WeakTypeTag](c: whitebox.Context)(ct: c.Tree): c.Expr[Either[CactusFailure, Gpb]] = {
    import c.universe._

    // unpack the implicit ClassTag tree
    val caseClassSymbol = (ct match {
      case q"ClassTag.apply[$cl](${_}): ${_}" => cl
    }).symbol

    c.Expr[Either[CactusFailure, Gpb]] {
      val tree =
        q""" {
          import com.avast.cactus.CactusException
          import com.avast.cactus.CactusFailure
          import com.avast.cactus.CactusMacros._

          import scala.util.Try
          import scala.collection.JavaConverters._

          try {
            Right(${CaseClassToGpb.createConverter(c)(caseClassSymbol.typeSignature.asInstanceOf[c.universe.Type], weakTypeOf[Gpb], q" ${TermName("caseClass")} ")})
          } catch {
            case e: CactusException => Left(e.failure)
          }
         }
        """

      println(tree)

      tree
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
          q" if (!$getter.isEmpty) $getter.asScala.toList else throw CactusException(MissingFieldFailure(${name.toString})) "

        case t => // plain type
          q" if ($query) $getter else throw CactusException(MissingFieldFailure(${name.toString})) "
      }
    }
  }

  private object CaseClassToGpb {
    def createConverter(c: whitebox.Context)(caseClassType: c.universe.Type, gpbType: c.universe.Type, caseClass: c.Tree): c.Tree = {
      import c.universe._

      if (!caseClassType.typeSymbol.isClass) {
        c.abort(c.enclosingPosition, s"Provided type $caseClassType is not a class")
      }

      val gpbClassSymbol = gpbType.typeSymbol.asClass

      gpbType.typeSymbol.typeSignature

      val caseClassSymbol = caseClassType.typeSymbol.asClass

      if (!caseClassSymbol.isCaseClass) {
        c.abort(c.enclosingPosition, s"Provided type $caseClassType is not a case class")
      }

      val ctor = caseClassType.decls.collectFirst {
        case m: MethodSymbol if m.isPrimaryConstructor => m
      }.get

      val fields = ctor.paramLists.flatten

      println(s"Fields: $fields")

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
        val setter = TermName(s"set$upper")

        val assignment = processEndType(c)(q"$caseClass.$fieldName", returnType)(q"builder.$setter")

        c.Expr(q" $assignment ")
      }

      q"""
        {
          val builder = ${TermName(gpbClassSymbol.name.toString)}.newBuilder()

          ..$params

          builder.build()
        }
       """
    }

    private def processEndType(c: whitebox.Context)
                              (field: c.universe.Tree, returnType: c.universe.Type)
                              (setter: c.universe.Tree): c.Tree = {
      import c.universe._

      val typeSymbol = returnType.typeSymbol
      val resultType = returnType.resultType

      resultType.toString match {
        case OptPattern(t) => // Option[T]
          val typeArg = resultType.typeArgs.head // it's an Option, so it has 1 type arg

          q""

//          q"""
//              $field.foreach(${processEndType(c)(field, typeArg)(setter)}.get)
//           """

          //q"CactusMacros.OptAToOptB(CactusMacros.tryToOption(Try(${processEndType(c)(field, typeArg)(query, getter)})))"

        case t if typeSymbol.isClass && typeSymbol.asClass.isCaseClass => // case class

          q""

          //q" if ($query) ${createConverter(c)(returnType, q"$getter ")} else throw CactusException(MissingFieldFailure(${field.toString})) "


        case t if typeSymbol.isClass && typeSymbol.asClass.baseClasses.map(_.name.toString).contains("TraversableLike") => // collection

          q""

          // collections don't have the "has" method, test size instead
          //q" if ($getter.size() > 0) $getter.asScala.toList else throw CactusException(MissingFieldFailure(${field.toString})) "

        case t => // plain type

          q" $setter($field) "

          //q" if ($query) $getter else throw CactusException(MissingFieldFailure(${field.toString})) "
      }
    }

  }

  private def firstUpper(s: String): String = {
    s.charAt(0).toUpper + s.substring(1)
  }

}
