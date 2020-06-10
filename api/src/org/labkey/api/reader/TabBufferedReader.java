package org.labkey.api.reader;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;

/**
 * A subclass of {@link BufferedReader} that's specifically designed to work with {@link TabLoader}'s infer fields
 * capability. Our previous approach used {@link BufferedReader#mark(int)} to set a fixed size read-ahead buffer prior
 * to import. java.io.BufferedReader allocates the entire buffer upfront and never resizes it, which restricted our ability to
 * infer a large number of rows, since every import incurred the cost of a large buffer. TabBufferedReader uses an alternate
 * implementation that starts with
 * a relatively small read-ahead buffer and grows it as needed, the goal being to accommodate inferring and importing
 * very large tables while incurring low memory footprint for smaller tables. This class also adjusts its behavior if
 * the exact size of the input is known, for example, when reading from a file or a CharSequence; in this case, the
 * buffer will never grow larger than the input size.
 * <p>
 * Read-ahead mode is initiated by calling {@link #setReadAhead()} and ended by calling {@link #resetReadAhead()}. The
 * key parameters that control read-ahead buffering are immediately below and can be adjusted to refine the behavior.
 * The read-ahead buffer is initialized to input size or INITIAL_BUFFER_SIZE, whichever is smaller. Before each line is
 * read, the remaining buffer space is checked; if it's smaller than two times the largest row seen so far then the
 * buffer is expanded. The new buffer size is double the previous size, though growth is always limited to
 * MAX_BUFFER_SIZE_INCREMENT and total size is never larger than the smaller of ABSOLUTE_MAX_BUFFER_SIZE or input size.
 * Once created, all existing data are copied over to the new buffer and reading continues.
 */
class TabBufferedReader extends org.labkey.api.reader.BufferedReader
{
    // All buffer sizes are in characters (which are 2 bytes each)
    private static final int ABSOLUTE_MAX_BUFFER_SIZE = 50*1024*1024;  // Maximum buffer size (unless input size is smaller)
    private static final int INITIAL_BUFFER_SIZE = 32*1024;            // Initial buffer size (unless input size is smaller)

    // Maximum buffer size for this reader. Smaller than ABSOLUTE_MAX_BUFFER_SIZE if input size is known to be smaller.
    private final int _maxBufferSize;

    private TabBufferedReader(@NotNull Reader in, int initialBufferSize, int maxBufferSize)
    {
        super(in, initialBufferSize);
        _maxBufferSize = maxBufferSize;
    }

    public TabBufferedReader(@NotNull Reader in, long exactCharacterCount)
    {
        this(in, getInitialBufferSize(exactCharacterCount), getMaxBufferSize(exactCharacterCount));
    }

    public TabBufferedReader(@NotNull Reader in)
    {
        this(in, INITIAL_BUFFER_SIZE, ABSOLUTE_MAX_BUFFER_SIZE);
    }

    private static int getInitialBufferSize(long exactCharacterCount)
    {
        return (int)Math.min(exactCharacterCount, INITIAL_BUFFER_SIZE);
    }

    private static int getMaxBufferSize(long exactCharacterCount)
    {
        return (int)Math.min(exactCharacterCount, ABSOLUTE_MAX_BUFFER_SIZE);
    }

    /*
     * NOTE the behavior of setReadAhead() is different in from Reader.mark()
     * in that it prevents over-running the readAheadLimit.  It is also
     * different from commons.io.input.BoundedReader in that readLine()
     * will not arbitrarily truncate a line in the middle.  You'll get the
     * whole line or none of it.
     */
    public void setReadAhead() throws IOException
    {
        mark(_maxBufferSize);
        setEnforceMarkLimit(true);
    }

    public void resetReadAhead() throws IOException
    {
        reset();
        // NOTE reset doesn't actually remove the mark , so we have call setEnforceMarkLimit(false)
        // could also implement super.removeMark()
        setEnforceMarkLimit(false);
    }

    @Override
    public String readLine() throws IOException
    {
        try
        {
            return super.readLine();
        }
        catch (MarkLimitExceededException x)
        {
            return null;
        }
    }
}
