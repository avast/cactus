package com.avast.cactus.grpc.server

import com.avast.cactus.CactusMacros
import com.google.protobuf.MessageLite
import io.grpc.{BindableService, Status}

import scala.language.higherKinds
import scala.reflect.macros.whitebox

class ServerMacros(val c: whitebox.Context) {

  import c.universe._

  def mapImplToService[Service <: BindableService: WeakTypeTag, F[_]](
      interceptors: c.Tree*)(ct: Tree, sch: c.Tree): c.Expr[MappedGrpcService[Service]] = {
    // this method require `sch` as an argument but only to secure the Scheduler will be present. If it's visible by the macro method, it
    // has to be visible also for the generated code thus it's ok to not use the argument

    val serviceType = weakTypeOf[Service]
    val implType = CactusMacros.extractSymbolFromClassTag(c)(ct)

    // fType cannot be get with weakTypeOf => https://issues.scala-lang.org/browse/SI-8919
    val fType = implType
      .baseType(implType.baseClasses.find(_.fullName == classOf[GrpcService[F]].getName).getOrElse {
        c.abort(c.enclosingPosition, s"The $implType does not extend GrpcService[F]")
      })
      .typeArgs
      .headOption
      .getOrElse {
        c.abort(c.enclosingPosition, s"Unable to extract F from GrpcService[F] - please report a bug")
      }

    if (CactusMacros.Debug) {
      println(s"Mapping $serviceType to $implType (F = $fType)")
    }

    val variable = CactusMacros.getVariable(c)

    val apiMethods = getApiMethods(serviceType)
    val methodsMappings = getMethodsMapping(fType, implType, apiMethods)

    val mappingMethods = methodsMappings.map { case (am, im) => generateMappingMethod(fType, am, im) }

    c.Expr[MappedGrpcService[Service]] {
      val t =
        q"""
        val service = new $serviceType {
          import com.avast.cactus.v3._
          import io.grpc._
          import com.avast.cactus.grpc.server.ServerCommonMethods._
          import scala.concurrent.Future

          private val wrapped = $variable

          private val interceptorsWrapper = new ServerInterceptorsWrapper(scala.collection.immutable.List(..$interceptors))

          ..$mappingMethods
        }

        new DefaultMappedGrpcService[$serviceType](service, com.avast.cactus.grpc.server.ServerMetadataInterceptor)

        """

      if (CactusMacros.Debug) println(t)

      t
    }
  }

  private def generateMappingMethod(fType: Type, apiMethod: ApiMethod, implMethod: ImplMethod): Tree = {

    val convertRequest = if (apiMethod.request =:= implMethod.request) {
      q" scala.util.Right(request) "
    } else {
      q" request.asCaseClass[${implMethod.request}] "
    }

    val convertResponse = if (apiMethod.response =:= implMethod.response) {
      q" Right.apply "
    } else {
      q" convertResponse[${implMethod.response}, ${apiMethod.response}] "
    }

    val executeRequestMethod = q" executeRequest[$fType, ${implMethod.request}, ${implMethod.response}, ${apiMethod.response}] "

    val (createCtxMethod: Tree, executeMethod: Tree) = implMethod.context match {
      case Some(ctxType) =>
        val createCtxMethod =
          q"""
            def createCtx: scala.util.Try[$ctxType] = {
              ${metadataToContextInstance(ctxType)}
                .map(scala.util.Success(_))
                .getOrElse {
                  scala.util.Failure(new IllegalArgumentException("Missing headers for creation of the context"))
                }
            }
           """

        (createCtxMethod,
         q"""
            withContext[${implMethod.request}, ${apiMethod.response}, $ctxType](createCtx) { ctx =>
              $executeRequestMethod(req, wrapped.${implMethod.name}(_, ctx), $convertResponse)
            }
           """)

      case _ => (q"", q" $executeRequestMethod(req, wrapped.${implMethod.name}(_), $convertResponse) ")
    }

    q"""
      override def ${apiMethod.name}(request: ${apiMethod.request}, responseObserver: io.grpc.stub.StreamObserver[${apiMethod.response}]): Unit = {

        $createCtxMethod

        ($convertRequest match {
          case scala.util.Right(req) => interceptorsWrapper.withInterceptors { $executeMethod }
          case scala.util.Left(errors) =>
            Future.successful {
              Left {
                new StatusException(Status.INVALID_ARGUMENT.withDescription(formatCactusFailures("request", errors)))
              }
            }
        }).recover(recoverWithStatus)
          .andThen(sendResponse(responseObserver))

        ()
      }
    """
  }

  private def getApiMethods(t: Type): Seq[ApiMethod] = {
    t.decls.flatMap(ApiMethod.extract).toSeq
  }

  private val standardJavaMethods: Set[MethodSymbol] = {
    typeOf[Object].members.collect {
      case m if m.isMethod => m.asMethod
    }.toSet
  }

  private def getMethodsMapping(fType: Type, implType: Type, apiMethods: Seq[ApiMethod]): Map[ApiMethod, ImplMethod] = {
    val implMethods = implType.members
      .collect {
        case m if m.isMethod => m.asMethod
      }
      .toList
      .filterNot(standardJavaMethods.contains) // filter out standard Java methods - wait, notify, ...

    apiMethods.map { apiMethod =>
      val implMethod = implMethods.filter(_.name == apiMethod.name) match {
        case List(m) => m
        case l =>
          c.info(c.enclosingPosition, "Found methods:\n" + l.map(CactusMacros.methodToString(c)).mkString("- ", "\n- ", ""), force = true)
          c.abort(
            c.enclosingPosition,
            s"Method ${apiMethod.name} in type ${implType.typeSymbol} must have exactly one alternative, found ${l.size}"
          )
      }

      apiMethod -> ImplMethod.extract(implMethod, fType)
    }.toMap
  }

  private def metadataToContextInstance(ctxType: Type): Tree = {
    val fields = toCaseClassFields(ctxType.typeSymbol)

    if (fields.isEmpty) {
      c.abort(c.enclosingPosition, s"Context case class $ctxType must have some fields")
    }

    val queries = fields.map { f =>
      val t = f.typeSignature.finalResultType
      fq" ${f.name.toTermName} <- Option(com.avast.cactus.grpc.ContextKeys.get[$t](${f.name.toString}).get(ctx)) "
    }

    q"""
       val ctx = Context.current()

       for (..$queries) yield {
         new $ctxType(..${fields.map(_.name.toTermName)})
       }
     """
  }

  private def toCaseClassFields(t: Symbol): List[Symbol] = {
    val cl = if (t.isClass) {
      val cl = t.asClass
      if (cl.isCaseClass) cl else c.abort(c.enclosingPosition, s"$t must be a case class")
    } else c.abort(c.enclosingPosition, s"$t must be a case class")

    val ctor = cl.typeSignature.decls
      .collectFirst {
        case m if m.isMethod && m.asMethod.isPrimaryConstructor => m.asMethod
      }
      .getOrElse(c.abort(c.enclosingPosition, s"Type $t must have a primary ctor"))

    ctor.paramLists.flatten
  }

  private def isGpbClass(t: Type): Boolean = t.baseClasses.contains(typeOf[MessageLite].typeSymbol)

  private case class ImplMethod(name: TermName, request: Type, context: Option[Type], response: Type)

  private object ImplMethod {
    def extract(m: MethodSymbol, fType: Type): ImplMethod = {
      if (m.paramLists.size != 1) c.abort(c.enclosingPosition, s"Method ${m.name} in type ${m.owner} must have exactly one parameter list")

      val (reqType, ctxType) = m.paramLists.head.map(_.typeSignature) match {
        case List(req: Type) => (req, None)
        case List(req: Type, ctx: Type) => (req, Some(ctx))
        case _ =>
          c.abort(
            c.enclosingPosition,
            s"Method ${m.name} in type ${m.owner} must have only one or two parameters (request and optionally context)"
          )
      }

      // TODO type matching
      val status = typeOf[Status].dealias
      val theF = fType.typeConstructor
      val either = typeOf[scala.util.Either[_, _]].dealias.typeConstructor

      val respType = (for {
        f <- Some(m.returnType.dealias) if f.dealias.typeConstructor == theF // F[_]
        e <- f.typeArgs.headOption if e.dealias.typeConstructor == either // F[Either[_,_]]
        ea <- Some(e.typeArgs) if ea.head.dealias == status // F[Either[Status,_]]
      } yield ea(1))
        .getOrElse(
          c.abort(c.enclosingPosition, s"Method ${m.name} in type ${m.owner} does not have required result type $fType[Either[Status, ?]]"))

      new ImplMethod(m.name, reqType, ctxType, respType)
    }
  }

  private case class ApiMethod(name: TermName, request: Type, response: Type)

  private object ApiMethod {
    def extract(s: Symbol): Option[ApiMethod] = {
      Option(s)
        .collect {
          case m
              if m.isMethod
                && m.name.toString != "bindService"
                && m.asMethod.paramLists.size == 1
                && m.asMethod.paramLists.head.size == 2
                && m.asMethod.returnType == typeOf[Unit] =>
            m.asMethod.name -> m.asMethod.paramLists.head.map(_.typeSignature.resultType)
        }
        .collect {
          case (name, List(req: Type, respObs: Type)) if isGpbClass(req) && respObs.typeArgs.forall(isGpbClass) =>
            ApiMethod(name, req, respObs.typeArgs.head)
        }
    }
  }

}
