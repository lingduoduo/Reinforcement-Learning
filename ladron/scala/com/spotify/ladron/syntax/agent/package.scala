package com.spotify.ladron.syntax

import com.spotify.ladron.Coders._
import com.spotify.ladron.syntax.json.DynamicJsonContextSyntax
import com.spotify.ladron.train.{Context, ContextFreeModelByContext}
import com.spotify.scio.ScioContext
import com.spotify.scio.extra.json._
import com.spotify.scio.values.SCollection

package object agent {
  implicit class BanditSyntax(self: SCollection[ContextFreeModelByContext]) {
    def saveBanditWithContext(path: String): Unit =
      self
        .keyBy(_.context)
        .saveAsDynamicJsonFile(path) { c: Context =>
          s"${c.channel.name}/${c.country}"
        }
  }

  implicit class BanditContextSyntax(private val sc: ScioContext) {
    def readBanditWithContext(path: String): SCollection[ContextFreeModelByContext] =
      sc.jsonFile(path)
  }
}
