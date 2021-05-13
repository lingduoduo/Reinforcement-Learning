package com.spotify.ladron.assignment

import com.google.common.hash.Hashing

import com.spotify.bcd.schemas.scala.UserId

object Assigner {
  /*
   * Assigning a candidate given bucket assignment
   * */
  def assignCandidate[T, H: Hasher](
    userId: UserId,
    test: AssignmentBucket[T],
    hashFn: H = Hashing.sha256()
  ): T = assign(userId, assign(userId, test))

  /**
   * Assign user to a cell in an assignment.
   *
   * Copied from ghe:remote-configuration-service:WeightedSampler with the following alterations
   * - uses sha256 since md5 is deprecated
   */
  def assign[T, H: Hasher](
    userId: UserId,
    test: Assignment[T],
    hashFn: H = Hashing.sha256()
  ): T = {
    val hash = computeHash(test.salt, userId, hashFn)
    val assignment = hash % test.weightedSum

    computeCell(test, assignment)
  }

  /**
   * Return the hash of this salt userId as a positive number.
   * NB Positive to make it easy to compute the assignment using mod.
   */
  private[this] def computeHash[H: Hasher](
    salt: String,
    userId: UserId,
    hashFn: H
  ): Long = {
    val hash: Long = implicitly[Hasher[H]].hash(hashFn, salt, userId)
    math.abs(hash)
  }

  private[this] def computeCell[T](
    test: Assignment[T],
    assignment: Long
  ): T = {
    def inside(leftWeight: Long, c: T): Boolean =
      assignment < leftWeight + test.cellWeights(c).value
    def fail(): T =
      throw new RuntimeException("Logic error, failed to compute group")
    @annotation.tailrec
    def loop(leftWeight: Long, cells: Seq[T]): T = cells match {
      case c +: _ if inside(leftWeight, c) => c
      case c +: cs                         => loop(leftWeight + test.cellWeights(c).value, cs)
      case _                               => fail()
    }
    loop(0, test.cellWeights.keys.toSeq)
  }
}
