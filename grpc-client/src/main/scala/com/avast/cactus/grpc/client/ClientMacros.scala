package com.avast.cactus.grpc.client

import io.grpc.stub.AbstractStub

import scala.reflect.macros.whitebox

class ClientMacros(val c: whitebox.Context) {

  import c.universe._

  def mapClientToTrait[GrpcClientStub <: AbstractStub[GrpcClientStub] : WeakTypeTag, MyTrait: WeakTypeTag](ec: c.Tree): c.Expr[MyTrait] = {

    val stubType = weakTypeOf[GrpcClientStub]
    val traitType = weakTypeOf[MyTrait]
    val traitTypeSymbol = traitType.typeSymbol

    if (!traitTypeSymbol.isClass || !traitTypeSymbol.asClass.isTrait || traitTypeSymbol.typeSignature.takesTypeArgs ) {
      c.abort(c.enclosingPosition, s"The $traitTypeSymbol must be a trait without type arguments")
    }


    ???
  }
}
