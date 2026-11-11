/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.util;

import jakarta.mail.Address;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.usageMetrics.ApiMetricArea;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.usageMetrics.UsageMetricsService;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/** Volume, timing, and burst metrics for outgoing email; GH Issue 1438 */
public class MailMetrics
{
    public enum Origin
    {
        SINGLE, BULK, DIGEST;

        private final String _key = name().toLowerCase();
    }

    private static final ThreadLocal<Batch> CURRENT_BATCH = new ThreadLocal<>();
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static final AtomicInteger PEAK_IN_FLIGHT = new AtomicInteger();
    private static final PeakRate PER_MINUTE = new PeakRate(TimeUnit.MINUTES.toMillis(1));
    private static final PeakRate PER_HOUR = new PeakRate(TimeUnit.HOURS.toMillis(1));
    private static final Map<Origin, AtomicInteger> LARGEST_BATCH = Arrays.stream(Origin.values())
        .collect(Collectors.toMap(o -> o, _ -> new AtomicInteger(), (a, _) -> a, () -> new EnumMap<>(Origin.class)));
    private static final Map<String, SendTimes> SEND_TIMES = new ConcurrentHashMap<>();

    private record SendTimes(LongAdder sends, LongAdder millis) {}

    public static void registerMetricsProvider()
    {
        UsageMetricsService.get().registerUsageMetrics("API", metrics -> {
            metrics.group("emailSendPeaks", "Email sends since server startup: busiest clock minute and hour (UTC start), most concurrent sends, largest bulk and digest runs, and send count and total milliseconds by transport provider")
                .put("peakMinute", PER_MINUTE::toMap)
                .put("peakHour", PER_HOUR::toMap)
                .put("peakConcurrent", PEAK_IN_FLIGHT::get)
                .put("largestBatch", () -> LARGEST_BATCH.entrySet().stream()
                    .filter(e -> e.getKey() != Origin.SINGLE)
                    .collect(Collectors.toMap(e -> e.getKey()._key, e -> e.getValue().get())))
                .put("sendTimes", () -> SEND_TIMES.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> Map.of("sends", e.getValue().sends().sum(), "totalMillis", e.getValue().millis().sum()))));
            return metrics;
        });
    }

    /** Attributes this thread's sends to a bulk or digest run until closed; a nested batch folds into the outer one */
    public static Batch startBatch(@NotNull Origin origin)
    {
        if (CURRENT_BATCH.get() != null)
            return new Batch(origin, false);

        Batch batch = new Batch(origin, true);
        CURRENT_BATCH.set(batch);
        return batch;
    }

    public static class Batch implements QuietCloser
    {
        private final @NotNull Origin _origin;
        private final boolean _outermost;
        private int _sends = 0;

        private Batch(@NotNull Origin origin, boolean outermost)
        {
            _origin = origin;
            _outermost = outermost;
        }

        @Override
        public void close()
        {
            if (!_outermost)
                return;

            CURRENT_BATCH.remove();

            if (_sends > 0)
            {
                increment(ApiMetricArea.EMAIL_BATCH_SIZES, _origin._key + "-" + batchBucket(_sends));
                LARGEST_BATCH.get(_origin).accumulateAndGet(_sends, Math::max);
            }
        }
    }

    static void notConfigured()
    {
        increment(ApiMetricArea.EMAIL_SEND_FAILURES, "notConfigured");
    }

    static Send startSend(@NotNull String providerName, @NotNull Message m)
    {
        Batch batch = CURRENT_BATCH.get();
        Origin origin = Origin.SINGLE;
        if (batch != null)
        {
            origin = batch._origin;
            batch._sends++;
        }

        long now = System.currentTimeMillis();
        PER_MINUTE.record(now);
        PER_HOUR.record(now);
        PEAK_IN_FLIGHT.accumulateAndGet(IN_FLIGHT.incrementAndGet(), Math::max);
        increment(ApiMetricArea.EMAIL_RECIPIENTS, origin._key + "-" + recipientBucket(countRecipients(m)));

        return new Send(origin, StringUtils.deleteWhitespace(providerName));
    }

    static class Send implements QuietCloser
    {
        private final Origin _origin;
        private final String _provider;
        private final long _start = System.nanoTime();
        private @Nullable Throwable _failure = null;

        private Send(Origin origin, String provider)
        {
            _origin = origin;
            _provider = provider;
        }

        void failed(Throwable t)
        {
            _failure = t;
        }

        @Override
        public void close()
        {
            IN_FLIGHT.decrementAndGet();
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - _start);
            SendTimes times = SEND_TIMES.computeIfAbsent(_provider, k -> new SendTimes(new LongAdder(), new LongAdder()));
            times.sends().increment();
            times.millis().add(millis);
            increment(ApiMetricArea.EMAIL_SEND_TIMES, _provider + "-" + durationBucket(millis));
            increment(ApiMetricArea.EMAIL_SENDS, _origin._key + "-" + _provider + "-" + (_failure == null ? "sent" : "failed"));
            if (_failure != null)
                increment(ApiMetricArea.EMAIL_SEND_FAILURES, _provider + "-" + failureCause(_failure));
        }
    }

    private static void increment(ApiMetricArea area, String metricName)
    {
        SimpleMetricsService.get().increment(area, metricName);
    }

    private static int countRecipients(Message m)
    {
        try
        {
            Address[] recipients = m.getAllRecipients();
            return recipients == null ? 0 : recipients.length;
        }
        catch (MessagingException e)
        {
            return 0;
        }
    }

    static String batchBucket(int sends)
    {
        if (sends == 1) return "1";
        if (sends <= 10) return "2To10";
        if (sends <= 50) return "11To50";
        if (sends <= 200) return "51To200";
        if (sends <= 1000) return "201To1000";
        return "GT1000";
    }

    static String recipientBucket(int recipients)
    {
        if (recipients <= 1) return String.valueOf(recipients);
        if (recipients <= 10) return "2To10";
        if (recipients <= 50) return "11To50";
        if (recipients <= 200) return "51To200";
        return "GT200";
    }

    static String durationBucket(long millis)
    {
        if (millis < 250) return "LT250ms";
        if (millis < 1000) return "250msTo1s";
        if (millis < 5000) return "1sTo5s";
        if (millis < 30000) return "5sTo30s";
        return "GT30s";
    }

    static String failureCause(Throwable t)
    {
        if (ExceptionUtils.indexOfType(t, SocketTimeoutException.class) >= 0) return "timeout";
        if (ExceptionUtils.indexOfType(t, AuthenticationFailedException.class) >= 0) return "authentication";
        if (ExceptionUtils.indexOfType(t, SendFailedException.class) >= 0) return "rejectedAddress";
        if (ExceptionUtils.indexOfType(t, SocketException.class) >= 0 || ExceptionUtils.indexOfType(t, UnknownHostException.class) >= 0) return "connection";
        return "other";
    }

    /** Busiest clock-aligned window seen; a burst straddling a boundary is split across two windows */
    static class PeakRate
    {
        private final long _windowMillis;
        private long _windowStart = -1;
        private long _peakWindowStart = -1;
        private int _count = 0;
        private int _peak = 0;

        PeakRate(long windowMillis)
        {
            _windowMillis = windowMillis;
        }

        synchronized void record(long now)
        {
            long windowStart = now - now % _windowMillis;
            if (windowStart != _windowStart)
            {
                _windowStart = windowStart;
                _count = 0;
            }
            if (++_count > _peak)
            {
                _peak = _count;
                _peakWindowStart = windowStart;
            }
        }

        synchronized Map<String, Object> toMap()
        {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sends", _peak);
            if (_peak > 0)
                map.put("start", Instant.ofEpochMilli(_peakWindowStart).toString());
            return map;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testBuckets()
        {
            assertEquals("1", batchBucket(1));
            assertEquals("2To10", batchBucket(10));
            assertEquals("201To1000", batchBucket(1000));
            assertEquals("GT1000", batchBucket(1001));
            assertEquals("0", recipientBucket(0));
            assertEquals("51To200", recipientBucket(200));
            assertEquals("GT200", recipientBucket(201));
            assertEquals("LT250ms", durationBucket(249));
            assertEquals("250msTo1s", durationBucket(250));
            assertEquals("GT30s", durationBucket(30000));
        }

        @Test
        public void testFailureCause()
        {
            assertEquals("timeout", failureCause(new MessagingException("wrapped", new SocketTimeoutException())));
            assertEquals("authentication", failureCause(new AuthenticationFailedException()));
            assertEquals("rejectedAddress", failureCause(new SendFailedException()));
            assertEquals("connection", failureCause(new MessagingException("wrapped", new UnknownHostException())));
            assertEquals("other", failureCause(new IllegalStateException()));
        }

        @Test
        public void testPeakRate()
        {
            PeakRate rate = new PeakRate(60_000);
            assertEquals(Map.of("sends", 0), rate.toMap());
            rate.record(60_000);
            rate.record(119_999);
            rate.record(120_000);
            assertEquals(2, rate.toMap().get("sends"));
            assertEquals(Instant.ofEpochMilli(60_000).toString(), rate.toMap().get("start"));
            rate.record(120_001);
            rate.record(120_002);
            assertEquals(3, rate.toMap().get("sends"));
            assertEquals(Instant.ofEpochMilli(120_000).toString(), rate.toMap().get("start"));
        }

        @Test
        public void testNestedBatch()
        {
            try (Batch outer = startBatch(Origin.BULK))
            {
                try (Batch inner = startBatch(Origin.DIGEST))
                {
                    assertNotSame(outer, inner);
                    assertSame(outer, CURRENT_BATCH.get());
                }
                assertSame(outer, CURRENT_BATCH.get());
            }
            assertNull(CURRENT_BATCH.get());
        }
    }
}
