/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.i18n

import java.net.URL
import java.util.Locale
import javax.inject.{ Inject, Provider, Singleton }

import play.api._
import play.api.inject.Module
import play.api.mvc._
import play.mvc.Http
import play.utils.{ PlayIO, Resources }

import scala.collection.JavaConverters._
import scala.io.Codec
import scala.language.postfixOps
import scala.util.Try
import scala.util.control.NonFatal
import scala.util.parsing.combinator._
import scala.util.parsing.input._

/**
 * Internationalisation API.
 *
 * For example:
 * {{{
 * val msgString = Messages("items.found", items.size)
 * }}}
 */
object Messages {

  private[play] val messagesApiCache = Application.instanceCache[MessagesApi]

  /**
   * Implicit conversions providing [[Messages]] or [[MessagesApi]] using an implicit [[Application]], for a smooth upgrade to 2.4
   */
  object Implicits {
    import scala.language.implicitConversions
    implicit def applicationMessagesApi(implicit application: Application): MessagesApi =
      messagesApiCache(application)
    implicit def applicationMessages(implicit lang: Lang, application: Application): Messages =
      new Messages(lang, messagesApiCache(application))
  }

  /**
   * Translates a message.
   *
   * Uses `java.text.MessageFormat` internally to format the message.
   *
   * @param key the message key
   * @param args the message arguments
   * @return the formatted message or a default rendering if the key wasn’t defined
   */
  def apply(key: String, args: Any*)(implicit messages: Messages): String = {
    messages(key, args: _*)
  }

  /**
   * Translates the first defined message.
   *
   * Uses `java.text.MessageFormat` internally to format the message.
   *
   * @param keys the message key
   * @param args the message arguments
   * @return the formatted message or a default rendering if the key wasn’t defined
   */
  def apply(keys: Seq[String], args: Any*)(implicit messages: Messages): String = {
    messages(keys, args: _*)
  }

  /**
   * Check if a message key is defined.
   * @param key the message key
   * @return a boolean
   */
  def isDefinedAt(key: String)(implicit messages: Messages): Boolean = {
    messages.isDefinedAt(key)
  }

  /**
   * Parse all messages of a given input.
   */
  def parse(messageSource: MessageSource, messageSourceName: String): Either[PlayException.ExceptionSource, Map[String, String]] = {
    new Messages.MessagesParser(messageSource, "").parse.right.map { messages =>
      messages.map { message => message.key -> message.pattern }.toMap
    }
  }

  /**
   * A source for messages
   */
  trait MessageSource {
    /**
     * Read the message source as a String
     */
    def read: String
  }

  case class UrlMessageSource(url: URL) extends MessageSource {
    def read = PlayIO.readUrlAsString(url)(Codec.UTF8)
  }

  private[i18n] case class Message(key: String, pattern: String, source: MessageSource, sourceName: String) extends Positional

  /**
   * Message file Parser.
   */
  private[i18n] class MessagesParser(messageSource: MessageSource, messageSourceName: String) extends RegexParsers {

    case class Comment(msg: String)

    override def skipWhitespace = false
    override val whiteSpace = """^[ \t]+""".r

    def namedError[A](p: Parser[A], msg: String) = Parser[A] { i =>
      p(i) match {
        case Failure(_, in) => Failure(msg, in)
        case o => o
      }
    }

    val end = """^\s*""".r
    val newLine = namedError((("\r"?) ~> "\n"), "End of line expected")
    val ignoreWhiteSpace = opt(whiteSpace)
    val blankLine = ignoreWhiteSpace <~ newLine ^^ { case _ => Comment("") }

    val comment = """^#.*""".r ^^ { case s => Comment(s) }

    val messageKey = namedError("""^[a-zA-Z0-9_.-]+""".r, "Message key expected")

    val messagePattern = namedError(
      rep(
        ("""\""" ^^ (_ => "")) ~> ( // Ignore the leading \
          ("\r"?) ~> "\n" ^^ (_ => "") | // Ignore escaped end of lines \
          "n" ^^ (_ => "\n") | // Translate literal \n to real newline
          """\""" | // Handle escaped \\
          "^.".r ^^ ("""\""" + _)
        ) |
          "^.".r // Or any character
      ) ^^ { case chars => chars.mkString },
      "Message pattern expected"
    )

    val message = ignoreWhiteSpace ~ messageKey ~ (ignoreWhiteSpace ~ "=" ~ ignoreWhiteSpace) ~ messagePattern ^^ {
      case (_ ~ k ~ _ ~ v) => Messages.Message(k, v.trim, messageSource, messageSourceName)
    }

    val sentence = (comment | positioned(message)) <~ newLine

    val parser = phrase(((sentence | blankLine).*) <~ end) ^^ {
      case messages => messages.collect {
        case m @ Messages.Message(_, _, _, _) => m
      }
    }

    def parse: Either[PlayException.ExceptionSource, Seq[Message]] = {
      parser(new CharSequenceReader(messageSource.read + "\n")) match {
        case Success(messages, _) => Right(messages)
        case NoSuccess(message, in) => Left(
          new PlayException.ExceptionSource("Configuration error", message) {
            def line = in.pos.line
            def position = in.pos.column - 1
            def input = messageSource.read
            def sourceName = messageSourceName
          }
        )
      }
    }

  }

}

/**
 * Provides messages for a particular language.
 *
 * This intended for use to carry both the messages and the current language, particularly useful in templates so that
 * both can be captured by one parameter.
 *
 * @param lang The lang (context)
 * @param messages The messages
 */
case class Messages(lang: Lang, messages: MessagesApi) {

  /**
   * Translates a message.
   *
   * Uses `java.text.MessageFormat` internally to format the message.
   *
   * @param key the message key
   * @param args the message arguments
   * @return the formatted message or a default rendering if the key wasn’t defined
   */
  def apply(key: String, args: Any*): String = messages(key, args: _*)(lang)

  /**
   * Translates the first defined message.
   *
   * Uses `java.text.MessageFormat` internally to format the message.
   *
   * @param keys the message key
   * @param args the message arguments
   * @return the formatted message or a default rendering if the key wasn’t defined
   */
  def apply(keys: Seq[String], args: Any*): String = messages(keys, args: _*)(lang)

  /**
   * Translates a message.
   *
   * Uses `java.text.MessageFormat` internally to format the message.
   *
   * @param key the message key
   * @param args the message arguments
   * @return the formatted message, if this key was defined
   */
  def translate(key: String, args: Seq[Any]): Option[String] = messages.translate(key, args)(lang)

  /**
   * Check if a message key is defined.
   * @param key the message key
   * @return a boolean
   */
  def isDefinedAt(key: String): Boolean = messages.isDefinedAt(key)(lang)
}

/**
 * The internationalisation API.
 */
trait MessagesApi {

  /**
   * Get all the defined messages
   */
  def messages: Map[String, Map[String, String]]

  def langs: Langs

  /**
   * Get the preferred messages for the given candidates.
   *
   * Will select a language from the candidates, based on the languages available, and fallback to the default language
   * if none of the candidates are available.
   */
  def preferred(candidates: Seq[Lang]): Messages

  /**
   * Get the preferred messages for the given request
   */
  def preferred(request: RequestHeader): Messages

  /**
   * Get the preferred messages for the given Java request
   */
  def preferred(request: play.mvc.Http.RequestHeader): Messages

  /**
   * Translates a message.
   *
   * Uses `java.text.MessageFormat` internally to format the message.
   *
   * @param key the message key
   * @param args the message arguments
   * @return the formatted message or a default rendering if the key wasn’t defined
   */
  def apply(key: String, args: Any*)(implicit lang: Lang): String

  /**
   * Translates the first defined message.
   *
   * Uses `java.text.MessageFormat` internally to format the message.
   *
   * @param keys the message key
   * @param args the message arguments
   * @return the formatted message or a default rendering if the key wasn’t defined
   */
  def apply(keys: Seq[String], args: Any*)(implicit lang: Lang): String

  /**
   * Translates a message.
   *
   * Uses `java.text.MessageFormat` internally to format the message.
   *
   * @param key the message key
   * @param args the message arguments
   * @return the formatted message, if this key was defined
   */
  def translate(key: String, args: Seq[Any])(implicit lang: Lang): Option[String]

  /**
   * Check if a message key is defined.
   * @param key the message key
   * @return a boolean
   */
  def isDefinedAt(key: String)(implicit lang: Lang): Boolean
}

@Singleton
class DefaultMessagesApiProvider @Inject() (environment: Environment, config: Configuration, langs: Langs) extends Provider[MessagesApi] {

  def messagesPrefix = config.getDeprecated[Option[String]]("play.i18n.path", "messages.path")

  def loadMessages(file: String): Map[String, String] = {
    import scala.collection.JavaConverters._

    environment.classLoader.getResources(joinPaths(messagesPrefix, file)).asScala.toList
      .filterNot(url => Resources.isDirectory(environment.classLoader, url)).reverse
      .map { messageFile =>
        Messages.parse(Messages.UrlMessageSource(messageFile), messageFile.toString).fold(e => throw e, identity)
      }.foldLeft(Map.empty[String, String]) { _ ++ _ }
  }

  def loadAllMessages: Map[String, Map[String, String]] = {
    langs.availables.map(_.code).map { lang =>
      (lang, loadMessages("messages." + lang))
    }.toMap
      .+("default" -> loadMessages("messages"))
      .+("default.play" -> loadMessages("messages.default"))
  }

  def joinPaths(first: Option[String], second: String) = first match {
    case Some(parent) => new java.io.File(parent, second).getPath
    case None => second
  }

  override lazy val get: MessagesApi = {
    new DefaultMessagesApi(
      loadAllMessages,
      langs
    )
  }

}

/**
 * The internationalisation API.
 */
@Singleton
class DefaultMessagesApi @Inject() (
    val messages: Map[String, Map[String, String]] = Map.empty,
    val langs: Langs = new DefaultLangs()) extends MessagesApi {

  import java.text._

  def preferred(candidates: Seq[Lang]) = Messages(langs.preferred(candidates), this)

  def preferred(request: RequestHeader) = {
    val maybeLangFromCookie = request.cookies.get(langs.langCookieName).flatMap(c => Lang.get(c.value))
    val lang = langs.preferred(maybeLangFromCookie.toSeq ++ request.acceptLanguages.map(Lang(_)))
    Messages(lang, this)
  }

  def preferred(request: Http.RequestHeader) = preferred(request._underlyingHeader())

  def apply(key: String, args: Any*)(implicit lang: Lang): String = {
    translate(key, args).getOrElse(noMatch(key, args))
  }

  def apply(keys: Seq[String], args: Any*)(implicit lang: Lang): String = {
    keys.foldLeft[Option[String]](None) {
      case (None, key) => translate(key, args)
      case (acc, _) => acc
    }.getOrElse(noMatch(keys.last, args))
  }

  protected def noMatch(key: String, args: Seq[Any])(implicit lang: Lang) = key

  def translate(key: String, args: Seq[Any])(implicit lang: Lang): Option[String] = {
    val codesToTry = Seq(lang.code, lang.language, "default", "default.play")
    val pattern: Option[String] =
      codesToTry.foldLeft[Option[String]](None)((res, lang) =>
        res.orElse(messages.get(lang).flatMap(_.get(key))))
    pattern.map(pattern =>
      new MessageFormat(pattern, lang.toLocale).format(args.map(_.asInstanceOf[java.lang.Object]).toArray))
  }

  def isDefinedAt(key: String)(implicit lang: Lang): Boolean = {
    val codesToTry = Seq(lang.code, lang.language, "default", "default.play")

    codesToTry.foldLeft[Boolean](false)({ (acc, lang) =>
      acc || messages.get(lang).exists(_.isDefinedAt(key))
    })
  }
}

class I18nModule extends Module {
  def bindings(environment: Environment, configuration: Configuration) = {
    Seq(
      bind[Langs].toProvider[DefaultLangsProvider],
      bind[MessagesApi].toProvider[DefaultMessagesApiProvider]
    )
  }
}

/**
 * Injection helper for i18n components
 */
trait I18nComponents {

  def environment: Environment
  def configuration: Configuration

  lazy val messagesApi: MessagesApi = new DefaultMessagesApiProvider(environment, configuration, langs).get
  lazy val langs: Langs = new DefaultLangsProvider(configuration).get
}
