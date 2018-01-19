package com.avast

import org.scalactic.Or

package object cactus {
  type CactusFailures = org.scalactic.Every[CactusFailure]
  type ResultOrErrors[A] = A Or CactusFailures
}
