package play.api.mvc

import play.api.i18n.{ Lang, MessagesApi }

trait LegacyI18nSupport {

  /**
   * Adds convenient methods to handle the client-side language.
   *
   * This class exists only for backward compatibility.
   */
  implicit class ResultWithLang(result: Result)(implicit messagesApi: MessagesApi) {

    /**
     * Sets the user's language permanently for future requests by storing it in a cookie.
     *
     * For example:
     * {{{
     * implicit val lang = Lang("fr-FR")
     * Ok(Messages("hello.world")).withLang(lang)
     * }}}
     *
     * @param lang the language to store for the user
     * @return the new result
     */
    def withLang(lang: Lang): Result =
      messagesApi.langs.setLang(result, lang)

    /**
     * Clears the user's language by discarding the language cookie set by withLang
     *
     * For example:
     * {{{
     * Ok(Messages("hello.world")).clearingLang
     * }}}
     *
     * @return the new result
     */
    def clearingLang: Result =
      messagesApi.langs.clearLang(result)

  }

}
