package com.avast.cactus.grpc.client

import com.avast.cactus.CactusMacros
import com.avast.cactus.grpc.ServerError
import com.google.common.util.concurrent.ListenableFuture
import com.google.protobuf.MessageLite
import io.grpc.stub.AbstractStub

import scala.reflect.macros.whitebox

class ClientMacros(val c: whitebox.Context) {

  import c.universe._

  def mapClientToTraitWithInterceptors[GrpcClientStub <: AbstractStub[GrpcClientStub]: WeakTypeTag, MyTrait: WeakTypeTag](
      interceptors: c.Tree*)(ec: c.Tree, ex: c.Tree): c.Expr[MyTrait] = {

    val stubType = weakTypeOf[GrpcClientStub]
    val traitType = weakTypeOf[MyTrait]
    val traitTypeSymbol = traitType.typeSymbol

    if (!traitTypeSymbol.isClass || !traitTypeSymbol.asClass.isTrait || traitTypeSymbol.typeSignature.takesTypeArgs) {
      CactusMacros.terminateWithInfo(c) {
        s"The $traitTypeSymbol must be a plain trait without type arguments"
      }
    }

    val apiMethods = getApiMethods(stubType)
    val methodsMappings = getMethodsMapping(traitType, stubType, apiMethods)

    checkAbstractMethods(traitType, methodsMappings.keySet)

    val channelVar = CactusMacros.getVariable(c)
    val mappingMethods = methodsMappings.map { case (im, am) => generateMappingMethod(im, am) }

    val stub = {
      q" ${stubType.typeSymbol.owner}.newFutureStub($channelVar).withInterceptors(com.avast.cactus.grpc.client.ClientMetadataInterceptor) "
    }

    val closeMethod = if (traitType.baseClasses.exists(_.fullName == "java.lang.AutoCloseable")) {
      if (channelVar.tpe.members.exists(m => m.isMethod && m.asMethod.name.toString == "shutdownNow")) {
        q"$channelVar.shutdownNow()"
      } else {
        CactusMacros.terminateWithInfo(c) {
          s"$traitType extends java.lang.AutoCloseable but the requirement could not be satisfied because given channel is of type ${channelVar.tpe.typeSymbol.fullName} which is not closeable"
        }
      }
    } else q" () "

    c.Expr[MyTrait] {
      val t =
        q"""
         new com.avast.cactus.grpc.client.ClientInterceptorsWrapper(scala.collection.immutable.Seq(..$interceptors)) with $traitType with _root_.java.lang.AutoCloseable {
            private val ex: java.util.concurrent.Executor = $ex

            private def newStub = $stub

            import com.avast.cactus.grpc.client.ClientCommonMethods._
            import com.avast.cactus.v3._

            ..$mappingMethods

            override def close(): Unit = { $closeMethod }
        }
       """

      if (CactusMacros.Debug) println(t)

      t
    }
  }

  private def generateMappingMethod(implMethod: ImplMethod, apiMethod: ApiMethod): Tree = {
    q"""
       override def ${implMethod.name}(request: ${implMethod.request}): scala.concurrent.Future[com.avast.cactus.grpc.ServerResponse[${implMethod.response}]] = withInterceptors {
          val stub = newStub

          request.asGpb[${apiMethod.request}] match {
             case scala.util.Right(req) => executeRequest[${apiMethod.request}, ${apiMethod.response}, ${implMethod.response}](req, stub.${apiMethod.name}, ex)
             case scala.util.Left(errors) =>
                scala.concurrent.Future.successful {
                   Left {
                      com.avast.cactus.grpc.ServerError(io.grpc.Status.INVALID_ARGUMENT.withDescription(formatCactusFailures("request", errors)))
                   }
                }
          }
        }
     """
  }

  private def checkAbstractMethods(traitType: Type, implMethods: Set[ImplMethod]): Unit = {
    val nonGrpcAbstractMethods = traitType.decls
      .collect {
        case m if m.isMethod => m.asMethod
      }
      .filter(m => m.isAbstract && !implMethods.exists(_.name == m.name))

    if (nonGrpcAbstractMethods.nonEmpty) {
      val foundIllegalMethods = nonGrpcAbstractMethods
        .map { m =>
          CactusMacros.methodToString(c)(m)
        }
        .mkString("\n - ", "\n - ", "")

      CactusMacros.terminateWithInfo(c) {
        s"Only gRPC methods are allowed to be abstract in type ${traitType.typeSymbol}, found others too: $foundIllegalMethods"
      }
    }
  }

  private def getApiMethods(t: Type): Seq[ApiMethod] = {
    t.decls.collect {
      case ApiMethod(m) => m
    }
  }.toSeq

  private def getMethodsMapping(traitType: Type, stubType: Type, apiMethods: Seq[ApiMethod]): Map[ImplMethod, ApiMethod] = {
    val methods = traitType.decls
      .collect {
        case ImplMethod(m) => m
      }

    methods.map { m =>
      val apiMethod = apiMethods
        .find(_.name == m.name)
        .getOrElse {
          CactusMacros.terminateWithInfo(c) {
            s"Method ${m.name} of ${traitType.typeSymbol} does not have it's counterpart in ${stubType.typeSymbol}"
          }
        }

      m -> apiMethod
    }.toMap
  }

  private def isGpbClass(t: Type): Boolean = t.baseClasses.contains(typeOf[MessageLite].typeSymbol)

  private case class ImplMethod(name: TermName, request: Type, response: Type)

  private object ImplMethod {
    def unapply(s: Symbol): Option[ImplMethod] = {
      if (s.isMethod) {
        val m = s.asMethod

        if (m.paramLists.size == 1) {
          val reqType = m.paramLists.head.head.typeSignature

          // TODO type matching
          val serverError = typeOf[ServerError].dealias.typeSymbol
          val future = typeOf[scala.concurrent.Future[_]].dealias.typeConstructor
          val either = typeOf[scala.util.Either[_, _]].dealias.typeConstructor

          for {
            f <- Some(m.returnType.dealias) if f.dealias.typeConstructor == future // Future[_]
            e <- f.typeArgs.headOption if e.dealias.typeConstructor == either // Future[Either[_,_]]
            ea <- Some(e.dealias.typeArgs) if ea.head.dealias.typeSymbol == serverError // Future[Either[ServerError,_]]
          } yield {
            val respType = ea(1)

            if (!m.isAbstract)
              CactusMacros.terminateWithInfo(c) {
                s"Method ${m.name} of trait ${m.owner} has to be abstract to be able to implement"
              }

            new ImplMethod(m.name, reqType, respType)
          }
        } else None
      } else None
    }
  }

  private case class ApiMethod(name: TermName, request: Type, response: Type)

  private object ApiMethod {
    def unapply(s: Symbol): Option[ApiMethod] = {
      Option(s)
        .collect {
          case m
              if m.isMethod
                && m.name.toString != "build"
                && m.asMethod.paramLists.size == 1
                && m.asMethod.paramLists.head.size == 1
                && m.asMethod.returnType.dealias.erasure == typeOf[ListenableFuture[Any]].dealias.erasure =>
            m.asMethod.name -> (m.asMethod.paramLists.flatten.head.typeSignature -> m.asMethod.returnType.finalResultType)
        }
        .collect {
          case (name, (req: Type, resp: Type)) if isGpbClass(req) && resp.typeArgs.forall(isGpbClass) =>
            ApiMethod(name, req, resp.typeArgs.head)
        }
    }
  }

}
