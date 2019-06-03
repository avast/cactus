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

  def mapClientToTraitWithInterceptors[GrpcClientStub <: AbstractStub[GrpcClientStub]: WeakTypeTag, F[_], MyTrait[_[_]]](
      interceptors: c.Tree*)(ec: c.Tree, ex: c.Tree, ct: c.Tree, as: c.Tree): c.Expr[MyTrait[F]] = {

    // fType and traitType cannot be get with weakTypeOf => https://issues.scala-lang.org/browse/SI-8919

    val stubType = weakTypeOf[GrpcClientStub]
    val traitType = CactusMacros.extractSymbolFromClassTag(c)(ct)
    val traitTypeSymbol = traitType.typeSymbol

    if (!traitTypeSymbol.isClass || !traitTypeSymbol.asClass.isTrait) {
      c.abort(
        c.enclosingPosition,
        s"The $traitTypeSymbol must be a trait"
      )
    }

    traitType.baseType(traitType.baseClasses.find(_.fullName == classOf[GrpcClient].getName).getOrElse {
      c.abort(c.enclosingPosition, s"The $traitType does not extend GrpcClient")
    })

    val fType = traitType.typeArgs.headOption
      .getOrElse {
        c.abort(c.enclosingPosition, s"Unable to extract F from $traitType - please report a bug")
      }

    val fSymbol = traitType.erasure.typeArgs.head.typeSymbol.asType

    if (CactusMacros.Debug) {
      println(s"Mapping $traitType to $stubType")
    }

    val apiMethods = getApiMethods(stubType)

    if (CactusMacros.Debug) println(apiMethods.mkString("Api methods: [",",","]"))

    val methodsMappings = getMethodsMapping(traitType, fSymbol, stubType, apiMethods)

    checkAbstractMethods(traitType, fSymbol, methodsMappings.keySet)

    val channelVar = CactusMacros.getVariable(c)
    val mappingMethods = methodsMappings.map { case (im, am) => generateMappingMethod(fType, im, am) }

    val stub = {
      q" ${stubType.typeSymbol.owner}.newFutureStub($channelVar).withInterceptors(com.avast.cactus.grpc.client.ClientMetadataInterceptor) "
    }

    val closeMethod = if (traitType.baseClasses.exists(_.fullName == "java.lang.AutoCloseable")) {
      if (channelVar.tpe.members.exists(m => m.isMethod && m.asMethod.name.toString == "shutdownNow")) {
        q"""{
              $channelVar.shutdownNow()
              ()
            }"""
      } else {
        c.abort(
          c.enclosingPosition,
          s"$traitType extends java.lang.AutoCloseable but the requirement could not be satisfied because given channel is of type ${channelVar.tpe.typeSymbol.fullName} which is not closeable (tip: try to use io.grpc.ManagedChannel instead)"
        )
      }
    } else q" () "

    c.Expr[MyTrait[F]] {
      val t =
        q"""
         new com.avast.cactus.grpc.client.ClientInterceptorsWrapper[$fType](scala.collection.immutable.Seq(..$interceptors)) with $traitType with _root_.java.lang.AutoCloseable {
            private val ex: java.util.concurrent.Executor = $ex

            override protected val F: _root_.cats.MonadError[$fType, _root_.java.lang.Throwable] = { $as }
            override protected val ec: scala.concurrent.ExecutionContext = { $ec }

            private def newStub = $stub

            import com.avast.cactus.grpc.client.{ClientCommonMethods => Methods}
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

    com.google.protobuf.Empty.getDefaultInstance

    implMethod.request match {
      case Some(implRequestType) =>
        q"""
          override def ${implMethod.name}(request: $implRequestType): $fReturnType = {
            super.withInterceptors { ctx =>
              val stub = newStub

              request.asGpb[${apiMethod.request}] match {
                case scala.util.Right(req) => Methods.executeRequest[$fType, ${apiMethod.request}, ${apiMethod.response}, ${implMethod.response}](req, ctx, stub.${apiMethod.name}, ex, ec)
                case scala.util.Left(errors) =>
                  F.pure {
                    Left {
                      com.avast.cactus.grpc.ServerError(
                        io.grpc.Status.INVALID_ARGUMENT
                          .withDescription(Methods.formatCactusFailures("request", errors))
                          .withCause(com.avast.cactus.CompositeFailure(errors.toList))
                      )
                    }
                  }
              }
            }
          }
     """

      case None =>
        q"""
          override def ${implMethod.name}(): $fReturnType = {
             super.withInterceptors { ctx =>
                val stub = newStub

                Methods.executeRequest[$fType, ${ apiMethod.request }, ${ apiMethod.response }, ${ implMethod.response }](com.google.protobuf.Empty.getDefaultInstance, ctx, stub.${ apiMethod.name }, ex, ec)
            }
          }
     """
    }
  }

  private def checkAbstractMethods(traitType: Type, fSymbol: TypeSymbol, implMethods: Set[ImplMethod]): Unit = {
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

      c.abort(
        c.enclosingPosition,
        s"Only gRPC methods are allowed to be abstract in type ${traitType.typeSymbol}[${fSymbol.name}], found others too: $foundIllegalMethods"
      )
    }
  }

  private def getApiMethods(traitType: Type): Seq[ApiMethod] = {
    traitType.decls.flatMap(ApiMethod.extract).toSeq
  }

  private def getMethodsMapping(traitType: Type,
                                fSymbol: TypeSymbol,
                                stubType: Type,
                                apiMethods: Seq[ApiMethod]): Map[ImplMethod, ApiMethod] = {
    val methods = traitType.decls.flatMap(ImplMethod.extract(_, fSymbol))

    if (CactusMacros.Debug) println(methods.mkString("Impl methods: [",",","]"))

    methods.map { m =>
      val apiMethod = apiMethods
        .find(_.name == m.name)
        .getOrElse {
          c.abort(
            c.enclosingPosition,
            s"Method ${m.name} of ${traitType.typeSymbol} does not have it's counterpart in ${stubType.typeSymbol}"
          )
        }

      m -> apiMethod
    }.toMap
  }

  private def isGpbClass(t: Type): Boolean = t.baseClasses.contains(typeOf[MessageLite].typeSymbol)

  private case class ImplMethod(name: TermName, request: Option[Type], response: Type)

  private object ImplMethod {
    def extract(s: Symbol, fSymbol: TypeSymbol): Option[ImplMethod] = {
      if (CactusMacros.Debug) {
        println(s"ImplMethod.extract($s)")
      }

      if (s.isMethod) {
        if (s.name.toString == "$init$") {
          c.abort(
            c.enclosingPosition,
            s"Trait ${s.owner.name} must not contain constructor method. It's presence can be caused e.g. by internal class etc."
          )
        }

        val m = s.asMethod

        if (m.paramLists.size == 1) {
          val reqType = m.paramLists.headOption
            .flatMap(_.headOption)
            .map(_.typeSignature)

          // TODO type matching
          val serverError = typeOf[ServerError].dealias.typeSymbol
          val either = typeOf[scala.util.Either[_, _]].dealias.typeConstructor

          for {
            f <- Some(m.returnType.dealias) if f.typeSymbol == fSymbol // F[_]
            e <- f.typeArgs.headOption if e.dealias.typeConstructor == either // F[Either[_,_]]
            ea <- Some(e.dealias.typeArgs) if ea.head.dealias.typeSymbol == serverError // F[Either[ServerError,_]]
          } yield {
            val respType = ea(1)

            if (!m.isAbstract)
              c.abort(
                c.enclosingPosition,
                s"Method ${m.name} of ${m.owner} has to be abstract to be able to implement"
              )

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
