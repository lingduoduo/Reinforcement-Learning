package com.spotify.ladron

import com.spotify.scio.coders.Coder
import com.spotify.scio.values.SCollection

package object syntax {
  implicit class KeySyntax[A](private val self: SCollection[A]) extends AnyVal {
    def keyByOpt[K](
      f: A => Option[K]
    )(implicit coderA: Coder[A], coderK: Coder[K]): SCollection[(K, A)] =
      self.flatMap { x =>
        for {
          key <- f(x)
        } yield key -> x
      }
  }
}
