package com.avast.cactus.grpc.client

import java.net.SocketAddress

import io.grpc.stub.AbstractStub

import scala.concurrent.ExecutionContext
import scala.language.experimental.macros

trait GrpcClientBuilder {

  def withAsyncInterceptor(interceptor: ClientAsyncInterceptor): GrpcClientBuilder

  def withPlainText(plainText: Boolean = true): GrpcClientBuilder

  def buildAndMap[GrpcClientStub <: AbstractStub[GrpcClientStub], MT](implicit ec: ExecutionContext): MT = {
    // the fake implementation is needed because limitations in macros: https://issues.scala-lang.org/browse/SI-7657
    throw new NotImplementedError // cannot use `???` because of Scala-style warnings
  }
}

object GrpcClientBuilder {
  def apply(address: SocketAddress): GrpcClientBuilder = {
    DefaultGrpcClientBuilder(
      addr = address
    )
  }
}

private[grpc] case class DefaultGrpcClientBuilder(addr: SocketAddress,
                                                  plainText: Boolean = false,
                                                  interceptors: Seq[ClientAsyncInterceptor] = Seq.empty)
    extends GrpcClientBuilder {
  override def withAsyncInterceptor(interceptor: ClientAsyncInterceptor): GrpcClientBuilder = {
    copy(interceptors = interceptor +: interceptors)
  }

  override def withPlainText(plainText: Boolean): GrpcClientBuilder = {
    copy(plainText = plainText)
  }

  override def buildAndMap[GrpcClientStub <: AbstractStub[GrpcClientStub], MT](implicit ec: ExecutionContext): MT =
    macro ClientMacros.mapClientToTrait[GrpcClientStub, MT]
}
