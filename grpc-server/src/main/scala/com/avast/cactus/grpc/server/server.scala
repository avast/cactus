package com.avast.cactus.grpc

import io.grpc._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.reflect.ClassTag

package object server {

  type ServerAsyncInterceptor = GrpcMetadata => Future[Either[Status, GrpcMetadata]]

  implicit class MapService[MT](val myTrait: MT) extends AnyVal {
    def mappedToService[JS <: BindableService](interceptors: ServerAsyncInterceptor*)(implicit ct: ClassTag[MT], ec: ExecutionContext): ServerServiceDefinition =
      macro ServerMacros.mapImplToService[JS]
  }

}
