/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;

/** Fixed HTTPS origin, no redirects, no cookies, bounded replies and no credential logging. */
final class FluxerHttpTransport implements FluxerAuth.Transport {
    private final ExecutorService network = Executors.newFixedThreadPool(2);
    private final Executor owner;
    private int pending;
    private Runnable drained;
    FluxerHttpTransport(Executor owner) { this.owner = owner; }
    @Override public void request(String method, String path, JsonObject body, String token, Consumer<FluxerAuth.Reply> done) {
        pending++;
        network.execute(() -> {
            FluxerAuth.Reply reply = exchange(method, path, body, token);
            try { owner.execute(() -> {
                try { done.accept(reply); }
                finally { pending--; finishIfDrained(); }
            }); } catch (RejectedExecutionException ignored) { }
        });
    }
    static FluxerAuth.Reply exchange(String method, String path, JsonObject body, String token) {
        HttpsURLConnection connection = null;
        try {
            if (!allowed(method, path, token)) throw new IllegalArgumentException("Unsupported auth request");
            connection = (HttpsURLConnection) new URL(FluxerAuth.ORIGIN + path).openConnection();
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "AbyssME/0.5 (Android native user client)");
            if (token != null) connection.setRequestProperty("Authorization", token);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            long retry = retryMillis(connection.getHeaderField("Retry-After"));
            if (status < 200 || status >= 300) return new FluxerAuth.Reply(status, null, retry);
            JsonElement result = new JsonObject();
            if (status != 204) try (InputStream input = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096]; int n;
                long deadline = System.nanoTime() + 15000000000L;
                while ((n = input.read(chunk)) != -1) {
                    if (out.size() + n > 2097152 || System.nanoTime() > deadline) throw new java.io.IOException("Reply too large or slow");
                    out.write(chunk, 0, n);
                }
                if (out.size() > 0) result = JsonParser.parseString(out.toString("UTF-8"));
            }
            return new FluxerAuth.Reply(status, result, retry);
        } catch (Exception ignored) { return new FluxerAuth.Reply(0, null, 0); }
        finally { if (connection != null) connection.disconnect(); }
    }
    static boolean allowed(String method, String path, String token) {
        if (token != null && !FluxerAuth.validToken(token)) return false;
        if (token != null) return ("GET".equals(method) && "/v1/users/@me".equals(path))
                || ("POST".equals(method) && "/v1/auth/logout".equals(path))
                || ("GET".equals(method) && ("/v1/users/@me/channels".equals(path) || "/v1/users/@me/relationships".equals(path)))
                || ("POST".equals(method) && ("/v1/users/@me/channels".equals(path) || "/v1/users/@me/relationships".equals(path)))
                || (("PUT".equals(method)||"DELETE".equals(method)) && path.matches("/v1/users/@me/relationships/[1-9][0-9]{0,19}"))
                || ("GET".equals(method) && path.matches("/v1/channels/[1-9][0-9]{0,19}/messages\\?limit=50(?:&before=[1-9][0-9]{0,19})?"))
                || ("POST".equals(method) && path.matches("/v1/channels/[1-9][0-9]{0,19}/messages"));
        return ("GET".equals(method) && "/.well-known/fluxer".equals(path))
                || ("POST".equals(method) && "/v1/auth/handoff/initiate".equals(path))
                || ("POST".equals(method) && path.matches("/v1/auth/handoff/[A-Z2-9]{6}-?[A-Z2-9]{6}/status"))
                || ("DELETE".equals(method) && path.matches("/v1/auth/handoff/[A-Z2-9]{6}-?[A-Z2-9]{6}"));
    }
    private static long retryMillis(String text) {
        if (text == null) return 0;
        try { return (long) Math.min(300000, Math.max(0, Double.parseDouble(text) * 1000)); }
        catch (NumberFormatException ignored) {
            try { return Math.max(0, Math.min(300000, ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli() - System.currentTimeMillis())); }
            catch (RuntimeException e) { return 0; }
        }
    }
    void closeWhenIdle(Runnable onDrained) { drained = onDrained; finishIfDrained(); }
    private void finishIfDrained() {
        if (pending == 0 && drained != null) {
            network.shutdown(); Runnable callback = drained; drained = null; callback.run();
        }
    }
}
