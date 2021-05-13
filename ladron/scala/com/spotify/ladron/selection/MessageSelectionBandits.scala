package com.spotify.ladron.selection

import enumeratum._

import com.spotify.ladron.common.{AssignmentPolicy, PolicyType}

object MessageSelectionBandits {
  sealed trait BanditModelType extends EnumEntry with Serializable with Product
  object BanditModelType extends Enum[BanditModelType] {
    val values: scala.collection.immutable.IndexedSeq[BanditModelType] = findValues

    /**
     * Model requires no context (but there may be one context free bandit per channel/country).
     */
    case object ContextFree extends BanditModelType

    /**
     * Model requires context as tf example.
     */
    case object Contextual extends BanditModelType

    /**
     * Policy requires no model, eg random or holdout.
     */
    case object Null extends BanditModelType
  }

  def modelType(a: AssignmentPolicy): BanditModelType = (a.model, a.`type`) match {
    case (_, PolicyType.Holdout) => BanditModelType.Null
    case (_, PolicyType.Random)  => BanditModelType.Null
    case (Some(_), _)            => BanditModelType.Contextual
    case (_, PolicyType.EGreedy) => BanditModelType.ContextFree
  }

  sealed trait BanditPolicyType extends EnumEntry with Serializable with Product
  object BanditPolicyType extends Enum[BanditPolicyType] {
    val values: scala.collection.immutable.IndexedSeq[BanditPolicyType] = findValues

    /**
     * Always pick a random alternative.
     */
    case object Random extends BanditPolicyType

    /**
     * With chance epsilon pick a random alternative.
     * Epsilon should be configured in the optionsByPolicy.
     */
    case object EGreedy extends BanditPolicyType
  }

  def policyType(a: AssignmentPolicy): BanditPolicyType = a.`type` match {
    case PolicyType.Holdout => BanditPolicyType.Random
    case PolicyType.Random  => BanditPolicyType.Random
    case PolicyType.EGreedy => BanditPolicyType.EGreedy
  }
}
