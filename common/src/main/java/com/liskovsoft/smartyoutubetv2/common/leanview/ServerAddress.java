package com.liskovsoft.smartyoutubetv2.common.leanview;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

/**
 * leanview: the address of the person's own server. The APK carries none: it is typed in Settings >
 * Accounts and kept with each connection. Cleaned up the way the web does it (https added when
 * missing, no trailing slash, host in lower case) and checked with the web's own probe: GET
 * /api/config must answer as a compatible backend (see the web client).
 * The session token travels with every request afterwards, so clear text (http://) is accepted only
 * for the home network. Blocking when it checks: call off the main thread.
 */
public final class ServerAddress {
    /** The oldest backend the web accepts too (src/lib/auth.ts). */
    private static final int[] MIN_VERSION = {1, 17, 15};
    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    public enum Reason {
        /** Nothing answered at that address (typo, no network, server down). */
        NOT_REACHABLE,
        /** Something answered, but not a compatible backend (or one that is too old). */
        NOT_COMPATIBLE,
        /** The TLS handshake failed: self-signed, expired or wrong-name certificate. */
        CERTIFICATE,
        /** The address answers with a redirect: the person types the final address instead. */
        REDIRECT
    }

    /** Why an address cannot be used; the person gets a message for the reason. */
    public static final class Problem extends IOException {
        public final Reason reason;

        Problem(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    /** Gets a URL and returns whatever answered, whatever the status; throws when nothing did. */
    public interface Fetcher {
        ServerHttp.Response get(String url) throws IOException;
    }

    private ServerAddress() {
    }

    /** The real fetcher: no redirects followed, the answer cut at a small size (the host is not vetted yet). */
    public static Fetcher http() {
        return ServerHttp::probe;
    }

    /**
     * What the person typed as an address ("yt.example.com", "https://yt.example.com/"), or null
     * when it cannot be one: empty, spaces, a query, a fragment or a user name inside, a scheme other
     * than http or https, or http:// for a host outside the home network ({@link #needsHttps}).
     */
    public static String normalize(String input) {
        String address = build(input);
        if (address == null) {
            return null;
        }
        if (address.startsWith("http://") && !isLocalHost(hostPort(address))) {
            return null;
        }
        return address;
    }

    /** True when the input is a usable address except that it asks for http:// outside the home network. */
    public static boolean needsHttps(String input) {
        String address = build(input);
        return address != null && address.startsWith("http://") && !isLocalHost(hostPort(address));
    }

    // Scheme and host folded to lower case (the path keeps its case), https when none was typed.
    private static String build(String input) {
        if (input == null) {
            return null;
        }
        String rest = input.trim();
        String scheme = "https";
        int sep = rest.indexOf("://");
        if (sep >= 0) {
            scheme = rest.substring(0, sep).toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            rest = rest.substring(sep + 3);
        }
        while (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        if (rest.isEmpty() || rest.startsWith("/") || rest.matches("(?s).*\\s.*")
                || rest.indexOf('?') >= 0 || rest.indexOf('#') >= 0 || rest.indexOf('@') >= 0) {
            return null;
        }
        int slash = rest.indexOf('/');
        String host = (slash < 0 ? rest : rest.substring(0, slash)).toLowerCase(Locale.ROOT);
        String path = slash < 0 ? "" : rest.substring(slash);
        return scheme + "://" + host + path;
    }

    private static String hostPort(String address) {
        String rest = address.substring(address.indexOf("://") + 3);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    /** The home network: loopback, private ranges, carrier-grade NAT (Tailscale), .local and .lan. */
    private static boolean isLocalHost(String hostPort) {
        String host = hostPort;
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            host = end < 0 ? host : host.substring(0, end + 1);
        } else {
            int colon = host.lastIndexOf(':');
            host = colon < 0 ? host : host.substring(0, colon);
        }
        if (host.equals("localhost") || host.equals("[::1]") || host.endsWith(".local") || host.endsWith(".lan")) {
            return true;
        }
        Matcher ip = IPV4.matcher(host);
        if (!ip.matches()) {
            return false;
        }
        int a = Integer.parseInt(ip.group(1));
        int b = Integer.parseInt(ip.group(2));
        return a == 127 || a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168)
                || (a == 100 && b >= 64 && b <= 127);
    }

    /**
     * Throws a {@link Problem} unless the address answers as a compatible server: GET /api/config must give a JSON object with the
     * fields the app uses, that is {@code internalAuth: true}, a {@code version} at least {@link #MIN_VERSION}, and a non-empty
     * {@code backend} text (its value is not looked at: what counts is the shape of the answer, not the name of the server).
     */
    public static void check(String address, Fetcher fetcher) throws Problem {
        ServerHttp.Response response;
        try {
            response = fetcher.get(address + "/api/config");
        } catch (SSLException e) {
            throw new Problem(Reason.CERTIFICATE, "certificate problem at " + address + ": " + e.getMessage());
        } catch (IOException e) {
            throw new Problem(Reason.NOT_REACHABLE, "could not reach " + address);
        }
        if (response.status >= 300 && response.status < 400) {
            throw new Problem(Reason.REDIRECT, address + " redirects (HTTP " + response.status + ")");
        }
        if (!response.ok()) {
            throw new Problem(Reason.NOT_COMPATIBLE, "HTTP " + response.status + " at " + address);
        }
        try {
            JSONObject config = new JSONObject(response.body);
            if (config.optString("backend", "").trim().isEmpty() || !config.optBoolean("internalAuth", false)) {
                throw new Problem(Reason.NOT_COMPATIBLE, address + " is not a compatible backend");
            }
            if (!atLeastMinimum(config.optString("version", ""))) {
                throw new Problem(Reason.NOT_COMPATIBLE, address + " runs a backend that is too old");
            }
        } catch (Problem problem) {
            throw problem;
        } catch (Exception e) {
            throw new Problem(Reason.NOT_COMPATIBLE, address + " did not answer with a configuration");
        }
    }

    private static boolean atLeastMinimum(String version) {
        String[] parts = version.split("[.\\-+]");
        for (int i = 0; i < MIN_VERSION.length; i++) {
            int part;
            try {
                part = i < parts.length ? Integer.parseInt(parts[i]) : 0;
            } catch (NumberFormatException e) {
                return false;
            }
            if (part != MIN_VERSION[i]) {
                return part > MIN_VERSION[i];
            }
        }
        return true;
    }
}
