package com.spotify.ladron.tf

import com.google.protobuf.ByteString
import com.spotify.bcd.schemas.scala.NonEmptyString
import com.spotify.ladron.common.MessageChannel
import magnolify.tensorflow.ExampleField

object Encoding {
  implicit val channelField: ExampleField.Primitive[MessageChannel] = ExampleField
    .from[ByteString](b => MessageChannel.withNameInsensitiveOption(b.toStringUtf8).get)(mc =>
      ByteString.copyFromUtf8(mc.name)
    )

  implicit val nonEmptyStringField: ExampleField.Primitive[NonEmptyString] = ExampleField
    .from[ByteString](b => NonEmptyString.parse(b.toStringUtf8).get)(cf =>
      ByteString.copyFromUtf8(cf.value)
    )
}
