package com.avast.cactus.grpc

import io.grpc._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.language.experimental.macros

package object server {

  implicit class MapService[MT](val myTrait: MT) extends AnyVal {
    def mappedToService[JS <: BindableService](interceptors: ServerAsyncInterceptor*)(implicit ct: ClassTag[MT],
                                                                                      ec: ExecutionContext): ServerServiceDefinition =
      macro com.avast.cactus.grpc.server.ServerMacros.mapImplToService[JS]
  }

}
