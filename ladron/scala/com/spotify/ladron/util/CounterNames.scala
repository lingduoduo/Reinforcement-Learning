package com.spotify.ladron.util

import com.google.common.base.CaseFormat
import org.apache.beam.sdk.metrics.MetricName

object CounterNames {

  /**
   * Create a normalized counter name using prefix and name.
   * Converts name from camel case to lower underscore format.
   */
  def name(namespace: String, prefix: String, name: String): String =
    s"${namespace}_${prefix}_${camelToSnakeCase(name)}"

  def name(namespace: String, prefix: String, name: String, postfix: String): String =
    s"${namespace}_${prefix}_${camelToSnakeCase(name)}_$postfix"

  def metricName(namespace: String, prefix: String, name: String): MetricName =
    MetricName.named(namespace, this.name(namespace, prefix, name))

  def metricName(namespace: String, prefix: String, name: String, postfix: String): MetricName =
    MetricName.named(namespace, this.name(namespace, prefix, name, postfix))

  private def camelToSnakeCase(s: String): String =
    CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, s)
}
