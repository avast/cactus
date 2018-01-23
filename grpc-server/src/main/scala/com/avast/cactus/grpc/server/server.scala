package com.avast.cactus.grpc

import io.grpc.{BindableService, ServerInterceptor, ServerInterceptors, ServerServiceDefinition}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.language.experimental.macros
import scala.reflect.ClassTag

package object server {

  implicit class MapService[MT](val myTrait: MT) extends AnyVal {
    def mappedTo[JS <: BindableService](implicit ct: ClassTag[MT], ec: ExecutionContext): JS = macro ServerMacros.mapImplToService[JS]
  }

  implicit class AddInterceptor(val s: BindableService) extends AnyVal {
    def withInterceptors(i: ServerInterceptor*): ServerServiceDefinition = ServerInterceptors.intercept(s, i.asJava)
  }

}
