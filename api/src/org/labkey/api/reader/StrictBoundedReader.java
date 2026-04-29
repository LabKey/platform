package org.labkey.api.reader;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.test.TestWhen;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Restricts the Reader to a character limit. Throws an exception when the limit is exceeded.
 * Similar to Apache's BoundedReader, which silently truncates.
 */
public class StrictBoundedReader extends Reader
{
    private final Reader in;
    private final long maxChars;
    private long charsRead = 0;
    private boolean closed = false;

    public static class LimitExceededException extends IOException
    {
        public LimitExceededException(String message)
        {
            super(message);
        }
    }

    @SuppressWarnings("NullableProblems")
    public StrictBoundedReader(@NotNull Reader in, long maxChars)
    {
        //noinspection ConstantValue
        if (in == null)
        {
            throw new NullPointerException("Reader cannot be null");
        }
        if (maxChars < 0)
        {
            throw new IllegalArgumentException("maxChars must be non-negative");
        }
        this.in = in;
        this.maxChars = maxChars;
    }

    @Override
    public int read() throws IOException
    {
        ensureOpen();
        if (charsRead >= maxChars)
        {
            throw new LimitExceededException("Character read limit of " + maxChars + " exceeded");
        }
        int result = in.read();
        if (result != -1)
        {
            charsRead++;
        }
        return result;
    }

    @Override
    public int read(char @NonNull [] cbuf, int off, int len) throws IOException
    {
        ensureOpen();
        if (charsRead >= maxChars)
        {
            throw new LimitExceededException("Character read limit of " + maxChars + " exceeded");
        }

        long remaining = maxChars - charsRead;
        int toRead = (int) Math.min(len, remaining);
        int numRead = in.read(cbuf, off, toRead);

        if (numRead > 0)
        {
            charsRead += numRead;
        }

        if (charsRead > maxChars)
        {
            throw new LimitExceededException("Character read limit of " + maxChars + " exceeded");
        }

        return numRead;
    }

    private void ensureOpen() throws IOException
    {
        if (closed)
        {
            throw new IOException("Reader is closed");
        }
    }

    @Override
    public void close() throws IOException
    {
        if (!closed)
        {
            in.close();
            closed = true;
        }
    }

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        @Test
        public void testSingleCharReadWithinLimit() throws IOException
        {
            try (StrictBoundedReader reader = new StrictBoundedReader(new StringReader("hello"), 10))
            {
                assertEquals('h', reader.read());
                assertEquals('e', reader.read());
                assertEquals('l', reader.read());
            }
        }

        @Test
        public void testBulkReadWithinLimit() throws IOException
        {
            try (StrictBoundedReader reader = new StrictBoundedReader(new StringReader("hello"), 10))
            {
                char[] buf = new char[5];
                int n = reader.read(buf, 0, 5);
                assertEquals(5, n);
                assertEquals("hello", new String(buf, 0, n));
            }
        }

        @Test
        public void testBulkReadTruncatedToLimit() throws IOException
        {
            // When requesting more chars than the limit allows, the read is capped at remaining
            try (StrictBoundedReader reader = new StrictBoundedReader(new StringReader("hello world"), 5))
            {
                char[] buf = new char[20];
                int n = reader.read(buf, 0, 20);
                assertEquals(5, n);
                assertEquals("hello", new String(buf, 0, n));
            }
        }

        @Test
        public void testSingleReadThrowsAfterLimitReached() throws IOException
        {
            try (StrictBoundedReader reader = new StrictBoundedReader(new StringReader("hello"), 3))
            {
                assertEquals('h', reader.read());
                assertEquals('e', reader.read());
                assertEquals('l', reader.read());
                try
                {
                    var _ = reader.read();
                    fail("Expected LimitExceededException");
                }
                catch (LimitExceededException e)
                {
                    // expected
                }
            }
        }

        @Test
        public void testBulkReadThrowsAfterLimitReached() throws IOException
        {
            try (StrictBoundedReader reader = new StrictBoundedReader(new StringReader("hello world"), 5))
            {
                char[] buf = new char[20];
                var _ = reader.read(buf, 0, 20);
                try
                {
                    var _ = reader.read(buf, 0, 1);
                    fail("Expected LimitExceededException");
                }
                catch (LimitExceededException e)
                {
                    // expected
                }
            }
        }

        @Test
        public void testZeroLimitThrowsImmediately() throws IOException
        {
            try (StrictBoundedReader reader = new StrictBoundedReader(new StringReader("hello"), 0))
            {
                try
                {
                    var _ = reader.read();
                    fail("Expected LimitExceededException");
                }
                catch (LimitExceededException e)
                {
                    // expected
                }
            }
        }

        @Test
        public void testEofReturnedBeforeLimit() throws IOException
        {
            try (StrictBoundedReader reader = new StrictBoundedReader(new StringReader("hi"), 100))
            {
                assertEquals('h', reader.read());
                assertEquals('i', reader.read());
                assertEquals(-1, reader.read());
            }
        }

        @Test
        public void testBulkEofReturnedBeforeLimit() throws IOException
        {
            try (StrictBoundedReader reader = new StrictBoundedReader(new StringReader("hi"), 100))
            {
                char[] buf = new char[10];
                assertEquals(2, reader.read(buf, 0, 10));
                assertEquals(-1, reader.read(buf, 0, 10));
            }
        }

        @Test(expected = NullPointerException.class)
        public void testNullReaderThrows()
        {
            //noinspection DataFlowIssue,resource
            new StrictBoundedReader(null, 10);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testNegativeLimitThrows()
        {
            new StrictBoundedReader(new StringReader("hello"), -1);
        }

        @Test(expected = IOException.class)
        public void testReadAfterCloseThrows() throws IOException
        {
            StrictBoundedReader reader = new StrictBoundedReader(new StringReader("hello"), 10);
            reader.close();
            var _ = reader.read();
        }

        @Test
        public void testDoubleCloseIsIdempotent() throws IOException
        {
            StrictBoundedReader reader = new StrictBoundedReader(new StringReader("hello"), 10);
            reader.close();
            reader.close(); // should not throw
        }
    }
}
