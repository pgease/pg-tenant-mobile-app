package in.pgease.tenant.shell;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides what the shell should do with a URL. Pure Java so it can be unit tested
 * without an Android runtime. The live tenant site, Easebuzz checkout, and the
 * return to tenant.pgease.com stay inside the WebView. UPI, phone, mail, WhatsApp
 * and maps leave the WebView.
 */
public final class ShellNavigationPolicy {

    public enum Decision {
        /** Let the WebView load the URL. */
        LOAD,
        /** Open the URL in another installed app. */
        EXTERNAL,
        /** Save the file with DownloadManager instead of navigating. */
        DOWNLOAD,
        /** Drop the navigation. */
        CANCEL
    }

    public static final String PRODUCTION_HOST = "tenant.pgease.com";
    public static final String PRODUCTION_ORIGIN = "https://tenant.pgease.com";

    private static final Set<String> EXTERNAL_SCHEMES = setOf(
        "upi",
        "upiapp",
        "tez",
        "gpay",
        "phonepe",
        "paytm",
        "paytmmp",
        "bhim",
        "credpay",
        "amazonpay",
        "mobikwik",
        "freecharge",
        "slice",
        "supermoney",
        "navi",
        "imobile",
        "whatsapp",
        "sms",
        "smsto",
        "tel",
        "mailto",
        "geo",
        "intent",
        "market"
    );

    private static final Set<String> EXTERNAL_HOSTS = setOf(
        "wa.me",
        "api.whatsapp.com",
        "web.whatsapp.com",
        "chat.whatsapp.com",
        "maps.google.com",
        "maps.app.goo.gl",
        "play.google.com"
    );

    private static final Set<String> ROOT_PATHS = setOf("/", "/login", "/home");

    private final String appHost;
    private final String homeUrl;

    public ShellNavigationPolicy(String serverUrl) {
        String host = hostOf(serverUrl);
        this.appHost = host.isEmpty() ? PRODUCTION_HOST : host;
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            this.homeUrl = ensureAppParam(PRODUCTION_ORIGIN + "/");
        } else {
            this.homeUrl = ensureAppParam(serverUrl.trim());
        }
    }

    public String getAppHost() {
        return appHost;
    }

    public String getHomeUrl() {
        return homeUrl;
    }

    public Decision decide(String rawUrl) {
        if (rawUrl == null) {
            return Decision.CANCEL;
        }
        String url = rawUrl.trim();
        if (url.isEmpty()) {
            return Decision.CANCEL;
        }

        String scheme = schemeOf(url);
        if (scheme.isEmpty()) {
            return Decision.LOAD;
        }

        if ("javascript".equals(scheme) || "about".equals(scheme) || "blob".equals(scheme) || "data".equals(scheme)) {
            return Decision.LOAD;
        }
        if ("file".equals(scheme)) {
            return Decision.CANCEL;
        }

        String path = pathOf(url);
        if (path.startsWith("/_capacitor_http_interceptor_")) {
            return Decision.CANCEL;
        }

        if (EXTERNAL_SCHEMES.contains(scheme)) {
            return Decision.EXTERNAL;
        }

        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            // Custom scheme deep links are handled separately; anything else unknown stays out of the WebView.
            if ("pgease".equals(scheme)) {
                return Decision.LOAD;
            }
            return Decision.EXTERNAL;
        }

        if (isPdf(path)) {
            return Decision.DOWNLOAD;
        }

        String host = hostOf(url);
        if (isExternalHost(host, path)) {
            return Decision.EXTERNAL;
        }
        return Decision.LOAD;
    }

    public boolean isAppHost(String url) {
        String host = hostOf(url);
        return PRODUCTION_HOST.equals(host) || appHost.equals(host) || "localhost".equals(host);
    }

    public boolean isInAppRoot(String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:")) {
            return true;
        }
        if (!isAppHost(url)) {
            return false;
        }
        String path = pathOf(url);
        if (path.isEmpty()) {
            path = "/";
        }
        return ROOT_PATHS.contains(path);
    }

    /**
     * Maps an inbound https or pgease:// link to a URL the WebView should open.
     * Returns null when the intent is not one of our links.
     */
    public String toInAppUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String url = rawUrl.trim();
        String scheme = schemeOf(url);
        if ("https".equals(scheme) || "http".equals(scheme)) {
            if (!PRODUCTION_HOST.equals(hostOf(url)) && !appHost.equals(hostOf(url))) {
                return null;
            }
            return ensureAppParam(url);
        }
        if (!"pgease".equals(scheme)) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (!"tenant".equalsIgnoreCase(host)) {
                if (host.isEmpty()) {
                    return homeUrl;
                }
                path = "/" + host + path;
            }
            if (path.isEmpty()) {
                path = "/";
            }
            StringBuilder builder = new StringBuilder(PRODUCTION_ORIGIN).append(path);
            if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
                builder.append('?').append(uri.getQuery());
            }
            if (uri.getFragment() != null && !uri.getFragment().isEmpty()) {
                builder.append('#').append(uri.getFragment());
            }
            return ensureAppParam(builder.toString());
        } catch (Exception ignored) {
            return homeUrl;
        }
    }

    public static String ensureAppParam(String url) {
        if (url == null || url.isEmpty()) {
            return PRODUCTION_ORIGIN + "/?app=1";
        }
        int hash = url.indexOf('#');
        String base = hash >= 0 ? url.substring(0, hash) : url;
        String fragment = hash >= 0 ? url.substring(hash) : "";
        if (base.contains("app=1")) {
            return url;
        }
        base = base + (base.contains("?") ? "&" : "?") + "app=1";
        return base + fragment;
    }

    public static String schemeOf(String url) {
        if (url == null) {
            return "";
        }
        int colon = url.indexOf(':');
        if (colon <= 0) {
            return "";
        }
        String scheme = url.substring(0, colon).toLowerCase(Locale.ROOT);
        for (int i = 0; i < scheme.length(); i++) {
            char c = scheme.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '+' || c == '.' || c == '-';
            if (!ok) {
                return "";
            }
        }
        return scheme;
    }

    public static String hostOf(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(url.replace(" ", "%20"));
            if (uri.getHost() != null) {
                return uri.getHost().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // Fall through to a small manual parse.
        }
        String scheme = schemeOf(url);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return "";
        }
        int start = url.indexOf("://");
        if (start < 0) {
            return "";
        }
        start += 3;
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String hostPort = url.substring(start, end);
        int at = hostPort.lastIndexOf('@');
        if (at >= 0) {
            hostPort = hostPort.substring(at + 1);
        }
        int colon = hostPort.indexOf(':');
        if (colon >= 0) {
            hostPort = hostPort.substring(0, colon);
        }
        return hostPort.toLowerCase(Locale.ROOT);
    }

    public static String pathOf(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(url.replace(" ", "%20"));
            if (uri.getPath() != null) {
                return uri.getPath();
            }
        } catch (Exception ignored) {
            // Fall through.
        }
        int start = url.indexOf("://");
        if (start < 0) {
            return "";
        }
        int pathStart = url.indexOf('/', start + 3);
        if (pathStart < 0) {
            return "";
        }
        int end = url.length();
        for (int i = pathStart; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        return url.substring(pathStart, end);
    }

    public static boolean isUpiScheme(String url) {
        String scheme = schemeOf(url);
        return "upi".equals(scheme)
            || "upiapp".equals(scheme)
            || "tez".equals(scheme)
            || "gpay".equals(scheme)
            || "phonepe".equals(scheme)
            || "paytm".equals(scheme)
            || "paytmmp".equals(scheme)
            || "bhim".equals(scheme)
            || "credpay".equals(scheme);
    }

    private static boolean isPdf(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private static boolean isExternalHost(String host, String path) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        if (EXTERNAL_HOSTS.contains(host)) {
            return true;
        }
        if ("www.google.com".equals(host) || "google.com".equals(host)) {
            return path.startsWith("/maps");
        }
        if ("whatsapp.com".equals(host) || "www.whatsapp.com".equals(host)) {
            return path.startsWith("/send") || path.startsWith("/dl");
        }
        return false;
    }

    private static Set<String> setOf(String... values) {
        HashSet<String> set = new HashSet<>();
        Collections.addAll(set, values);
        return set;
    }
}
