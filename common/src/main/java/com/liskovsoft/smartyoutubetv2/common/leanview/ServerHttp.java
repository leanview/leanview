package com.liskovsoft.smartyoutubetv2.common.leanview;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** leanview: minimal JSON requests to the person's server. Blocking: call off the main thread. */
public final class ServerHttp {
    private static final int TIMEOUT_MS = 15_000;
    /** The address check talks to a host nobody has vetted: it reads at most this much. */
    private static final int PROBE_MAX_BYTES = 64 * 1024;

    private ServerHttp() {
    }

    public static final class Response {
        public final int status;
        public final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /**
     * GET for a host that is not trusted yet (the address check): redirects are not followed (the
     * person types the final address) and the answer is cut at {@value #PROBE_MAX_BYTES} bytes.
     */
    public static Response probe(String url) throws IOException {
        return execute("GET", url, null, null, null, null, false, PROBE_MAX_BYTES);
    }

    public static Response request(String method, String url, String token, String jsonBody) throws IOException {
        return request(method, url, token, jsonBody, null, null);
    }

    /** With one extra header (e.g. a Cookie session). */
    public static Response request(String method, String url, String token, String jsonBody,
                                   String headerName, String headerValue) throws IOException {
        return execute(method, url, token, jsonBody, headerName, headerValue, true, Integer.MAX_VALUE);
    }

    private static Response execute(String method, String url, String token, String jsonBody,
                                    String headerName, String headerValue, boolean followRedirects, int maxBytes) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(followRedirects);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (headerName != null) {
                connection.setRequestProperty(headerName, headerValue);
            }
            if (jsonBody != null) {
                byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }
            }
            int status = connection.getResponseCode();
            InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            return new Response(status, in == null ? "" : readBounded(in, maxBytes));
        } finally {
            connection.disconnect();
        }
    }

    /** The stream as text, cut after {@code max} bytes. */
    static String readBounded(InputStream in, int max) throws IOException {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while (total < max && (read = stream.read(buffer, 0, Math.min(buffer.length, max - total))) != -1) {
                out.write(buffer, 0, read);
                total += read;
            }
            return out.toString("UTF-8");
        }
    }
}
