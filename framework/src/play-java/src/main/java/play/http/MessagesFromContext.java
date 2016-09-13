package play.http;

import com.google.common.collect.Lists;
import play.i18n.Lang;
import play.i18n.Langs;
import play.i18n.Messages;
import play.i18n.MessagesApi;
import play.mvc.Http;

import javax.inject.Inject;
import java.util.LinkedList;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 *
 */
public class MessagesFromContext {

    private final Langs langs;
    private final MessagesApi messagesApi;

    @Inject
    public MessagesFromContext(Langs langs, MessagesApi messagesApi) {
        this.langs = langs;
        this.messagesApi = messagesApi;
    }

    /**
     * @return the messages for the current lang
     */
    public Messages messages(Http.Context context) {

        Http.Cookie langCookie = context.request().cookies().get(langs.langCookieName());
        Lang cookieLang = langCookie == null ? null : new Lang(play.api.i18n.Lang.apply(langCookie.value()));

        LinkedList<Lang> langs = context.request().acceptLanguages().stream().map(Lang::new).collect(Collectors.toCollection(LinkedList::new));
        if (cookieLang != null) {
            langs.addFirst(cookieLang);
        }
        if (context.lang() != null) {
            langs.addFirst(new Lang(context.lang()));
        }
        return messagesApi.preferred(langs);
    }

    /**
     * Change durably the lang for the current user.
     *
     * @param code New lang code to use (e.g. "fr", "en-US", etc.)
     * @return true if the requested lang was supported by the application, otherwise false
     */
    public boolean changeLang(Http.Context context, String code) {
        return changeLang(context, Lang.forCode(code));
    }

    /**
     * Change durably the lang for the current user.
     *
     * @param lang New Lang object to use
     * @return true if the requested lang was supported by the application, otherwise false
     */
    public boolean changeLang(Http.Context context, Lang lang) {
        if (langs().availables().contains(lang)) {
            context.setTransientLang(lang.toLocale());
            scala.Option<String> domain = play.api.mvc.Session.domain();
            context.response().setCookie(langs().langCookieName(), lang.code(), null, play.api.mvc.Session.path(),
                    domain.isDefined() ? domain.get() : null, langs().langCookieSecure(), langs().langCookieHttpOnly());
            return true;
        } else {
            return false;
        }
    }

    private Langs langs() {
        return null;
    }

    /**
     * Clear the lang for the current user.
     */
    public void clearLang(Http.Context context) {
        context.setTransientLang((Locale) null);
        scala.Option<String> domain = play.api.mvc.Session.domain();
        context.response().discardCookie(langs().langCookieName(), play.api.mvc.Session.path(),
                domain.isDefined() ? domain.get() : null, langs().langCookieSecure());
    }
}
