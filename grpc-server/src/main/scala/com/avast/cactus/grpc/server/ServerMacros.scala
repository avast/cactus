package com.avast.cactus.grpc.server

import com.avast.cactus.CactusMacros
import com.google.protobuf.MessageLite
import io.grpc.{BindableService, Status}

import scala.reflect.macros.whitebox

class ServerMacros(val c: whitebox.Context) {

  import c.universe._

  def mapImplToService[Service <: BindableService: WeakTypeTag](ct: Tree, ec: c.Tree): c.Expr[Service] = {
    // this method require `ec` as an argument but only to secure the EC will be present. If it's visible by the macro method, it has to be
    // visible also for the generated code thus it's ok to not use the argument

    val serviceType = weakTypeOf[Service]
    val implType = CactusMacros.extractSymbolFromClassTag(c)(ct)

    val variable = CactusMacros.getVariable(c)

    val apiMethods = getApiMethods(serviceType)
    val methodsMappings = getMethodsMapping(implType, apiMethods)

    val mappingMethods = methodsMappings.map { case (am, im) => generateMappingMethod(variable, am, im) }

    c.Expr[Service] {
        q"""
        new $serviceType {
          import com.avast.cactus.v3._
          import io.grpc._
          import com.avast.cactus.grpc.server.ServerCommonMethods._

          ..$mappingMethods
        }
        """
    }
  }

  private def generateMappingMethod(variable: Tree, apiMethod: ApiMethod, implMethod: ImplMethod): Tree = {
    q"""
      override def ${apiMethod.name}(request: ${apiMethod.request}, responseObserver: StreamObserver[${apiMethod.response}]): Unit = {
        (request.asCaseClass[MyRequest] match {
          case org.scalactic.Good(req) => executeRequest[${implMethod.request}, ${implMethod.response}, ${apiMethod.response}](req, $variable.${implMethod.name})
          case org.scalactic.Bad(errors) =>
            Future.successful {
              Left {
                new StatusException(Status.INVALID_ARGUMENT.withDescription(formatCactusFailures("request", errors)))
              }
            }
        }).recover(recoverWithStatus)
          .andThen(sendResponse(responseObserver))
      }
    """
  }

  private def getApiMethods(t: Type): Seq[ApiMethod] = {
    t.decls.collect {
      case ApiMethod(m) => m
    }
  }.toSeq

  private def getMethodsMapping(ot: Type, apiMethods: Seq[ApiMethod]): Map[ApiMethod, ImplMethod] = {
    val implMethods = ot.members.collect {
      case m if m.isMethod => m.asMethod
    }.toList

    apiMethods.map { apiMethod =>
      val implMethod = implMethods.filter(_.name == apiMethod.name) match {
        case List(m) => m
        case _ => c.abort(c.enclosingPosition, s"Method ${apiMethod.name} in type ${ot.typeSymbol} must have exactly one alternative")
      }

      apiMethod -> ImplMethod(implMethod)
    }.toMap
  }

  private def isGpbClass(t: Type): Boolean = t.baseClasses.contains(typeOf[MessageLite].typeSymbol)

  private case class ImplMethod(name: TermName, request: TypeSymbol, context: Option[TypeSymbol], response: TypeSymbol)

  private object ImplMethod {
    def apply(m: MethodSymbol): ImplMethod = {
      if (m.paramLists.size != 1) c.abort(c.enclosingPosition, s"Method ${m.name} in type ${m.owner} must have exactly one parameter list")

      val (reqType, ctxType) = m.paramLists.head.map(_.typeSignature) match {
        case List(req: Type) => (req.typeSymbol.asType, None)
        case List(req: Type, ctx: Type) => (req.typeSymbol.asType, Some(ctx.typeSymbol.asType))
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"Method ${m.name} in type ${m.owner} must have only one or two parameters (request and optionally context)"
          )
      }

      // TODO type matching
      val status = typeOf[Status].dealias
      val future = typeOf[scala.concurrent.Future[_]].dealias.typeConstructor
      val either = typeOf[scala.util.Either[_, _]].dealias.typeConstructor

      val respType = (for {
        f <- Some(m.returnType.dealias) if f.dealias.typeConstructor == future // Future[_]
        e <- f.typeArgs.headOption if e.dealias.typeConstructor == either // Future[Either[_,_]]
        ea <- Some(e.typeArgs) if ea.head.dealias == status // Future[Either[Status,_]]
      } yield ea(1))
        .getOrElse(
          c.abort(c.enclosingPosition, s"Method ${m.name} in type ${m.owner} does not have required result type Future[Either[Status, ?]]"))
        .typeSymbol
        .asType

      new ImplMethod(m.name, reqType, ctxType, respType)
    }
  }

  private case class ApiMethod(name: TermName, request: TypeSymbol, response: TypeSymbol)

  private object ApiMethod {
    def unapply(s: Symbol): Option[ApiMethod] = {
      Option(s)
        .collect {
          case m
              if m.isMethod
                && m.name.toString != "bindService"
                && m.asMethod.paramLists.size == 1
                && m.asMethod.returnType == typeOf[Unit] =>
            m.asMethod.name -> m.asMethod.paramLists.head.map(_.typeSignature.resultType)
        }
        .collect {
          case (name, List(req: Type, respObs: Type)) if isGpbClass(req) && respObs.typeArgs.forall(isGpbClass) =>
            ApiMethod(name, req.typeSymbol.asType, respObs.typeArgs.head.typeSymbol.asType)
        }
    }
  }

}
