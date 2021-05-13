package com.spotify.ladron.syntax

import scala.reflect.ClassTag

import com.twitter.chill.ClosureCleaner
import io.circe.Printer
import io.circe.syntax._
import org.apache.beam.sdk.coders.StringUtf8Coder
import org.apache.beam.sdk.io.{Compression, FileIO}
import org.apache.beam.sdk.transforms.{Contextful, SerializableFunction}
import org.apache.beam.sdk.{io => beam}

import com.spotify.scio.coders.Coder
import com.spotify.scio.extra.json.Encoder
import com.spotify.scio.values.SCollection

package object json {
  implicit class DynamicJsonContextSyntax[C: ClassTag: Coder, T: ClassTag: Coder](
    private val self: SCollection[(C, T)]
  ) {

    /**
     * Save this SCollection as json files specified by the destination function.
     */
    def saveAsDynamicJsonFile(
      path: String,
      suffix: String = ".json",
      numShards: Int = 0,
      compression: Compression = Compression.UNCOMPRESSED,
      printer: Printer = Printer.noSpaces
    )(destinationFn: C => String)(implicit encoder: Encoder[T]): Unit = {
      if (self.context.isTest) {
        throw new NotImplementedError(
          "Text file with dynamic destinations cannot be used in a test context"
        )
      } else {
        val toJsonFn: ((C, T)) => String = x => printer.print(x._2.asJson)
        val destFn: ((C, T)) => String = x => destinationFn(x._1)
        val write = writeDynamic(path, numShards, suffix, destFn)
          .withCompression(compression)
          .via(Contextful.fn(serializableFn(toJsonFn)), beam.TextIO.sink())
        self.internal.apply(write)
      }
    }
  }

  private def writeDynamic[A](
    path: String,
    numShards: Int,
    suffix: String,
    destinationFn: A => String
  ): FileIO.Write[String, A] = {
    FileIO
      .writeDynamic[String, A]()
      .to(path)
      .withNumShards(numShards)
      .by(serializableFn(destinationFn))
      .withDestinationCoder(StringUtf8Coder.of())
      .withNaming(serializableFn { destination: String =>
        FileIO.Write.defaultNaming(s"$destination/part", suffix)
      })
  }

  private def serializableFn[T, U](f: T => U): SerializableFunction[T, U] = {
    val g = f
    ClosureCleaner(g)

    new SerializableFunction[T, U] {
      override def apply(input: T): U =
        g(input)
    }
  }
}
