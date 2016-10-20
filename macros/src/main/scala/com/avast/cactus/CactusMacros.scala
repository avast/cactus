package com.avast.cactus

import org.scalactic.{Every, Or}

import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom
import scala.language.experimental.macros
import scala.language.{higherKinds, implicitConversions}
import scala.reflect.macros._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object CactusMacros {

  private val Debug = false

  private val OptPattern = "Option\\[(.*)\\]".r

  def CollAToCollB[A, B, T[X] <: TraversableLike[X, T[X]]](coll: T[A])(implicit cbf: CanBuildFrom[T[A], B, T[B]], aToBConverter: Converter[A, B]): T[B] = {
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

  def convertGpbToCaseClass[CaseClass: c.WeakTypeTag](c: whitebox.Context)(gpbCt: c.Tree): c.Expr[CaseClass Or Every[CactusFailure]] = {
    import c.universe._

    val caseClassType = weakTypeOf[CaseClass]

    // unpack the implicit ClassTag tree
    val gpbSymbol = extractSymbolFromClassTag(c)(gpbCt)

    val variableName = getVariableName(c)

    c.Expr[CaseClass Or Every[CactusFailure]] {
      val tree =
        q""" {
          import com.avast.cactus.CactusException
          import com.avast.cactus.CactusFailure
          import com.avast.cactus.CactusMacros._

          import org.scalactic._
          import org.scalactic.Accumulation._

          import scala.util.Try
          import scala.collection.JavaConverters._

          ${GpbToCaseClass.createConverter(c)(caseClassType, gpbSymbol.typeSignature.asInstanceOf[c.universe.Type], variableName)}
         }
        """

      if (Debug) println(tree)

      tree
    }
  }

  def convertCaseClassToGpb[Gpb: c.WeakTypeTag](c: whitebox.Context)(caseClassCt: c.Tree): c.Expr[Gpb Or Every[CactusFailure]] = {
    import c.universe._

    // unpack the implicit ClassTag tree
    val caseClassSymbol = extractSymbolFromClassTag(c)(caseClassCt)

    val variableName = getVariableName(c)

    c.Expr[Gpb Or Every[CactusFailure]] {
      val tree =
        q""" {
          import com.avast.cactus.CactusException
          import com.avast.cactus.CactusFailure
          import com.avast.cactus.CactusMacros._

          import org.scalactic._
          import org.scalactic.Accumulation._

          import scala.util.Try
          import scala.collection.JavaConverters._

          try {
            Good(${CaseClassToGpb.createConverter(c)(caseClassSymbol.typeSignature.asInstanceOf[c.universe.Type], weakTypeOf[Gpb], variableName)})
          } catch {
            case e: CactusException => Bad(One(e.failure))
          }
         }
        """

      if (Debug) println(tree)

      tree
    }
  }

  private object GpbToCaseClass {

    def createConverter(c: whitebox.Context)(caseClassType: c.universe.Type, gpbType: c.universe.Type, gpb: c.Tree): c.Tree = {
      import c.universe._

      val i = initialize(c)(caseClassType, gpbType)
      import i._

      if (Debug) {
        println(s"Converting ${gpbType.typeSymbol} to ${caseClassType.typeSymbol}")
      }

      val params = fields.map { field =>
        val e = extractField(c)(field, gpbGetters)
        import e._

        val query = TermName(s"has$upper")

        val value = processEndType(c)(fieldName, nameInGpb, returnType, gpbType)(q"$gpb.$query", q"$gpb.$gpbGetter", gpbGetter)

        c.Expr(q"val $fieldName: $returnType Or Every[CactusFailure] = { $value }")
      }

      val fieldNames = fields.map(_.name.toTermName)

      q"""
         {
            ..$params

            withGood(..$fieldNames) { ${TermName(caseClassSymbol.name.toString)}.apply }
         }
       """
    }


    private def processEndType(c: whitebox.Context)
                              (name: c.universe.TermName, nameInGpb: String, returnType: c.universe.Type, gpbType: c.universe.Type)
                              (query: c.universe.Tree, getter: c.universe.Tree, gpbGetterMethod: c.universe.MethodSymbol): c.Tree = {
      import c.universe._

      val typeSymbol = returnType.typeSymbol
      val resultType = returnType.resultType

      resultType.toString match {
        case OptPattern(t) => // Option[T]
          val typeArg = resultType.typeArgs.head // it's an Option, so it has 1 type arg

          q""" {
                 val value: $typeArg Or Every[CactusFailure] = {${processEndType(c)(name, nameInGpb, typeArg, gpbType)(query, getter, gpbGetterMethod)}}

                 value.map(Option(_)).recover(_ => None)
               }
           """

        case t if caseClassAndGpb(c)(typeSymbol, gpbGetterMethod) => // GPB -> case class

          val internalGpbType = gpbType.decls.collectFirst {
            case m: MethodSymbol if m.name.toString == getter.toString().split("\\.").reverse.head => m.returnType
          }.getOrElse(c.abort(c.enclosingPosition, "Could not determine internal GPB type"))

          if (Debug) {
            println(s"Internal case class $typeSymbol, GPB type: ${internalGpbType.typeSymbol}")
          }

          q" if ($query) ${createConverter(c)(returnType, internalGpbType, q"$getter ")} else Bad(One(MissingFieldFailure($nameInGpb))) "

        case t if isCollection(c)(typeSymbol) => // collection

          val toFinalCollection = TermName(typeSymbol.name.toString match {
            case "List" => "toList"
            case _ => "toVector"
          })

          // collections don't have the "has" method, test if empty instead
          q" Good(CactusMacros.CollAToCollB($getter.asScala.$toFinalCollection)) "

        case t => // plain type

          val srcType = gpbGetterMethod.returnType.resultType.typeSymbol
          val dstType = returnType.resultType.typeSymbol

          val value = if (srcType != dstType) {
            if (Debug) {
              println(s"Requires converter from $srcType to $dstType")
            }

            q" CactusMacros.AToB[$srcType, $dstType]($getter) "
          } else q" $getter "

          q" if ($query) Good($value) else Bad(One(MissingFieldFailure($nameInGpb))) "
      }
    }
  }

  private object CaseClassToGpb {
    def createConverter(c: whitebox.Context)(caseClassType: c.universe.Type, gpbType: c.universe.Type, caseClass: c.Tree): c.Tree = {
      import c.universe._

      val i = initialize(c)(caseClassType, gpbType)
      import i._

      val gpbClassSymbol = gpbType.typeSymbol.asClass

      if (Debug) {
        println(s"Converting ${caseClassType.typeSymbol} to ${gpbType.typeSymbol}")
      }

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
                              (gpbType: c.universe.Type, gpbGetterMethod: c.universe.MethodSymbol, setter: c.universe.Tree, upperFieldName: String): c.Tree = {
      import c.universe._

      val typeSymbol = returnType.typeSymbol
      val resultType = returnType.resultType

      resultType.toString match {
        case OptPattern(t) => // Option[T]
          val typeArg = resultType.typeArgs.head // it's an Option, so it has 1 type arg

          q" $field.foreach(value => ${processEndType(c)(q"value", typeArg)(gpbType, gpbGetterMethod, setter, upperFieldName)}) "

        case t if caseClassAndGpb(c)(typeSymbol, gpbGetterMethod) => // case class -> GPB

          q" $setter(${createConverter(c)(typeSymbol.typeSignature, gpbGetterMethod.returnType, q" $field ")}) "

        case t if isCollection(c)(typeSymbol) => // collection

          val l = if (upperFieldName.endsWith("List")) upperFieldName.substring(0, upperFieldName.length - 4) else upperFieldName
          val addMethod = TermName(s"addAll$l")

          val fieldGenType = returnType.typeArgs.head // it's collection, it HAS type arg

          val getterResultType = gpbGetterMethod.returnType.resultType
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

          val srcType = returnType.resultType.typeSymbol
          val dstType = gpbGetterMethod.returnType.resultType.typeSymbol

          val value = if (srcType != dstType) {
            if (Debug) {
              println(s"Requires converter from $srcType to $dstType")
            }

            q" CactusMacros.AToB[$srcType, $dstType]($field) "
          } else q" $field "

          q" $setter($value) "
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
      .getOrElse {
        if (Debug) {
          println(s"No getter for $fieldName found in GPB - neither ${s"get${upper}List"} nor ${s"get$upper"}")
          println(s"All getters: ${gpbGetters.map(_.name.toString).mkString("[", ", ", "]")}")
        }

        c.abort(c.enclosingPosition, s"Could not find getter in GPB for field $fieldName")
      }

  }

  private def extractSymbolFromClassTag[CaseClass: c.WeakTypeTag](c: whitebox.Context)(gpbCt: c.Tree) = {
    import c.universe._

    (gpbCt match {
      case q"ClassTag.apply[$cl](${_}): ${_}" => cl

      case t => c.abort(c.enclosingPosition, s"Cannot process the conversion - variable type extraction from tree '$t' failed")
    }).symbol
  }

  private def getVariableName[Gpb: c.WeakTypeTag](c: whitebox.Context): c.universe.Tree = {
    import c.universe._

    val variableName = c.prefix.tree match {
      case q"cactus.this.`package`.${_}[${_}]($n)" => n
      case q"com.avast.cactus.`package`.${_}[${_}]($n)" => n

      case t => c.abort(c.enclosingPosition, s"Cannot process the conversion - variable name extraction from tree '$t' failed")
    }

    q" $variableName "
  }

  private def caseClassAndGpb(c: whitebox.Context)(caseClassTypeSymbol: c.universe.Symbol, gpbGetterMethod: c.universe.MethodSymbol): Boolean = {
    caseClassTypeSymbol.isClass && caseClassTypeSymbol.asClass.isCaseClass && gpbGetterMethod.returnType.baseClasses.map(_.name.toString).contains("MessageLite")
  }

  private def isCollection(c: whitebox.Context)(typeSymbol: c.universe.Symbol): Boolean = {
    typeSymbol.isClass && typeSymbol.asClass.baseClasses.map(_.name.toString).contains("TraversableLike")
  }

  private def firstUpper(s: String): String = {
    s.charAt(0).toUpper + s.substring(1)
  }

}
