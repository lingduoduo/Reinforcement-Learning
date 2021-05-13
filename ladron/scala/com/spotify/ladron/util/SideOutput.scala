package com.spotify.ladron.util

import com.spotify.scio.coders.Coder
import com.spotify.scio.values.SCollection
import com.spotify.scio.avro._
import com.spotify.scio.tensorflow._
import org.apache.avro.specific.SpecificRecordBase
import org.tensorflow.example.Example

import scala.reflect.ClassTag

trait SideOutput {

  /**
   * Convert the input type to avro and then process save it on the side.
   */
  def avroFile[T: Coder, A <: SpecificRecordBase: Coder: ClassTag](
    f: T => A
  )(path: String)(ts: SCollection[T]): SCollection[T]

  def exampleFile[T: Coder](f: T => Example)(path: String)(ts: SCollection[T]): SCollection[T]
}

/**
 * Util methods that allow processing avro views on the side, ie for logging intermediate results.
 */
object SideOutput {

  /**
   * Method used to implement avro side functions.
   */
  def consumeAvroSide[T: Coder, A <: SpecificRecordBase: Coder: ClassTag](
    f: T => A
  )(sink: SCollection[A] => Unit)(ts: SCollection[T]): SCollection[T] = {
    sink(ts.map(f))
    ts
  }

  def consumeExampleSide[T: Coder](
    f: T => Example
  )(sink: SCollection[Example] => Unit)(ts: SCollection[T]): SCollection[T] = {
    sink(ts.map(f))
    ts
  }

  /**
   * Drop any entries.
   */
  val Drop: SideOutput = new SideOutput {
    def avroFile[T: Coder, A <: SpecificRecordBase: Coder: ClassTag](
      f: T => A
    )(path: String)(ts: SCollection[T]): SCollection[T] = consumeAvroSide(f)(_ => {})(ts)

    def exampleFile[T: Coder](f: T => Example)(path: String)(ts: SCollection[T]): SCollection[T] =
      consumeExampleSide(f)(_ => {})(ts)
  }

  /**
   * Save all entries to avro files.
   */
  val Save: SideOutput = new SideOutput {
    def avroFile[T: Coder, A <: SpecificRecordBase: Coder: ClassTag](
      f: T => A
    )(path: String)(ts: SCollection[T]): SCollection[T] =
      consumeAvroSide(f) {
        _.withName("WriteSideOutput").saveAsAvroFile(path)
      }(ts)

    def exampleFile[T: Coder](f: T => Example)(path: String)(ts: SCollection[T]): SCollection[T] =
      consumeExampleSide(f) { _.withName("WriteSideOutput").saveAsTfRecordFile(path) }(ts)
  }
}
