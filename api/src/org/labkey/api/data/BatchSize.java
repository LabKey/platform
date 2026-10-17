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
package org.labkey.api.data;

import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.logging.LogHelper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Standard sizes for splitting multi-row edits into batches. Set {@code -Dlabkey.batchSize.<kind>=N} at startup to run
 * with other sizes, e.g. tiny ones to shake out batch-boundary bugs; tests use {@link #override}.
 */
public final class BatchSize
{
    private static final Logger LOG = LogHelper.getLogger(BatchSize.class, "Batch sizes for multi-row edits");

    public enum Kind
    {
        /** Key-partitioned selects, updates, and deletes, and flush-every-N loops over rows */
        ROWS(1000),
        /** Bounds both the JDBC batch and the audit events held in memory while a batch accumulates */
        AUDIT(2500);

        private final int _defaultSize;

        Kind(int defaultSize)
        {
            _defaultSize = defaultSize;
        }

        private String getSystemProperty()
        {
            return "labkey.batchSize." + name().toLowerCase();
        }
    }

    private static final AtomicIntegerArray SIZES = new AtomicIntegerArray(Kind.values().length);
    private static final boolean[] OVERRIDDEN = new boolean[Kind.values().length];

    static
    {
        for (Kind kind : Kind.values())
        {
            SIZES.set(kind.ordinal(), parseSize(kind, System.getProperty(kind.getSystemProperty())));
        }
    }

    private BatchSize()
    {
    }

    // Log and fall back rather than throw, since a static initializer failure surfaces only as NoClassDefFoundError
    private static int parseSize(Kind kind, String value)
    {
        if (null == value)
            return kind._defaultSize;

        try
        {
            int size = Integer.parseInt(value.trim());
            if (size >= 1)
            {
                if (size != kind._defaultSize)
                    LOG.info("Batch size {} set to {} (default {})", kind, size, kind._defaultSize);
                return size;
            }
        }
        catch (NumberFormatException ignored)
        {
        }

        LOG.error("Ignoring {}={}: must be an integer of at least 1; using default {}", kind.getSystemProperty(), value, kind._defaultSize);
        return kind._defaultSize;
    }

    public static int get(Kind kind)
    {
        return SIZES.get(kind.ordinal());
    }

    /**
     * Server-wide, so concurrent requests see it too; for tests that need to cross a batch boundary with few rows.
     * Close the result to restore the previous size.
     */
    public static synchronized AutoCloseable override(Kind kind, int size)
    {
        if (size < 1)
            throw new IllegalArgumentException("Batch size must be at least 1, but was " + size);
        // Overlapping overrides would restore out of order and leave the wrong size behind
        if (OVERRIDDEN[kind.ordinal()])
            throw new IllegalStateException("Batch size " + kind + " is already overridden");

        int previous = SIZES.getAndSet(kind.ordinal(), size);
        OVERRIDDEN[kind.ordinal()] = true;

        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            synchronized (BatchSize.class)
            {
                if (closed.getAndSet(true))
                    return;
                SIZES.set(kind.ordinal(), previous);
                OVERRIDDEN[kind.ordinal()] = false;
            }
        };
    }

    public static class TestCase extends Assert
    {
        @Test
        public void overrideRestores() throws Exception
        {
            int original = get(Kind.ROWS);

            try (AutoCloseable ignored = override(Kind.ROWS, 3))
            {
                assertEquals(3, get(Kind.ROWS));
                assertThrows(IllegalStateException.class, () -> override(Kind.ROWS, 5));
            }

            assertEquals(original, get(Kind.ROWS));
        }

        @Test
        public void secondCloseIsNoOp() throws Exception
        {
            int original = get(Kind.ROWS);
            AutoCloseable first = override(Kind.ROWS, 3);
            first.close();

            try (AutoCloseable ignored = override(Kind.ROWS, 5))
            {
                first.close();
                assertEquals(5, get(Kind.ROWS));
            }

            assertEquals(original, get(Kind.ROWS));
        }

        @Test
        public void invalidPropertyUsesDefault()
        {
            assertEquals(Kind.ROWS._defaultSize, parseSize(Kind.ROWS, "0"));
            assertEquals(Kind.ROWS._defaultSize, parseSize(Kind.ROWS, "10k"));
            assertEquals(7, parseSize(Kind.ROWS, "7"));
        }

        @Test
        public void rejectsNonPositive()
        {
            assertThrows(IllegalArgumentException.class, () -> override(Kind.ROWS, 0));
        }
    }
}
