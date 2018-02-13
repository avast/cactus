package com.avast.cactus.v3

import com.avast.cactus.ResultOrErrors
import com.google.protobuf.Message

import scala.annotation.implicitNotFound
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

@implicitNotFound("Could not find an instance of AnyValueConverter from ${Gpb}, try to import or define one")
trait AnyValueConverter[Gpb] {
  def apply(fieldPath: String)(a: AnyValue): ResultOrErrors[Gpb]
}

object AnyValueConverter {

  implicit def deriveAnyValueToGpbConverter[To <: Message]: AnyValueConverter[To] = macro anyValueConverter[To]

  def anyValueConverter[GpbClass: c.WeakTypeTag](c: whitebox.Context): c.Expr[AnyValueConverter[GpbClass]] = {
    import c.universe._

    val anyValueType = typeOf[AnyValue]
    val gpbType = weakTypeOf[GpbClass]

    val gpbTypeName = c.Expr[String](q"${gpbType.companion}.getDefaultInstance.getDescriptorForType.getFullName")

    val theFunction = {
      q"""
            {
               import com.avast.cactus._
               import com.avast.cactus.CactusMacros._

               import org.scalactic._
               import org.scalactic.Accumulation._

               import scala.util.Try
               import scala.util.control.NonFatal
               import scala.collection.JavaConverters._

               try {
                 if (anyValInstance.typeUrl == "type.googleapis.com/" + $gpbTypeName) {
                    Good(${gpbType.companion}.parseFrom(anyValInstance.bytes))
                 } else {
                    Bad(One(WrongAnyTypeFailure(fieldPath, anyValInstance.typeUrl, "type.googleapis.com/" + $gpbTypeName)))
                 }
               } catch { case NonFatal(e) => Bad(One(UnknownFailure(fieldPath, e))) }
            }
         """
    }

    c.Expr[AnyValueConverter[GpbClass]] {
      q"""
         new AnyValueConverter[$gpbType] {
            def apply(fieldPath: String)(anyValInstance: $anyValueType): ResultOrErrors[$gpbType] = $theFunction.toEitherNEL
         }
       """
    }
  }

}
