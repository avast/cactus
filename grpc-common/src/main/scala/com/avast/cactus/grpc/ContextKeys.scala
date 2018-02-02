package com.avast.cactus.grpc

import io.grpc.Context
import io.grpc.Context.Key

import scala.collection.mutable
import scala.reflect.ClassTag

object ContextKeys {
  private val keys = mutable.Map.empty[String, Key[_]]

  def get[A: ClassTag](name: String): Key[A] = keys.synchronized {
    val ct = implicitly[ClassTag[A]]
    val finalName = toFinalName(name, ct)

    keys
      .getOrElse(finalName, {
        val newKey = Context.key[A](finalName)
        keys += finalName -> newKey
        newKey
      })
      .asInstanceOf[Key[A]] // this is needed :-(
  }

  private def toFinalName(name: String, ct: ClassTag[_]): String = {
    s"$name-${ct.runtimeClass.getName}"
  }
}
