package com.avast.cactus.grpc

import com.avast.cactus.CactusFailures

private[grpc] trait CommonMethods {
  def formatCactusFailures(subject: String, errors: CactusFailures): String = {
    s"Errors when converting $subject: ${errors.toList.mkString("[", ", ", "]")}"
  }
}
