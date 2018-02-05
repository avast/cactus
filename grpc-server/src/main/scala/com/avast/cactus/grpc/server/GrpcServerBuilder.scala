package com.avast.cactus.grpc.server

import java.net.SocketAddress

import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.{BindableService, ServerServiceDefinition, Server => GrpcServer}

import scala.collection.JavaConverters._

trait GrpcServerBuilder {
  def withService(service: BindableService): GrpcServerBuilder

  def withService(service: ServerServiceDefinition): GrpcServerBuilder

  def withoutReflectionService: GrpcServerBuilder

  def build: GrpcServer

}

object GrpcServerBuilder {
  def apply(addr: SocketAddress): GrpcServerBuilder = {
    DefaultGrpcServerBuilder(
      addr = addr
    )
  }
}

private case class DefaultGrpcServerBuilder(addr: SocketAddress,
                                            services: Seq[Either[BindableService, ServerServiceDefinition]] = Seq.empty,
                                            includeReflectionService: Boolean = true)
    extends GrpcServerBuilder {

  override def withService(service: BindableService): GrpcServerBuilder = {
    copy(services = Left(service) +: services)
  }

  override def withService(service: ServerServiceDefinition): GrpcServerBuilder = {
    copy(services = Right(service) +: services)
  }

  override def withoutReflectionService: GrpcServerBuilder = {
    copy(includeReflectionService = false)
  }

  override def build: GrpcServer = {
    val serverBuilder = NettyServerBuilder.forAddress(addr)

    services.foreach {
      case Left(s) => serverBuilder.addService(s)
      case Right(s) => serverBuilder.addService(s)
    }

    if (includeReflectionService) serverBuilder.addService(ProtoReflectionService.newInstance())
    serverBuilder.build()
  }
}
