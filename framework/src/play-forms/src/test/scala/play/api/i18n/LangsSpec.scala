package play.api.i18n

import org.specs2.mutable._
import play.api.Configuration
import play.api.mvc.{ Cookies, Results }

class LangsSpec extends Specification {

  val config = Configuration.reference ++ Configuration.from(Map("play.i18n.langs" -> Seq("en", "fr", "fr-CH")))

  val langs = new DefaultLangsProvider(config).get

  "support setting the language on a result" in {
    val cookie = Cookies.decodeSetCookieHeader(langs.setLang(Results.Ok, Lang("en-AU")).header.headers("Set-Cookie")).head
    cookie.name must_== "PLAY_LANG"
    cookie.value must_== "en-AU"
  }

}
