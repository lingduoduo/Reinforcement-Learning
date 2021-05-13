package com.spotify.ladron.train

import com.spotify.ladron.agent.model.ContextFreeModel
import com.spotify.ladron.common.MessageChannel

/**
 * Note: There are some cases when adding a new field can break compatibility.
 * When adding a new field, previous bandits need to be recreated.
 */
final case class Context(channel: MessageChannel, country: String)
object Context {
  val Global = "Global"
}

/**
 * To be backwards compatible with the JSON format the model parameter must be called bandit.
 */
final case class ContextFreeModelByContext(context: Context, bandit: ContextFreeModel)
