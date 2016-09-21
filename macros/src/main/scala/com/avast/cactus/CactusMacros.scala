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

  implicit def CollAToCollB[A, B, T[X] <: TraversableLike[X, T[X]]](coll: T[A])(implicit cbf: CanBuildFrom[T[A], B, T[B]], aToBConverter: Converter[A, B]): T[B] = {
    coll.map(aToBConverter.apply)
  }

  implicit def TryAToTryB[A, B](ta: Try[A])(implicit aToBConverter: Converter[A, B]): Try[B] = {
    ta.map(aToBConverter.apply)
  }

  implicit def OptAToOptB[A, B](ta: Option[A])(implicit aToBConverter: Converter[A, B]): Option[B] = {
    ta.map(aToBConverter.apply)
  }

  implicit def AToB[A, B](a: A)(implicit aToBConverter: Converter[A, B]): B = {
    aToBConverter.apply(a)
  }

  def tryToOption[A](t: Try[A]): Option[A] = t match {
    case Success(a) => Option(a)
    case Failure(e) if e.isInstanceOf[CactusException] => None
    case Failure(NonFatal(e)) => throw e
  }

  def convertGpbToCaseClass[CaseClass: c.WeakTypeTag](c: whitebox.Context)(gpbCt: c.Tree): c.Expr[Either[CactusFailure, CaseClass]] = {
    import c.universe._

    val caseClassType = weakTypeOf[CaseClass]

    // unpack the implicit ClassTag tree
    val gpbSymbol = extractSymbolFromClassTag(c)(gpbCt)

    val variableName = getVariableName(c)

    c.Expr[Either[CactusFailure, CaseClass]] {
      q""" {
          import com.avast.cactus.CactusException
          import com.avast.cactus.CactusFailure
          import com.avast.cactus.CactusMacros._

          import scala.util.Try
          import scala.collection.JavaConverters._

          try {
            Right(${GpbToCaseClass.createConverter(c)(caseClassType, gpbSymbol.typeSignature.asInstanceOf[c.universe.Type], variableName)})
          } catch {
            case e: CactusException => Left(e.failure)
          }
         }
        """
    }
  }

  def convertCaseClassToGpb[Gpb: c.WeakTypeTag](c: whitebox.Context)(caseClassCt: c.Tree): c.Expr[Either[CactusFailure, Gpb]] = {
    import c.universe._

    // unpack the implicit ClassTag tree
    val caseClassSymbol = extractSymbolFromClassTag(c)(caseClassCt)

    val variableName = getVariableName(c)

    c.Expr[Either[CactusFailure, Gpb]] {
      q""" {
          import com.avast.cactus.CactusException
          import com.avast.cactus.CactusFailure
          import com.avast.cactus.CactusMacros._

          import scala.util.Try
          import scala.collection.JavaConverters._

          try {
            Right(${CaseClassToGpb.createConverter(c)(caseClassSymbol.typeSignature.asInstanceOf[c.universe.Type], weakTypeOf[Gpb], variableName)})
          } catch {
            case e: CactusException => Left(e.failure)
          }
         }
        """
    }
  }

  private object GpbToCaseClass {

    def createConverter(c: whitebox.Context)(caseClassType: c.universe.Type, gpbType: c.universe.Type, gpb: c.Tree): c.Tree = {
      import c.universe._

      val i = initialize(c)(caseClassType, gpbType)
      import i._

      val params = fields.map { field =>
        val e = extractField(c)(field, gpbGetters)
        import e._

        val query = TermName(s"has$upper")

        val value = processEndType(c)(fieldName, returnType, gpbType)(q"$gpb.$query", q"$gpb.$gpbGetter")

        c.Expr(q"$fieldName = $value")
      }

      q" { ${TermName(caseClassSymbol.name.toString)}(..$params) } "
    }


    private def processEndType(c: whitebox.Context)
                              (name: c.universe.TermName, returnType: c.universe.Type, gpbType: c.universe.Type)
                              (query: c.universe.Tree, getter: c.universe.Tree): c.Tree = {
      import c.universe._

      val typeSymbol = returnType.typeSymbol
      val resultType = returnType.resultType

      resultType.toString match {
        case OptPattern(t) => // Option[T]
          val typeArg = resultType.typeArgs.head // it's an Option, so it has 1 type arg

          q"(CactusMacros.tryToOption(Try(${processEndType(c)(name, typeArg, gpbType)(query, getter)})))"

        case t if typeSymbol.isClass && typeSymbol.asClass.isCaseClass => // case class

          val internalGpbType = gpbType.decls.collectFirst {
            case m: MethodSymbol if m.name.toString == getter.toString().split("\\.").reverse.head => m.returnType
          }.getOrElse(c.abort(c.enclosingPosition, "Could not determine internal GPB type"))

          q" if ($query) ${createConverter(c)(returnType, internalGpbType, q"$getter ")} else throw CactusException(MissingFieldFailure(${name.toString})) "

        case t if typeSymbol.isClass && typeSymbol.asClass.baseClasses.map(_.name.toString).contains("TraversableLike") => // collection
          // collections don't have the "has" method, test size instead
          q" if (!$getter.isEmpty) CactusMacros.CollAToCollB($getter.asScala.toList) else throw CactusException(MissingFieldFailure(${name.toString})) "

        case t => // plain type
          q" if ($query) $getter else throw CactusException(MissingFieldFailure(${name.toString})) "
      }
    }
  }

  private object CaseClassToGpb {
    def createConverter(c: whitebox.Context)(caseClassType: c.universe.Type, gpbType: c.universe.Type, caseClass: c.Tree): c.Tree = {
      import c.universe._

      val i = initialize(c)(caseClassType, gpbType)
      import i._

      val gpbClassSymbol = gpbType.typeSymbol.asClass

      val params = fields.map { field =>
        val e = extractField(c)(field, gpbGetters)
        import e._

        val setter = TermName(s"set$upper")

        val assignment = processEndType(c)(q"$caseClass.$fieldName", returnType)(gpbType, gpbGetter, q"builder.$setter", upper)

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
                              (gpbType: c.universe.Type, gpbGetter: c.universe.MethodSymbol, setter: c.universe.Tree, upperFieldName: String): c.Tree = {
      import c.universe._

      val typeSymbol = returnType.typeSymbol
      val resultType = returnType.resultType

      resultType.toString match {
        case OptPattern(t) => // Option[T]
          val typeArg = resultType.typeArgs.head // it's an Option, so it has 1 type arg

          q" $field.foreach(value => ${processEndType(c)(q"value", typeArg)(gpbType, gpbGetter, setter, upperFieldName)}) "

        case t if typeSymbol.isClass && typeSymbol.asClass.isCaseClass => // case class

          q" $setter(${createConverter(c)(typeSymbol.typeSignature, gpbGetter.returnType, q" $field ")}) "

        case t if typeSymbol.isClass && typeSymbol.asClass.baseClasses.map(_.name.toString).contains("TraversableLike") => // collection

          val l = if (upperFieldName.endsWith("List")) upperFieldName.substring(0, upperFieldName.length - 4) else upperFieldName
          val addMethod = TermName(s"addAll$l")

          val fieldGenType = returnType.typeArgs.head // it's collection, it HAS type arg

          val getterResultType = gpbGetter.returnType.resultType
          val getterGenType = getterResultType.typeArgs.headOption
            .getOrElse {
              if (getterResultType.toString == "com.google.protobuf.ProtocolStringList") {
                typeOf[java.lang.String]
              } else {
                c.abort(c.enclosingPosition, s"Could not convert $field to Seq[$getterResultType]")
              }
            }

          // the implicit conversion wouldn't be used implicitly
          // we have to specify types to be converted manually, because type inference cannot determine it
          // the collections is converted to mutable Seq here, since it has to have unified format for the conversion
          q"""
              ${TermName("builder")}.$addMethod(CollAToCollB[$fieldGenType, $getterGenType, scala.Seq]($field.toSeq).asJava)
           """

        case t => // plain type

          q" $setter($field) "
      }
    }

  }


  private def initialize(c: whitebox.Context)(caseClassType: c.universe.Type, gpbType: c.universe.Type) = new {

    import c.universe._

    if (!caseClassType.typeSymbol.isClass) {
      c.abort(c.enclosingPosition, s"Provided type $caseClassType is not a class")
    }

    val caseClassSymbol = caseClassType.typeSymbol.asClass

    if (!caseClassSymbol.isCaseClass) {
      c.abort(c.enclosingPosition, s"Provided type $caseClassType is not a case class")
    }

    val ctor = caseClassType.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.get

    val fields = ctor.paramLists.flatten

    val gpbGetters = gpbType.decls.collect {
      case m: MethodSymbol if m.name.toString.startsWith("get") && !m.isStatic => m
    }
  }

  private def extractField(c: whitebox.Context)(field: c.universe.Symbol, gpbGetters: Iterable[c.universe.MethodSymbol]) = new {

    import c.universe._

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

    // find getter for the field in GPB
    // try *List first for case it's a repeated field and user didn't name it *List in the case class
    val gpbGetter = gpbGetters
      .find(_.name.toString == s"get${upper}List") // collection ?
      .orElse(gpbGetters.find(_.name.toString == s"get$upper"))
      .getOrElse(c.abort(c.enclosingPosition, s"Could not find getter in GPB for field $fieldName"))

  }

  private def extractSymbolFromClassTag[CaseClass: c.WeakTypeTag](c: whitebox.Context)(gpbCt: c.Tree) = {
    import c.universe._

    (gpbCt match {
      case q"ClassTag.apply[$cl](${_}): ${_}" => cl
    }).symbol
  }

  private def getVariableName[Gpb: c.WeakTypeTag](c: whitebox.Context): c.universe.Tree = {
    import c.universe._

    val variableName = c.prefix.tree match {
      case q"cactus.this.`package`.${_}[${_}]($n)" => n
      case q"com.avast.cactus.`package`.${_}[${_}]($n)" => n

      case t => c.abort(c.enclosingPosition, s"Cannot process the conversion - variable name extraction from tree ${t} failed")
    }

    q" $variableName "
  }

  private def firstUpper(s: String): String = {
    s.charAt(0).toUpper + s.substring(1)
  }

}
