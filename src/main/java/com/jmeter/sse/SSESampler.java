package com.jmeter.sse;

import okhttp3.*;
import okhttp3.sse.*;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JMeter Sampler for Server-Sent Events (SSE).
 *
 * Connects to an SSE endpoint, listens for events for a configurable duration,
 * then closes the connection and reports results to JMeter.
 *
 * Design notes:
 * - All SampleResult field writes happen exclusively on the JMeter thread (finally block)
 *   to avoid race conditions with OkHttp callback threads.
 * - intentionalCancel flag suppresses the spurious onFailure("Socket closed") that
 *   OkHttp fires when we call eventSource.cancel() ourselves.
 */
public class SSESampler extends AbstractSampler {

    private static final Logger log = LoggerFactory.getLogger(SSESampler.class);

    // JMeter property keys
    public static final String URL      = "SSESampler.url";
    public static final String DURATION = "SSESampler.duration";
    public static final String HEADERS  = "SSESampler.headers";

    /**
     * Shared client — one connection pool reused across all threads/samples.
     * Creating a new OkHttpClient per sample() causes thread/socket exhaustion.
     */
    private static final OkHttpClient SHARED_CLIENT = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)   // SSE: keep connection alive indefinitely
            .connectTimeout(10, TimeUnit.SECONDS)
            .build();

    @Override
    public SampleResult sample(Entry entry) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setSamplerData("Connecting to: " + getUrl());
        result.sampleStart();

        // --- Shared state between JMeter thread and OkHttp callback threads ---
        CountDownLatch latch             = new CountDownLatch(1);
        StringBuilder  responseData      = new StringBuilder();
        AtomicBoolean  failed            = new AtomicBoolean(false);
        AtomicBoolean  intentionalCancel = new AtomicBoolean(false); // suppress cancel-induced onFailure
        AtomicInteger  eventCount        = new AtomicInteger(0);
        AtomicLong     totalBytes        = new AtomicLong(0);

        // Failure details collected on OkHttp thread, applied on JMeter thread
        AtomicReference<String> failureCode    = new AtomicReference<>("500");
        AtomicReference<String> failureMessage = new AtomicReference<>("Unknown error");

        // --- Build request ---
        Request.Builder requestBuilder = new Request.Builder()
                .url(getUrl())
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache");

        String headersRaw = getHeaders();
        if (headersRaw != null && !headersRaw.trim().isEmpty()) {
            for (String line : headersRaw.split("\\n")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String name  = parts[0].trim();
                    String value = parts[1].trim();
                    if (!name.isEmpty()) {
                        requestBuilder.header(name, value);
                        log.debug("Added header: {}: {}", name,
                                name.equalsIgnoreCase("Authorization") ? "***" : value);
                    }
                }
            }
        }

        Request request = requestBuilder.build();

        // --- SSE Listener (runs on OkHttp threads — only writes to atomics, not result) ---
        EventSourceListener listener = new EventSourceListener() {

            @Override
            public void onOpen(EventSource eventSource, Response response) {
                result.connectEnd(); // mark connection-establishment boundary
                int code = response.code();
                log.info("SSE connection open: HTTP {}", code);
                if (!response.isSuccessful()) {
                    // HTTP-level failure (401, 403, 500, etc.)
                    failureCode.set(String.valueOf(code));
                    failureMessage.set("HTTP " + code + ": " + response.message());
                    failed.set(true);
                    latch.countDown();
                }
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                String line = "ID: " + id + " | Type: " + type + " | Data: " + data + "\n";
                responseData.append(line);
                totalBytes.addAndGet(line.length());
                int n = eventCount.incrementAndGet();
                log.debug("SSE event #{} [id={}]: {}", n, id, data);
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                // Suppress failures caused by our own eventSource.cancel() call.
                // When we cancel, OkHttp raises SocketException("Socket closed") / similar —
                // that is expected and should NOT mark the sample as failed.
                if (intentionalCancel.get()) {
                    log.debug("SSE failure suppressed (caused by intentional cancel): {}",
                            t != null ? t.getMessage() : "null");
                    latch.countDown();
                    return;
                }

                String message = (t != null) ? t.getMessage() : "Unknown SSE failure";
                String code    = (response != null) ? String.valueOf(response.code()) : "500";
                failureCode.set(code);
                failureMessage.set("Error: " + message);
                log.error("SSE failure [code={}]: {}", code, message, t);
                failed.set(true);
                latch.countDown();
            }

            @Override
            public void onClosed(EventSource eventSource) {
                log.info("SSE connection closed normally. Events received: {}", eventCount.get());
                latch.countDown();
            }
        };

        EventSource eventSource = EventSources.createFactory(SHARED_CLIENT)
                .newEventSource(request, listener);

        try {
            long durationSec = Long.parseLong(getDuration());
            boolean latchCompleted = latch.await(durationSec, TimeUnit.SECONDS);
            log.info("SSE latch released (completed={}, failed={}, events={})",
                    latchCompleted, failed.get(), eventCount.get());

        } catch (InterruptedException e) {
            failed.set(true);
            failureCode.set("500");
            failureMessage.set("Thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();

        } catch (NumberFormatException e) {
            failed.set(true);
            failureCode.set("500");
            failureMessage.set("Invalid duration value: '" + getDuration() + "'");

        } finally {
            // Set intentionalCancel BEFORE cancel() so that the onFailure callback
            // triggered by cancel() is suppressed and does NOT overwrite our result.
            intentionalCancel.set(true);
            eventSource.cancel();

            result.sampleEnd();

            // All result field writes happen here, on the JMeter thread, after cancel().
            // No OkHttp callback can interfere at this point.
            String body = responseData.toString();
            result.setResponseData(body, "UTF-8");
            result.setBodySize(totalBytes.get());
            result.setBytes(totalBytes.get() + result.getHeadersSize());

            if (failed.get()) {
                result.setSuccessful(false);
                result.setResponseCode(failureCode.get());
                result.setResponseMessage(failureMessage.get());
            } else {
                result.setSuccessful(true);
                result.setResponseCodeOK();
                result.setResponseMessage("OK — " + eventCount.get() + " events received");
            }

            log.info("SSE sample complete: success={}, code={}, events={}, bytes={}",
                    result.isSuccessful(), result.getResponseCode(),
                    eventCount.get(), totalBytes.get());
        }

        return result;
    }

    // Property accessors — getPropertyAsString supports JMeter ${variable} interpolation
    public String getUrl()      { return getPropertyAsString(URL); }
    public String getDuration() { return getPropertyAsString(DURATION, "10"); }
    public String getHeaders()  { return getPropertyAsString(HEADERS, ""); }
}