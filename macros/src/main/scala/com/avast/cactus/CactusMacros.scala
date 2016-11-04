package com.avast.cactus

import java.util.function.{Function => JavaFunction}

import org.scalactic.{Every, Or}

import scala.collection.JavaConverters._
import scala.collection.TraversableLike
import scala.collection.generic.CanBuildFrom
import scala.language.experimental.macros
import scala.language.{higherKinds, implicitConversions}
import scala.reflect.macros._

object CactusMacros {

  private val Debug = false

  private val OptPattern = "Option\\[(.*)\\]".r

  implicit def CollAToCollB[A, B, T[X] <: TraversableLike[X, T[X]]](coll: T[A])(implicit cbf: CanBuildFrom[T[A], B, T[B]], aToBConverter: Converter[A, B]): T[B] = {
    coll.map(aToBConverter.apply)
  }

  implicit def JavaListAToJavaListB[A, B](list: java.lang.Iterable[A])(implicit aToBConverter: Converter[A, B]): java.lang.Iterable[B] = {
    //TODO optimize
    list.asScala.map(aToBConverter.apply).asJava
  }

  implicit def OrAToOrB[A, B](ta: Or[A, Every[CactusFailure]])(implicit aToBConverter: Converter[A, B]): Or[B, Every[CactusFailure]] = {
    ta.map(aToBConverter.apply)
  }

  implicit def AToB[A, B](a: A)(implicit aToBConverter: Converter[A, B]): B = {
    aToBConverter.apply(a)
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
          import com.avast.cactus.CactusFailure
          import com.avast.cactus.CactusMacros._

          import org.scalactic._
          import org.scalactic.Accumulation._

          import scala.util.Try
          import scala.collection.JavaConverters._

          Good(${CaseClassToGpb.createConverter(c)(caseClassSymbol.typeSignature.asInstanceOf[c.universe.Type], weakTypeOf[Gpb], variableName)})
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

        val value = processEndType(c)(fieldName, nameInGpb, dstType, gpbType)(q"$gpb.$query", q"$gpb.$gpbGetter", gpbGetter)

        c.Expr(q"val $fieldName: $dstType Or Every[CactusFailure] = { $value }")
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
                              (fieldName: c.universe.TermName, nameInGpb: String, returnType: c.universe.Type, gpbType: c.universe.Type)
                              (query: c.universe.Tree, getter: c.universe.Tree, gpbGetterMethod: c.universe.MethodSymbol): c.Tree = {
      import c.universe._

      val dstResultType = returnType.resultType

      val srcTypeSymbol = gpbGetterMethod.returnType.resultType.typeSymbol
      val dstTypeSymbol = dstResultType.typeSymbol

      dstResultType.toString match {
        case OptPattern(t) => // Option[T]
          val typeArg = dstResultType.typeArgs.head // it's an Option, so it has 1 type arg

          q""" {
                 val value: $typeArg Or Every[CactusFailure] = {${processEndType(c)(fieldName, nameInGpb, typeArg, gpbType)(query, getter, gpbGetterMethod)}}

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

          dstResultType.typeArgs.headOption match {
            case Some(typeArg) =>
              val vectorTypeSymbol = typeOf[Vector[_]].typeSymbol

              val toFinalCollection = if (dstTypeSymbol != vectorTypeSymbol && !vectorTypeSymbol.asClass.baseClasses.contains(dstTypeSymbol)) {
                q" CactusMacros.OrAToOrB[Vector[$typeArg],${dstTypeSymbol.name.toTypeName}[$typeArg]] "
              } else {
                q" identity "
              }

              q" $toFinalCollection(Good($getter.asScala.toVector)) "

            case None =>
              // TODO what if the src type is not a collection?
              val getterGenType = extractGpbGenType(c)(gpbGetterMethod)

              q" Good(CactusMacros.AToB[Vector[$getterGenType], $dstResultType]($getter.asScala.toVector)) "
          }

        case t => // plain type

          val value = if (srcTypeSymbol != dstTypeSymbol) {
            if (Debug) {
              println(s"Requires converter from $srcType to $dstType")
            }

            q" CactusMacros.AToB[$srcTypeSymbol, $dstTypeSymbol]($getter) "
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

        val assignment = processEndType(c)(q"$caseClass.$fieldName", dstType)(gpbType, gpbGetter, q"builder.$setter", upper)

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
                              (field: c.universe.Tree, srcReturnType: c.universe.Type)
                              (gpbType: c.universe.Type, gpbGetterMethod: c.universe.MethodSymbol, setter: c.universe.Tree, upperFieldName: String): c.Tree = {
      import c.universe._

      val typeSymbol = srcReturnType.typeSymbol
      val dstResultType = srcReturnType.resultType

      val dstTypeSymbol = gpbGetterMethod.returnType.resultType.typeSymbol
      val srcTypeSymbol = dstResultType.typeSymbol

      dstResultType.toString match {
        case OptPattern(t) => // Option[T]
          val typeArg = dstResultType.typeArgs.head // it's an Option, so it has 1 type arg

          q" $field.foreach(value => ${processEndType(c)(q"value", typeArg)(gpbType, gpbGetterMethod, setter, upperFieldName)}) "

        case t if caseClassAndGpb(c)(typeSymbol, gpbGetterMethod) => // case class -> GPB

          q" $setter(${createConverter(c)(typeSymbol.typeSignature, gpbGetterMethod.returnType, q" $field ")}) "

        case t if isCollection(c)(typeSymbol) => // collection

          val l = if (upperFieldName.endsWith("List")) upperFieldName.substring(0, upperFieldName.length - 4) else upperFieldName
          val addMethod = TermName(s"addAll$l")

          val getterGenType = extractGpbGenType(c)(gpbGetterMethod)

          // TODO what if the dst type is not a collection?
          val (fieldGenType, value) = srcReturnType.typeArgs.headOption match {
            case Some(genType) => (genType, field)
            case None => (getterGenType, q" CactusMacros.AToB[$srcTypeSymbol, Vector[$getterGenType]]($field) ")
          }
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

          val value = if (srcTypeSymbol != dstTypeSymbol) {
            if (Debug) {
              println(s"Requires converter from $srcTypeSymbol to $dstTypeSymbol")
            }

            q" CactusMacros.AToB[$srcTypeSymbol, $dstTypeSymbol]($field) "
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
    val dstType = field.typeSignature

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

  private def extractGpbGenType(c: whitebox.Context)(gpbGetterMethod: c.universe.MethodSymbol) = {
    import c.universe._

    val getterResultType = gpbGetterMethod.returnType.resultType
    val getterGenType = getterResultType.typeArgs.headOption
      .getOrElse {
        if (getterResultType.toString == "com.google.protobuf.ProtocolStringList") {
          typeOf[java.lang.String]
        } else {
          c.abort(c.enclosingPosition, s"Could not extract generic type from $getterResultType")
        }
      }

    getterGenType
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
