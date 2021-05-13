package com.spotify.ladron.syntax
import com.spotify.scio.values.SCollection
import org.apache.beam.sdk.io.{Compression, FileBasedSink, TextIO}

package object textfile {
  implicit class CsvExtension(val self: SCollection[String]) {
    def saveAsSingleTextFile(
      path: String,
      header: Option[String] = None,
      suffix: String = ".csv",
      compression: Compression = Compression.UNCOMPRESSED
    ): Unit = {
      val dest = header match {
        case Some(h) =>
          TextIO
            .write()
            .to(path)
            .withoutSharding()
            .withSuffix(suffix)
            .withHeader(h)
            .withCompression(compression)
        case _ =>
          TextIO
            .write()
            .to(path)
            .withoutSharding()
            .withSuffix(suffix)
            .withCompression(compression)
      }

      self.internal.apply(dest)
    }
  }
}
