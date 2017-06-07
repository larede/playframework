package play.filters.csp

import javax.inject.{ Inject, Singleton }

import play.api.Configuration
import play.api.mvc.{ EssentialAction, EssentialFilter, RequestHeader, Result }
import play.filters.headers.SecurityHeadersConfig

/**
 *
 *
 * @see <a href="http://www.adobe.com/devnet/articles/crossdomain_policy_file_spec.html">Cross Domain Policy File Specification</a>
 */
@Singleton
class ContentSecurityPolicyFilter @Inject() (config: ContentSecurityPolicyConfig) extends EssentialFilter {
  import ContentSecurityPolicyFilter._

  /**
   *
   */
  protected def headers(request: RequestHeader, result: Result): Seq[(String, String)] = {
    Seq(CONTENT_SECURITY_POLICY_HEADER -> config.contentSecurityPolicy).flatten
  }

  /**
   * Applies the filter to an action, appending the headers to the result so it shows in the HTTP response.
   */
  def apply(next: EssentialAction) = EssentialAction { req =>
    import play.core.Execution.Implicits.trampoline
    next(req).map(result => result.withHeaders(headers(req, result): _*))
  }
}

case class ContentSecurityPolicyConfig(contentSecurityPolicy: String = "default-src 'self'") {

  def withContentSecurityPolicy(contentSecurityPolicy: String): ContentSecurityPolicyConfig =
    copy(contentSecurityPolicy = contentSecurityPolicy)
}

/**
 * Parses out a SecurityHeadersConfig from play.api.Configuration (usually this means application.conf).
 */
object ContentSecurityPolicy {

  def fromConfiguration(conf: Configuration): ContentSecurityPolicyConfig = {
    val config = conf.get[Configuration]("play.filters.csp")

    ContentSecurityPolicyConfig(
      contentSecurityPolicy = config.get[String]("contentSecurityPolicy")
    )
  }
}

object ContentSecurityPolicyFilter {
  val CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy"
}
