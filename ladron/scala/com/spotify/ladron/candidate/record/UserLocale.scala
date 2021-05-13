package com.spotify.ladron.candidate.record

import java.util.Locale

import com.spotify.bcd.schemas.scala.UserId
import com.spotify.userdata.schema.UserdataSourceBroad

final case class UserLocale(
  userId: UserId,
  preferredLocale: Locale
) {

  /**
   * Deprecation ref, "Legacy language codes" section:
   *  https://docs.oracle.com/javase/7/docs/api/java/util/Locale.html
   *
   * @return normalized ISO-compatible language code
   */
  def languageCode: String = {
    preferredLocale.getLanguage match {
      case "iw"  => "he"
      case "ji"  => "yi"
      case "in"  => "id"
      case l @ _ => l
    }
  }
}

object UserLocale {
  def fromUserdataSourceBroad(userdataSourceBroad: UserdataSourceBroad): Option[UserLocale] = {
    for {
      userId <- UserId.fromString(userdataSourceBroad.getUserId)
      preferredLocale <- Option(userdataSourceBroad.getPreferredLocale).map(_.toString)
      locale <- parseLocale(preferredLocale)
    } yield UserLocale(userId, locale)
  }

  /**
   * @return parsed locale normalized in case of incompatible devices
   */
  private def parseLocale(locale: String): Option[Locale] = {
    // `en_US` -> `en-US`
    val languageTag = locale.replace('_', '-')
    val parsedLocale = Locale.forLanguageTag(languageTag)
    if (parsedLocale.getLanguage.isEmpty) {
      None
    } else {
      Some(parsedLocale)
    }
  }
}
