package com.spotify.ladron.config.assignment

import com.spotify.ladron.NonNegativeInt

object CommonAssignment {
  // scalastyle:off magic.number
  val UnifiedExploitSize: NonNegativeInt = NonNegativeInt.unsafeFromInt(333)
  val UnifiedExploitContextFreeSize: NonNegativeInt = NonNegativeInt.unsafeFromInt(200)
  val UnifiedExploitContextualSize: NonNegativeInt = NonNegativeInt.unsafeFromInt(133)
  val UnifiedExploreSize: NonNegativeInt = NonNegativeInt.unsafeFromInt(333)
  val UnifiedControlSize: NonNegativeInt = NonNegativeInt.unsafeFromInt(334)
  // scalastyle:on magic.number
}
