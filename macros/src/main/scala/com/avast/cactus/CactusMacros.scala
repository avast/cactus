package com.avast.cactus

import org.scalactic.{Bad, Every, Good, Or}

import scala.collection.generic.CanBuildFrom
import scala.collection.{TraversableLike, mutable}
import scala.language.experimental.macros
import scala.language.{higherKinds, implicitConversions}
import scala.reflect.macros._

//noinspection TypeAnnotation
object CactusMacros {

  private[cactus] type AnnotationsMap = Map[String, Map[String, String]]

  private[cactus] val Debug = false

  private val OptPattern = "Option\\[(.*)\\]".r

  private[cactus] object ClassesNames {

    object Protobuf {
      val ProtocolStringList = "com.google.protobuf.ProtocolStringList"
      val ListValue = "com.google.protobuf.ListValue"
      val ByteString = "com.google.protobuf.ByteString"
      val MessageLite = "com.google.protobuf.MessageLite"
      val GeneratedMessageV3 = "com.google.protobuf.GeneratedMessageV3"
    }

    object Scala {
      val AnyVal = "scala.AnyVal"
      val TraversableLike = "scala.collection.TraversableLike"
    }

    object Java {
      val String = "java.lang.String"
      val Map = "java.util.Map"
      val Iterable = "java.lang.Iterable"
    }

  }

  private val JavaPrimitiveTypes = Set(
    classOf[java.lang.Boolean].getName,
    classOf[java.lang.Byte].getName,
    classOf[java.lang.Character].getName,
    classOf[java.lang.Short].getName,
    classOf[java.lang.Integer].getName,
    classOf[java.lang.Long].getName,
    classOf[java.lang.Double].getName,
    classOf[java.lang.Float].getName,
    classOf[java.lang.String].getName // consider String as primitive
  )

  def CollAToCollB[A, B, T[X] <: TraversableLike[X, T[X]]](fieldPath: String, coll: T[A])(implicit cbf: CanBuildFrom[T[A], B, T[B]], aToBConverter: Converter[A, B]): T[B] = {
    coll.map(aToBConverter.apply(fieldPath))
  }

  def AToB[A, B](fieldPath: String)(a: A)(implicit aToBConverter: Converter[A, B]): B = {
    aToBConverter.apply(fieldPath)(a)
  }

  def convertGpbToCaseClass[CaseClass: c.WeakTypeTag](c: whitebox.Context)(gpbCt: c.Tree): c.Expr[CaseClass Or Every[CactusFailure]] = {
    import c.universe._

    // unpack the implicit ClassTag tree
    val gpbSymbol = extractSymbolFromClassTag(c)(gpbCt)

    val variable = getVariable(c)

    val variableName = variable.symbol.asTerm.fullName.split('.').last

    c.Expr[CaseClass Or Every[CactusFailure]] {
      val caseClassType = weakTypeOf[CaseClass]
      val gpbType = gpbSymbol.typeSignature.asInstanceOf[c.universe.Type]

      val generatedConverters: mutable.Map[String, c.Tree] = mutable.Map.empty

      val converter = GpbToCaseClass.createConverter(c)(c.Expr[String](q"$variableName"), caseClassType, gpbType, variable)(generatedConverters)

      val finalConverters = generatedConverters.values

      val tree =
        q""" {
          import com.avast.cactus.CactusFailure
          import com.avast.cactus.CactusMacros._

          import org.scalactic._
          import org.scalactic.Accumulation._

          import scala.util.Try
          import scala.collection.JavaConverters._

          ..$finalConverters

          $converter
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

    val variable = getVariable(c)

    c.Expr[Gpb Or Every[CactusFailure]] {
      val caseClassType = caseClassSymbol.typeSignature.asInstanceOf[c.universe.Type]
      val gpbType = weakTypeOf[Gpb]

      val generatedConverters: mutable.Map[String, c.Tree] = mutable.Map.empty

      val converter = CaseClassToGpb.createConverter(c)(caseClassType, gpbType, variable)(generatedConverters)

      val finalConverters = generatedConverters.values

      val tree =
        q""" {
          import com.avast.cactus.CactusFailure
          import com.avast.cactus.CactusMacros._

          import org.scalactic._
          import org.scalactic.Accumulation._

          import scala.util.Try
          import scala.collection.JavaConverters._

          ..$finalConverters

          Good($converter).orBad[org.scalactic.Every[com.avast.cactus.CactusFailure]]
         }
        """

      if (Debug) println(tree)

      tree
    }
  }

  private[cactus] object GpbToCaseClass {

    def createConverter(c: whitebox.Context)
                       (fieldPath: c.universe.Expr[String], caseClassType: c.universe.Type, gpbType: c.universe.Type, gpb: c.Tree)
                       (implicit converters: mutable.Map[String, c.universe.Tree]): c.Tree = {
      import c.universe._

      val i = initialize(c)(caseClassType, gpbType)
      import i._

      if (Debug) {
        println(s"Converting ${gpbType.typeSymbol} to ${caseClassType.typeSymbol}")
      }

      val params = fields.map { field =>
        val e = extractField(c)(field, isProto3, gpbGetters, gpbSetters)
        import e._

        if (Debug) println(s"$fieldName field type: $fieldType")

        val value: c.Tree = fieldType match {
          case n: FieldType.Normal[MethodSymbol, ClassSymbol, Type] =>
            val returnType = n.getter.returnType
            val query = protoVersion.getQuery(c)(gpb, upper, returnType)

            val innerFilePath = c.Expr[String] {
              q"""$fieldPath + "." + $nameInGpb"""
            }

            processEndType(c)(fieldName, innerFilePath, annotations, nameInGpb, dstType)(query, q"$gpb.${n.getter}", returnType)

          case o: FieldType.OneOf[MethodSymbol, ClassSymbol, Type] =>
            processOneOf(c)(gpbType, gpb)(o)
        }

        c.Expr(q"val $fieldName: $dstType Or Every[CactusFailure] = { $value }")
      }

      val fieldNames = fields.map(_.name.toTermName)
      val fieldTypes = fields.map(_.typeSignature.resultType)
      val fieldsWithTypes = (fieldNames zip fieldTypes).map { case (n, t) => q"$n:$t" }

      // prevent Deprecated warning from scalactic.Or
      val mappingFunction = if (fieldNames.size > 1) {
        q" withGood(..$fieldNames) "
      } else {
        q" ${fieldNames.head}.map "
      }

      q"""
         {
            ..$params

            $mappingFunction { ..$fieldsWithTypes => ${caseClassSymbol.companion}(..${fieldNames.map(f => q"$f = $f")}) }
         }
       """
    }

    private def processOneOf(c: whitebox.Context)
                            (gpbType: c.universe.Type, gpb: c.Tree)
                            (oneOfType: FieldType.OneOf[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
      import c.universe._

      val conv = ProtoVersion.V3.newOneOfConverterToCaseClass(c)(gpbType, oneOfType)

      // be able to change NOT_SET state to `None`, if the type is wrapped in `Option`
      oneOfType.classType.resultType.toString match {
        case OptPattern(_) => q""" ($conv($gpb)).map(Option(_)).recover( _=> None) """
        case _ => q" $conv($gpb) "
      }
    }

    private[cactus] def processEndType(c: whitebox.Context)
                                      (fieldName: c.universe.TermName, fieldPath: c.universe.Expr[String], fieldAnnotations: Map[String, Map[String, String]], nameInGpb: String, returnType: c.universe.Type)
                                      (query: Option[c.universe.Tree], getter: c.universe.Tree, getterReturnType: c.universe.Type)
                                      (implicit converters: mutable.Map[String, c.Tree]): c.Tree = {
      import c.universe._

      val dstResultType = returnType.resultType
      val srcResultType = getterReturnType.resultType

      val srcTypeSymbol = srcResultType.typeSymbol
      val dstTypeSymbol = dstResultType.typeSymbol

      dstResultType.toString match {
        case OptPattern(_) => // Option[T]
          val dstTypeArg = dstResultType.typeArgs.head // it's an Option, so it has 1 type arg

          val wrappedDstType = wrapDstType(c)(dstTypeArg)

          newConverter(c)(srcResultType, wrappedDstType) {
            q" (fieldPath: String, t: $srcResultType) => ${processEndType(c)(fieldName, c.Expr[String](q"fieldPath"), fieldAnnotations, nameInGpb, dstTypeArg)(None, q" t ", getterReturnType)} "
          }

          query match {
            case Some(q) =>
              q""" {
                 if ($q) {
                   val value: $dstTypeArg Or Every[CactusFailure] = CactusMacros.AToB[$srcResultType, $wrappedDstType]($fieldPath)($getter)

                   value.map(Option(_)).recover(_ => None)
                 } else { Good[Option[$dstTypeArg]](None).orBad[Every[CactusFailure]] }
               }
           """
            case None =>
              q""" {
                 val value: $dstTypeArg Or Every[CactusFailure] = CactusMacros.AToB[$srcResultType, $wrappedDstType]($fieldPath)($getter)

                 value.map(Option(_)).recover(_ => None)
               }
           """
          }

        case _ if caseClassAndGpb(c)(dstTypeSymbol, getterReturnType) => // GPB -> case class
          if (Debug) {
            println(s"Internal case $dstTypeSymbol, GPB type: ${getterReturnType.typeSymbol}") // `class` word missing by intention
          }

          val wrappedDstType = wrapDstType(c)(dstResultType)

          newConverter(c)(srcResultType, wrappedDstType) {
            q" (fieldPath: String, t: $srcResultType) => ${createConverter(c)(c.Expr[String](q"fieldPath"), returnType, getterReturnType, q" t ")} "
          }

          val value = q" CactusMacros.AToB[$srcResultType, $wrappedDstType]($fieldPath)($getter) "

          query match {
            case Some(q) => q" if ($q) $value else Bad(One(MissingFieldFailure($nameInGpb))) "
            case None => value
          }


        case _ if isScalaMap(c)(dstTypeSymbol) =>

          fieldAnnotations.find { case (key, _) => key == classOf[GpbMap].getName } match {
            case Some((_, annot)) =>
              val keyFieldName = annot.getOrElse("key", c.abort(c.enclosingPosition, s"GpbMap annotation need 'key' to be filled in"))
              val valueFieldName = annot.getOrElse("value", c.abort(c.enclosingPosition, s"GpbMap annotation need 'value' to be filled in"))

              if (Debug) {
                println(s"Converting $srcTypeSymbol to Map from message with key = '$keyFieldName' and value = '$valueFieldName'")
              }

              val dstTypeArgs = dstResultType.typeArgs

              val (dstKeyType, dstValueType) = (dstTypeArgs.head, dstTypeArgs.tail.head)

              val getKeyField = TermName("get" + firstUpper(keyFieldName))
              val getValueField = TermName("get" + firstUpper(valueFieldName))


              val gpbGenType = extractGpbGenType(c)(getterReturnType)

              val srcKeyType = gpbGenType.member(getKeyField).asMethod.returnType
              val srcValueType = gpbGenType.member(getValueField).asMethod.returnType

              val keyField = if (typesEqual(c)(srcKeyType, dstKeyType)) {
                q" Good(f.$getKeyField).orBad[Every[com.avast.cactus.CactusFailure]] "
              } else {
                val wrappedDstType = CactusMacros.GpbToCaseClass.wrapDstType(c)(dstKeyType)

                newConverter(c)(srcKeyType, wrappedDstType) {
                  q" (fieldPath: String, t: $srcKeyType) => ${processEndType(c)(TermName("key"), c.Expr[String](q"fieldPath"), Map(), "nameInGpb", dstKeyType)(None, q" t ", srcKeyType)} "
                }

                q" CactusMacros.AToB[$srcKeyType, $wrappedDstType]($fieldPath)(f.$getKeyField) "
              }

              val valueField = if (typesEqual(c)(srcValueType, dstValueType)) {
                q" Good(f.$getValueField).orBad[Every[com.avast.cactus.CactusFailure]] "
              } else {
                val wrappedDstType = CactusMacros.GpbToCaseClass.wrapDstType(c)(dstValueType)

                newConverter(c)(srcValueType, wrappedDstType) {
                  q" (fieldPath: String, t: $srcValueType) => ${processEndType(c)(TermName("key"), c.Expr[String](q"fieldPath"), Map(), "nameInGpb", dstValueType)(None, q" t ", srcValueType)} "
                }

                q" CactusMacros.AToB[$srcValueType, $wrappedDstType]($fieldPath)(f.$getValueField) "
              }

              val wrappedDstType = wrapDstType(c)(dstResultType)

              newConverter(c)(srcResultType, wrappedDstType) {
                q""" (fieldPath: String, a: $srcResultType) => {
                          a.asScala
                            .map(f => $keyField -> $valueField)
                            .toSeq.map{ case(key, or) => withGood(key, or)(_ -> _) }.combined.map(_.toMap)
                        } """
              }

              q" CactusMacros.AToB[$srcResultType, $wrappedDstType]($fieldPath)($getter) "

            case None if isJavaMap(c)(srcTypeSymbol) =>
              val wrappedDstType = wrapDstType(c)(dstResultType)

              newConverter(c)(srcResultType, wrappedDstType) {
                ProtoVersion.V3.newConverterJavaToScalaMap(c)(srcResultType, dstResultType)
              }

              q" CactusMacros.AToB[$srcResultType, $wrappedDstType]($fieldPath)($getter) "

            case None =>
              if (Debug) {
                println(s"Map field $fieldName without annotation, fallback to raw conversion")
              }

              q" Good(CactusMacros.AToB[$srcResultType, $dstResultType]($fieldPath)($getter)) "
          }

        case _ if isScalaCollection(c)(dstTypeSymbol) || dstTypeSymbol.name == TypeName("Array") => // collection

          if (isJavaCollection(c)(srcTypeSymbol)) {
            (dstResultType.typeArgs.headOption, srcResultType.typeArgs.headOption) match {
              case (Some(dstTypeArg), srcTypeArgOpt) =>
                val vectorTypeSymbol = typeOf[Vector[_]].typeSymbol

                val toFinalCollection = if (symbolsEqual(c)(vectorTypeSymbol, dstTypeSymbol)) {
                  q" identity "
                } else {
                  q" CactusMacros.AToB[Vector[$dstTypeArg],${dstTypeSymbol.name.toTypeName}[$dstTypeArg]]($fieldPath) "
                }

                val srcTypeArg = srcTypeArgOpt.getOrElse {
                  if (srcTypeSymbol.fullName == ClassesNames.Protobuf.ProtocolStringList) {
                    typeOf[String]
                  } else {
                    c.abort(c.enclosingPosition, s"Expected ${ClassesNames.Protobuf.ProtocolStringList}, $srcResultType present, please report this bug")
                  }
                }

                if (typesEqual(c)(srcTypeArg, dstTypeArg)) {
                  q" Good($toFinalCollection($getter.asScala.toVector)) "
                } else {
                  val wrappedDstTypeArg = wrapDstType(c)(dstTypeArg)

                  newConverter(c)(srcTypeArg, wrappedDstTypeArg) {
                    q" (fieldPath: String, a: $srcTypeArg) =>  { ${processEndType(c)(fieldName, c.Expr[String](q"fieldPath"), fieldAnnotations, nameInGpb, dstTypeArg)(None, q" a ", srcTypeArg)} } "
                  }

                  q" $getter.asScala.map(CactusMacros.AToB[$srcTypeArg, $wrappedDstTypeArg]($fieldPath)).toVector.combined.map($toFinalCollection) "
                }

              case (_, _) =>
                val getterGenType = extractGpbGenType(c)(getterReturnType)

                if (Debug) {
                  println(s"Converting $srcTypeSymbol to $dstTypeSymbol, fallback to raw conversion")
                }

                q" Good(CactusMacros.AToB[Vector[$getterGenType], $dstResultType]($fieldPath)($getter.asScala.toVector)) "
            }
          } else {
            if (Debug) {
              println(s"Converting $srcTypeSymbol to $dstTypeSymbol, fallback to raw conversion")
            }

            q" Good(CactusMacros.AToB[$srcResultType, $dstResultType]($fieldPath)($getter)) "
          }

        case _ if isJavaCollection(c)(srcTypeSymbol) => // this means raw conversion, because otherwise it would have match before

          q" Good(CactusMacros.AToB[$srcResultType, $dstResultType]($fieldPath)($getter)) "

        case _ => // plain type

          val value = convertIfNeeded(c)(fieldPath, srcResultType, dstResultType)(getter)

          query match {
            case Some(q) => q" if ($q) Good($value) else Bad(One(MissingFieldFailure($fieldPath))) "
            case None => q" Good($value)"
          }
      }
    }

    private[cactus] def wrapDstType(c: whitebox.Context)(t: c.universe.Type): c.universe.Type = {
      extractType(c)(s"org.scalactic.Good[$t](???).orBad[org.scalactic.Every[CactusFailure]]")
    }

  }

  private[cactus] object CaseClassToGpb {
    def createConverter(c: whitebox.Context)
                       (caseClassType: c.universe.Type, gpbType: c.universe.Type, caseClass: c.Tree)
                       (implicit converters: mutable.Map[String, c.Tree]): c.Tree = {
      import c.universe._

      val i = initialize(c)(caseClassType, gpbType)
      import i._

      val gpbClassSymbol = gpbType.typeSymbol.asClass

      if (Debug) {
        println(s"Converting ${caseClassType.typeSymbol} to ${gpbType.typeSymbol}")
      }

      val params = fields.map { field =>
        val e = extractField(c)(field, isProto3, gpbGetters, gpbSetters)
        import e._

        val assgn = fieldType match {
          case n: FieldType.Normal[MethodSymbol, ClassSymbol, Type] =>
            val setterParam = n.setter.paramLists.headOption.flatMap(_.headOption)
              .getOrElse(c.abort(c.enclosingPosition, s"Could not extract param from setter for field $field"))

            processEndType(c)(q"$caseClass.$fieldName", annotations, dstType)(setterParam.typeSignature, q"builder.${n.setter.name}", upper)

          case o: FieldType.OneOf[MethodSymbol, ClassSymbol, Type] =>
            processOneOf(c)(gpbType, gpbSetters)(q"$caseClass.$fieldName", o)
        }

        c.Expr(q" $assgn ")
      }

      q"""
         {
            val builder = ${gpbClassSymbol.companion}.newBuilder()

            ..$params

            builder.build()
         }
       """
    }

    private def processOneOf(c: whitebox.Context)
                            (gpbType: c.universe.Type, gpbSetters: Iterable[c.universe.MethodSymbol])
                            (field: c.universe.Tree, oneOfType: FieldType.OneOf[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]): c.Tree = {
      import c.universe._

      def conv(finalType: c.universe.Type) = ProtoVersion.V3.newOneOfConverterToGpb(c)(gpbType, gpbSetters)(oneOfType.copy(classType = finalType))

      oneOfType.classType.resultType.toString match {
        case OptPattern(_) => q""" $field.foreach(${conv(oneOfType.classType.resultType.typeArgs.head)}) """
        case _ => q" ${conv(oneOfType.classType)}($field) "
      }
    }

    private[cactus] def processEndType(c: whitebox.Context)
                                      (field: c.universe.Tree, fieldAnnotations: Map[String, Map[String, String]], srcReturnType: c.universe.Type)
                                      (setterRequiredType: c.universe.Type, setter: c.universe.Tree, upperFieldName: String)
                                      (implicit converters: mutable.Map[String, c.universe.Tree]): c.Tree = {
      import c.universe._

      val srcResultType = srcReturnType.resultType
      val dstResultType = setterRequiredType.resultType

      val dstTypeSymbol = dstResultType.typeSymbol
      val srcTypeSymbol = srcResultType.typeSymbol

      srcResultType.toString match {
        case OptPattern(_) => // Option[T]
          val typeArg = srcResultType.typeArgs.head // it's an Option, so it has 1 type arg

          q" $field.foreach(value => ${processEndType(c)(q"value", fieldAnnotations, typeArg)(setterRequiredType, setter, upperFieldName)}) "

        case _ if caseClassAndGpb(c)(srcTypeSymbol, dstResultType) => // case class -> GPB

          newConverter(c)(srcResultType, dstResultType) {
            q" (a: $srcResultType) => ${createConverter(c)(srcResultType, setterRequiredType, q" a ")} "
          }

          q" $setter(CactusMacros.AToB[$srcResultType, $dstResultType]($field)) "

        case _ if isScalaMap(c)(srcTypeSymbol) => // Map[A, B]
          val addMethod = setter

          fieldAnnotations.find { case (key, _) => key == classOf[GpbMap].getName } match {
            case Some((_, annot)) =>
              val keyFieldName = annot.getOrElse("key", c.abort(c.enclosingPosition, s"GpbMap annotation need 'key' to be filled in"))
              val valueFieldName = annot.getOrElse("value", c.abort(c.enclosingPosition, s"GpbMap annotation need 'key' to be filled in"))

              if (Debug) {
                println(s"Converting Map to $dstTypeSymbol - message with key = '$keyFieldName' and value = '$valueFieldName'")
              }

              val srcTypeArgs = srcResultType.typeArgs

              val (srcKeyType, srcValueType) = (srcTypeArgs.head, srcTypeArgs.tail.head)

              val getKeyField = TermName("get" + firstUpper(keyFieldName))
              val getValueField = TermName("get" + firstUpper(valueFieldName))


              val gpbGenType = extractGpbGenType(c)(setterRequiredType)

              val dstKeyType = gpbGenType.member(getKeyField).asMethod.returnType
              val dstValueType = gpbGenType.member(getValueField).asMethod.returnType

              val keyField = if (typesEqual(c)(srcKeyType, dstKeyType)) {
                q" key "
              } else {
                newConverter(c)(srcKeyType, dstKeyType) {
                  q" (a: $srcKeyType) => ${processEndType(c)(q"a", Map(), srcKeyType)(dstKeyType, q" identity  ", "")} "
                }

                q" CactusMacros.AToB[$srcKeyType, $dstKeyType](key) "
              }

              val valueField = if (typesEqual(c)(srcValueType, dstValueType)) {
                q" value "
              } else {
                newConverter(c)(srcValueType, dstValueType) {
                  q" (a: $srcValueType) => ${processEndType(c)(q"a", Map(), srcValueType)(dstValueType, q" identity  ", "")} "
                }

                q" CactusMacros.AToB[$srcValueType, $dstValueType](value) "
              }

              val mapGpb = gpbGenType.companion

              newConverter(c)(srcResultType, dstResultType) {
                q"""
                   (t: $srcResultType) => {
                      val m: Iterable[$gpbGenType] = t.map{ _ match { case (key, value) =>
                            $mapGpb.newBuilder()
                            .${TermName("set" + firstUpper(keyFieldName))}($keyField)
                            .${TermName("set" + firstUpper(valueFieldName))}($valueField)
                            .build()
                        }
                      }

                      m.asJava
                    }
                 """
              }

              q"""
                $addMethod(
                   CactusMacros.AToB[$srcResultType, $dstResultType]($field)
                )
               """

            case None if isJavaMap(c)(dstTypeSymbol) =>
              newConverter(c)(srcResultType, dstResultType) {
                ProtoVersion.V3.newConverterScalaToJavaMap(c)(srcResultType, dstResultType)
              }

              q"""
                $addMethod(
                   CactusMacros.AToB[$srcResultType, $dstResultType]($field)
                )
               """

            case None =>
              if (Debug) {
                println(s"Map field $field without annotation, fallback to raw conversion")
              }

              q" $addMethod(CactusMacros.AToB[$srcResultType, $dstResultType]($field)) "
          }

        case _ if isScalaCollection(c)(srcTypeSymbol) || srcTypeSymbol.name == TypeName("Array") => // collection

          val addMethod = setter

          val getterGenType = extractGpbGenType(c)(setterRequiredType)

          if (isJavaCollection(c)(dstTypeSymbol)) {

            (dstResultType.typeArgs.headOption, srcResultType.typeArgs.headOption) match {
              case (Some(dstTypeArg), srcTypeArgOpt) =>

                val srcTypeArg = srcTypeArgOpt.getOrElse {
                  if (srcResultType.typeSymbol.fullName == ClassesNames.Protobuf.ProtocolStringList) {
                    typeOf[String]
                  }
                  else {
                    c.abort(c.enclosingPosition, s"Expected ${ClassesNames.Protobuf.ProtocolStringList}, $srcResultType present, please report this bug")
                  }
                }

                if (typesEqual(c)(srcTypeArg, dstTypeArg)) {

                  val javaIterable = if (srcTypeSymbol.name != TypeName("Array")) {
                    q" $field.asJava "
                  } else {
                    q" java.util.Arrays.asList($field: _*) "
                  }

                  q" $addMethod($javaIterable) "
                } else {

                  newConverter(c)(srcTypeArg, dstTypeArg) {
                    q" (a: $srcTypeArg) =>  { ${processEndType(c)(q"a", fieldAnnotations, srcTypeArg)(dstTypeArg, q"identity", " a ")} } "
                  }

                  q" $addMethod(CactusMacros.CollAToCollB[$srcTypeArg, $dstTypeArg, scala.collection.Seq]($field.toSeq).asJava) "
                }

              case (_, _) =>
                if (Debug) {
                  println(s"Converting $srcTypeSymbol to $dstTypeSymbol, fallback to conversion $srcTypeSymbol -> Seq[${getterGenType.typeSymbol}]")
                }

                q" $addMethod(CactusMacros.AToB[$srcResultType, scala.collection.Seq[$getterGenType]]($field).asJava) "

            }

          } else {
            if (Debug) {
              println(s"Converting $srcTypeSymbol to $dstTypeSymbol, fallback to raw conversion")
            }

            q" $addMethod(CactusMacros.AToB[$srcResultType, $dstResultType]($field)) "
          }

        case _ if isJavaCollection(c)(dstTypeSymbol) => // this means raw conversion, because otherwise it would have match before

          val addMethod = setter

          if (Debug) {
            println(s"Converting $srcTypeSymbol to $dstTypeSymbol, fallback to raw conversion")
          }

          q" $addMethod(CactusMacros.AToB[$srcResultType, $dstResultType]($field)) "

        case _ => // plain type

          val value = convertIfNeeded(c)(c.Expr[String](q""), srcResultType, dstResultType)(field) // TODO

          q" $setter($value) "
      }
    }

  }

  private def initialize(c: whitebox.Context)(caseClassType: c.universe.Type, gpbType: c.universe.Type) = new {

    import c.universe._

    if (!caseClassType.typeSymbol.isClass) {
      c.abort(c.enclosingPosition, s"Provided type ${caseClassType.typeSymbol} is not a class")
    }

    val caseClassSymbol = caseClassType.typeSymbol.asClass

    if (!caseClassSymbol.isCaseClass) {
      c.abort(c.enclosingPosition, s"Provided type ${caseClassType.typeSymbol} is not a case class")
    }

    val isProto3 = gpbType.baseClasses.exists(_.asType.fullName == ClassesNames.Protobuf.GeneratedMessageV3)

    if (Debug && isProto3) println(s"Type ${gpbType.typeSymbol} is detected as proto v3")

    val protoVersion = if (isProto3) ProtoVersion.V3 else ProtoVersion.V2

    val ctor = caseClassType.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.get

    val fields = ctor.paramLists.flatten.flatMap { field =>
      val annotations = field.annotations.map(_.tree.tpe.toString)

      annotations.find(_ == classOf[GpbIgnored].getName) match {
        case Some(_) =>
          if (!field.asTerm.isParamWithDefault) {
            c.abort(c.enclosingPosition, s"Field '${field.name}' of type ${caseClassType.typeSymbol.fullName} is annotated as GpbIgnored, but doesn't have default value")
          }

          None

        case None => Some(field)
      }
    }

    val gpbGetters = gpbType.decls.collect {
      case m: MethodSymbol if m.name.toString.startsWith("get") && !m.isStatic => m
    }

    private val newBuilderMethod = gpbType.companion.decls.collectFirst {
      case m: MethodSymbol if m.name.toString == "newBuilder" => m
    }.getOrElse(c.abort(c.enclosingPosition, s"Could not extract $gpbType.Builder"))

    val gpbSetters = newBuilderMethod.returnType.decls.collect {
      case m: MethodSymbol if (m.name.toString.startsWith("set") || m.name.toString.startsWith("addAll") || m.name.toString.startsWith("putAll")) && !m.isStatic => m
    }
  }

  private def extractField(c: whitebox.Context)(field: c.universe.Symbol, isProto3: Boolean,
                                                gpbGetters: Iterable[c.universe.MethodSymbol],
                                                gpbSetters: Iterable[c.universe.MethodSymbol]) = new {

    import c.universe._

    val fieldName = field.name.decodedName.toTermName
    val dstType = field.typeSignature

    val annotations = getAnnotations(c)(field)

    val gpbNameAnnotations = annotations.find { case (key, _) => key == classOf[GpbName].getName }

    val nameInGpb = gpbNameAnnotations.flatMap { case (_, par) =>
      par.get("value")
    }.map(_.toString()).getOrElse(fieldName.toString)

    val upper = firstUpper(nameInGpb)

    val fieldType: FieldType[MethodSymbol, ClassSymbol, Type] = {
      // find getter for the field in GPB
      // try *List first for case it's a repeated field and user didn't name it *List in the case class
      val gpbGetter = {
        gpbGetters
          .find(_.name.toString == s"get${upper}List") // collection ?
          .orElse(gpbGetters.find(_.name.toString == s"get${upper}Map")) // map ?
          .orElse(gpbGetters.find(_.name.toString == s"get$upper"))
          .map(Good(_))
          .getOrElse {
            if (Debug) {
              println(s"No getter for $fieldName found in GPB - neither ${s"get${upper}List"} nor ${s"get$upper"}")
              println(s"All getters: ${gpbGetters.map(_.name.toString).mkString("[", ", ", "]")}")
            }

            Bad(s"Could not find getter in GPB for field $nameInGpb ($fieldName in case class), does the field in GPB exist?")
          }
      }

      // find setter for the field in GPB
      val gpbSetter = {
        gpbSetters
          .find(_.name.toString == s"addAll$upper") // collection ?
          .orElse(gpbSetters.find(_.name.toString == s"putAll$upper")) // map ?
          .orElse(gpbSetters.find(_.name.toString == s"set$upper"))
          .map(Good(_))
          .getOrElse {
            if (Debug) {
              println(s"No setter for $fieldName found in GPB - neither ${s"addAll$upper"} nor ${s"set$upper"}")
              println(s"All setters: ${gpbSetters.map(_.name.toString).mkString("[", ", ", "]")}")
            }

            Bad(s"Could not find setter in GPB for field $nameInGpb ($fieldName in case class), does the field in GPB exist?")
          }
      }

      (for {
        getter <- gpbGetter
        setter <- gpbSetter
      } yield {
        FieldType.Normal[MethodSymbol, ClassSymbol, Type](getter, setter)
      }).recover {
        case err if isProto3 => // give it one more chance, it can be ONE-OF
          if (Debug) println(s"Testing ${dstType.typeSymbol} to being a ONE-OF")

          getOneOfType(c)(upper, dstType, annotations).getOrElse {
            c.abort(c.enclosingPosition, err)
          }
      }.get // gets FieldType or stops the compilation in regular way
    }
  }

  private def getAnnotations(c: whitebox.Context)(field: c.universe.Symbol): AnnotationsMap = {
    import c.universe._

    val annotsTypes = field.annotations.map(_.tree.tpe.typeSymbol.fullName)
    val annotsParams = field.annotations.map {
      _.tree.children.tail.map { case q" $name = $value " =>
        name.toString() -> c.eval[String](c.Expr(q"$value"))
      }.toMap
    }

    annotsTypes.zip(annotsParams).toMap
  }

  private def getOneOfType(c: whitebox.Context)(fieldNameUpper: String, fieldType: c.universe.Type, fieldAnnotations: AnnotationsMap): Option[FieldType.OneOf[c.universe.MethodSymbol, c.universe.ClassSymbol, c.universe.Type]] = {
    import c.universe._

    val resultType = fieldType.resultType

    val fieldTypeSymbol = (resultType.toString match {
      case OptPattern(_) => resultType.typeArgs.head
      case _ => fieldType
    }).typeSymbol.asType

    if (fieldTypeSymbol.isClass) {
      ProtoVersion.V3.extractNameOfOneOf(c)(fieldNameUpper, fieldAnnotations)
        .flatMap { name =>
          val asClass = fieldTypeSymbol.asClass

          if (asClass.isSealed) {
            val impls = getImpls(c)(asClass)

            // checks format of impls - single parameter
            impls.foreach { t =>
              t.typeSignature.decls.collectFirst {
                case m if m.isMethod && m.asMethod.isPrimaryConstructor =>
                  if (m.asMethod.paramLists.flatten.size != 1) c.abort(c.enclosingPosition, s"ONE-OF trait implementations has to have exactly one parameter - check $t")
              }
            }

            if (impls.isEmpty) c.abort(c.enclosingPosition, s"Didn't find any implementations for $fieldTypeSymbol")

            if (Debug) println(s"$fieldTypeSymbol is a ONE-OF, name '$name', impls $impls")

            Some(FieldType.OneOf[MethodSymbol, ClassSymbol, Type](name, fieldType, impls))
          } else None
        }
    } else None
  }

  private def getImpls(c: whitebox.Context)(cl: c.universe.ClassSymbol): Set[c.universe.ClassSymbol] = {
    import c.universe._

    init(c)(cl.owner)

    val companion = cl.companion
    if (companion.isModule) {
      init(c)(companion)
    }

    cl.knownDirectSubclasses.collect { case s if s.isClass => s.asClass }
  }

  // this is a hack - we need to force the initialization of the class before knowing it's impls
  private[cactus] def init(c: whitebox.Context)(s: c.universe.Symbol) = {
    import c.universe._

    s.typeSignature.decls.foreach {
      case a: ClassSymbol => a.selfType.baseClasses
      case _ =>
    }
  }

  private[cactus] def extractGpbGenType(c: whitebox.Context)(getterReturnType: c.universe.Type) = {
    import c.universe._

    val getterResultType = getterReturnType.resultType
    val getterGenType = getterResultType.typeArgs.headOption
      .getOrElse {
        getterResultType.toString match {
          case ClassesNames.Protobuf.ProtocolStringList => typeOf[java.lang.String]
          case ClassesNames.Protobuf.ListValue => typeOf[com.google.protobuf.Value]
          case _ => c.abort(c.enclosingPosition, s"Could not extract generic type from $getterResultType")
        }
      }

    getterGenType
  }

  private[cactus] def extractSymbolFromClassTag[CaseClass: c.WeakTypeTag](c: whitebox.Context)(gpbCt: c.Tree) = {
    import c.universe._

    (gpbCt match {
      case q"ClassTag.apply[$cl](${_}): ${_}" => cl

      case t => c.abort(c.enclosingPosition, s"Cannot process the conversion - variable type extraction from tree '$t' failed")
    }).symbol
  }

  private[cactus] def extractType(c: whitebox.Context)(q: String): c.universe.Type = {
    c.typecheck(c.parse(q)).tpe
  }

  private[cactus] def getVariable[Gpb: c.WeakTypeTag](c: whitebox.Context): c.universe.Tree = {
    import c.universe._

    val variable = c.prefix.tree match {
      case q"cactus.this.`package`.${_}[${_}]($n)" => n
      case q"cactus.this.`package`.${_}($n)" => n
      case q"com.avast.cactus.`package`.${_}[${_}]($n)" => n
      case q"com.avast.cactus.`package`.${_}($n)" => n

      case t => c.abort(c.enclosingPosition, s"Cannot process the conversion - variable name extraction from tree '$t' failed")
    }

    q" $variable "
  }

  private def caseClassAndGpb(c: whitebox.Context)(caseClassTypeSymbol: c.universe.Symbol, getterReturnType: c.universe.Type): Boolean = {
    caseClassTypeSymbol.isClass && caseClassTypeSymbol.asClass.isCaseClass && getterReturnType.baseClasses.exists(_.fullName == ClassesNames.Protobuf.MessageLite)
  }

  private def isScalaCollection(c: whitebox.Context)(typeSymbol: c.universe.Symbol): Boolean = {
    typeSymbol.isClass && typeSymbol.asClass.baseClasses.exists(_.fullName == ClassesNames.Scala.TraversableLike)
  }

  private def isJavaCollection(c: whitebox.Context)(typeSymbol: c.universe.Symbol): Boolean = {
    typeSymbol.isClass && typeSymbol.asClass.baseClasses.exists(_.fullName == ClassesNames.Java.Iterable) && typeSymbol.fullName != ClassesNames.Protobuf.ByteString
  }

  private def isScalaMap(c: whitebox.Context)(typeSymbol: c.universe.Symbol): Boolean = {
    import c.universe._

    isScalaCollection(c)(typeSymbol) && typeSymbol.name == TypeName("Map")
  }

  private def isJavaMap(c: whitebox.Context)(typeSymbol: c.universe.Symbol): Boolean = {
    typeSymbol.isClass && typeSymbol.asClass.baseClasses.exists(_.fullName == ClassesNames.Java.Map)
  }

  private def firstUpper(s: String): String = {
    s.charAt(0).toUpper + s.substring(1)
  }

  private[cactus] def typesEqual(c: whitebox.Context)(srcType: c.universe.Type, dstType: c.universe.Type): Boolean = {
    val srcTypeSymbol = srcType.typeSymbol
    val dstTypeSymbol = dstType.typeSymbol

    symbolsEqual(c)(srcTypeSymbol, dstTypeSymbol)
  }

  private[cactus] def symbolsEqual(c: whitebox.Context)(srcTypeSymbol: c.universe.Symbol, dstTypeSymbol: c.universe.Symbol): Boolean = {
    srcTypeSymbol == dstTypeSymbol || (srcTypeSymbol.isClass && srcTypeSymbol.asClass.baseClasses.contains(dstTypeSymbol))
  }

  private[cactus] def convertIfNeeded(c: whitebox.Context)(fieldPath: c.universe.Expr[String], srcType: c.universe.Type, dstType: c.universe.Type)(value: c.Tree): c.Tree = {
    import c.universe._

    val srcTypeSymbol = srcType.typeSymbol
    val dstTypeSymbol = dstType.typeSymbol

    if (typesEqual(c)(srcType, dstType)) {
      q" $value "
    } else {
      if (Debug) {
        println(s"Requires converter from $srcTypeSymbol to $dstTypeSymbol")
      }

      q" CactusMacros.AToB[$srcType, $dstType]($fieldPath)($value) "
    }
  }

  private[cactus] def newConverter(c: whitebox.Context)(from: c.universe.Type, to: c.universe.Type)
                                  (f: c.Tree)
                                  (implicit converters: mutable.Map[String, c.universe.Tree]): Unit = {
    import c.universe._

    def addConverter() = {
      val key = toConverterKey(c)(from, to)

      converters.getOrElse(key, {
        if (Debug) {
          println(s"Defining converter from ${from.typeSymbol} to ${to.typeSymbol}")
        }

        converters += key -> q" implicit lazy val ${TermName(s"conv${converters.size}")}:Converter[$from, $to] = Converter($f) "
      })
    }

    if (c.inferImplicitValue(extractType(c)(s"???.asInstanceOf[com.avast.cactus.Converter[$from, $to]]")).isEmpty) {
      val recursive = f match {
        case q" (($_: $_) => identity(CactusMacros.AToB[${f}, ${t}]($_))) " if f.tpe =:= from && t.tpe =:= to => true
        case _ => false
      }

      if (!recursive) {
        // skip primitive types, conversions already defined
        if (!(isPrimitive(c)(from) && isPrimitive(c)(to))) {
          addConverter()
        } else {
          if (Debug) {
            println(s"Skipping definition of converter from ${from.typeSymbol} to ${to.typeSymbol}")
          }
        }
      } else {
        if (Debug) {
          println(s"Skipping recursive definition of converter from ${from.typeSymbol} to ${to.typeSymbol}")
        }
      }
    } else {
      if (Debug) {
        println(s"Found in scope existing implicit converter from ${from.typeSymbol} to ${to.typeSymbol}")
      }
    }
  }

  private[cactus] def isPrimitive(c: whitebox.Context)(t: c.universe.Type) = {
    val typeSymbol = t.typeSymbol

    typeSymbol.asClass.baseClasses.exists(_.fullName == ClassesNames.Scala.AnyVal) || JavaPrimitiveTypes.contains(typeSymbol.fullName)
  }

  private def toConverterKey(c: whitebox.Context)(a: c.universe.Type, b: c.universe.Type): String = {
    val aKey = a.typeSymbol.fullName + a.typeArgs.mkString("+", "_", "+")
    val bKey = b.typeSymbol.fullName + b.typeArgs.mkString("+", "_", "+")

    val key = s"${aKey}__$bKey"
    key
  }

}
