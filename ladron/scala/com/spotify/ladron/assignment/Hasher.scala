package com.spotify.ladron.assignment

import com.google.common.base.Charsets
import com.google.common.hash.HashFunction
import com.spotify.bcd.schemas.scala.UserId

/**
 * Hash salt, userId using the provided hash function.
 */
trait Hasher[T] extends Serializable {
  def hash(hasher: T, salt: String, userId: UserId): Long
}

object Hasher {
  implicit val Guava = new Hasher[HashFunction] {
    override def hash(
      hashFn: HashFunction,
      salt: String,
      userId: UserId
    ): Long = {
      val hasher = hashFn.newHasher()
      hasher.putString(salt, Charsets.UTF_8)
      hasher.putBytes(userId.bytes.toArray)
      hasher.hash().asLong()
    }
  }
}
