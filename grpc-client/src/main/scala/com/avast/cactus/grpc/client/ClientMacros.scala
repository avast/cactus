package com.avast.cactus.grpc.client

import com.avast.cactus.CactusMacros
import com.avast.cactus.grpc.ServerError
import com.google.common.util.concurrent.ListenableFuture
import com.google.protobuf.MessageLite
import io.grpc.stub.AbstractStub

import scala.language.higherKinds
import scala.reflect.macros.whitebox

class ClientMacros(val c: whitebox.Context) {

  import c.universe._

  def mapClientToTraitWithInterceptors[GrpcClientStub <: AbstractStub[GrpcClientStub]: WeakTypeTag, F[_], MyTrait: WeakTypeTag](
      interceptors: c.Tree*)(ec: c.Tree, ex: c.Tree): c.Expr[MyTrait] = {

    val stubType = weakTypeOf[GrpcClientStub]
    val traitType = weakTypeOf[MyTrait]
    val traitTypeSymbol = traitType.typeSymbol

    // fType cannot be get with weakTypeOf => https://issues.scala-lang.org/browse/SI-8919
    val fType = traitType
      .baseType(traitType.baseClasses.find(_.fullName == classOf[GrpcClient[F]].getName).getOrElse {
        c.abort(c.enclosingPosition, s"The $traitType does not extend GrpcClient[F]")
      })
      .typeArgs
      .headOption
      .getOrElse {
        c.abort(c.enclosingPosition, s"Unable to extract F from GrpcClient[F] - please report a bug")
      }

    if (!traitTypeSymbol.isClass || !traitTypeSymbol.asClass.isTrait || traitTypeSymbol.typeSignature.takesTypeArgs) {
      CactusMacros.terminateWithInfo(c) {
        s"The $traitTypeSymbol must be a plain trait without type arguments"
      }
    }

    if (CactusMacros.Debug) {
      println(s"Mapping $traitType to $stubType (F = $fType)")
    }

    val apiMethods = getApiMethods(stubType)
    val methodsMappings = getMethodsMapping(traitType, fType, stubType, apiMethods)

    checkAbstractMethods(traitType, methodsMappings.keySet)

    val channelVar = CactusMacros.getVariable(c)
    val mappingMethods = methodsMappings.map { case (im, am) => generateMappingMethod(fType, im, am) }

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

  private def generateMappingMethod(fType: Type, implMethod: ImplMethod, apiMethod: ApiMethod): Tree = {
    val returnType = tq"com.avast.cactus.grpc.ServerResponse[${implMethod.response}]"
    val fReturnType = tq"${fType.typeSymbol}[$returnType]"

    q"""
       override def ${implMethod.name}(request: ${implMethod.request}): $fReturnType = adaptToF[$fType, $returnType] {
          withInterceptors {
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

  private def getApiMethods(traitType: Type): Seq[ApiMethod] = {
    traitType.decls.flatMap(ApiMethod.extract).toSeq
  }

  private def getMethodsMapping(traitType: Type, fType: Type, stubType: Type, apiMethods: Seq[ApiMethod]): Map[ImplMethod, ApiMethod] = {
    val methods = traitType.decls.flatMap(ImplMethod.extract(_, fType))

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
    def extract(s: Symbol, fType: Type): Option[ImplMethod] = {
      if (s.isMethod) {
        val m = s.asMethod

        if (m.paramLists.size == 1) {
          val reqType = m.paramLists.head.head.typeSignature

          // TODO type matching
          val serverError = typeOf[ServerError].dealias.typeSymbol
          val theF = fType.typeConstructor
          val either = typeOf[scala.util.Either[_, _]].dealias.typeConstructor

          for {
            f <- Some(m.returnType.dealias) if f.dealias.typeConstructor == theF // F[_]
            e <- f.typeArgs.headOption if e.dealias.typeConstructor == either // F[Either[_,_]]
            ea <- Some(e.dealias.typeArgs) if ea.head.dealias.typeSymbol == serverError // F[Either[ServerError,_]]
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
    def extract(s: Symbol): Option[ApiMethod] = {
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
