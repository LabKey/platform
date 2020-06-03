package org.labkey.api.reader;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;

/**
 * A subclass of {@link BufferedReader} that's specifically designed to work with {@link TabLoader}'s infer fields
 * capability. Our previous approach used {@link BufferedReader#mark(int)} to set a fixed size read-ahead buffer prior
 * to import. BufferedReader allocates the entire buffer upfront and never resizes it, which restricted our ability to
 * infer a large number of rows, since every import incurred the cost of a large buffer. TabBufferedReader starts with
 * a relatively small read-ahead buffer and grows it as needed, the goal being to accommodate inferring and importing
 * very large tables while incurring low memory footprint for smaller tables. This class also adjusts its behavior if
 * the exact size of the input is known, for example, when reading from a file or a CharSequence; in this case, the
 * buffer will never grow larger than the input size.
 *
 * Read-ahead mode is initiated by calling {@link #setReadAhead()} and ended by calling {@link #resetReadAhead()}. The
 * key parameters that control read-ahead buffering are immediately below and can be adjusted to refine the behavior.
 * The read-ahead buffer is initialized to input size or INITIAL_BUFFER_SIZE, whichever is smaller. Before each line is
 * read, the remaining buffer space is checked; if it's smaller than two times the largest row seen so far then the
 * buffer is expanded. The new buffer size is double the previous size, though growth is always limited to
 * MAX_BUFFER_SIZE_INCREMENT and total size is never larger than the smaller of ABSOLUTE_MAX_BUFFER_SIZE or input size.
 * Once created, all existing data are copied over to the new buffer and reading continues.
 *
 * Because {@link BufferedReader}'s buffering details are all private, this class must use reflection to manipulate a
 * handful of buffer-controlling member variables: readAheadLimitField, markedChar, and nextChar.
 */
class TabBufferedReader extends BufferedReader
{
    // All buffer sizes are in characters (which are 2 bytes each)
    private static final int ABSOLUTE_MAX_BUFFER_SIZE = 50*1024*1024;  // Maximum buffer size (unless input size is smaller)
    private static final int MAX_BUFFER_SIZE_INCREMENT = 10*1024*1024; // Largest buffer size increment
    private static final int INITIAL_BUFFER_SIZE = 1024*1024;          // Initial buffer size (unless input size is smaller)

    // Maximum buffer size for this reader. Smaller than ABSOLUTE_MAX_BUFFER_SIZE if input size is known to be smaller.
    private final int _maxBufferSize;
    private final Field _readAheadLimitField;
    private final Field _nextCharField;
    private final Field _markedCharField;

    private boolean _readAhead = false;
    private int _currentBufferSize;
    private int _maxLineLength = 10 * 1024; // Just an initial guess... this will increase as larger rows are read
    private int _markedChar = -1;

    private TabBufferedReader(@NotNull Reader in, int initialBufferSize, int maxBufferSize)
    {
        super(in, initialBufferSize);
        _currentBufferSize = initialBufferSize;
        _maxBufferSize = maxBufferSize;

        try
        {
            _readAheadLimitField = BufferedReader.class.getDeclaredField("readAheadLimit");
            _readAheadLimitField.setAccessible(true);
            _nextCharField = BufferedReader.class.getDeclaredField("nextChar");
            _nextCharField.setAccessible(true);
            _markedCharField = BufferedReader.class.getDeclaredField("markedChar");
            _markedCharField.setAccessible(true);
        }
        catch (NoSuchFieldException e)
        {
            throw new RuntimeException(e);
        }
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

    public void setReadAhead() throws IOException
    {
        mark(_currentBufferSize);
        _readAhead = true;
        try
        {
            _markedChar = _markedCharField.getInt(this);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void resetReadAhead() throws IOException
    {
        reset();
        _readAhead = false;
        _markedChar = -1;
    }

    @Override
    public String readLine() throws IOException
    {
        if (!_readAhead || ensureBuffer())
        {
            String line = super.readLine();

            if (null != line && line.length() > _maxLineLength)
                _maxLineLength = line.length();

            return line;
        }

        return null;
    }

    // Try to ensure enough room in the buffer for the line that's about to be read. We don't know how long that line
    // is, so just verify that there's enough buffer space for double the longest line seen so far; if not, attempt to
    // expand the buffer. Return false if we can't expand the buffer any more; in that case, caller must stop reading.
    private boolean ensureBuffer()
    {
        try
        {
            int delta = _nextCharField.getInt(this) - _markedChar + (_maxLineLength * 2);
            if (delta >= _currentBufferSize)
            {
                // Double the buffer size, but never increase by more than MAX_BUFFER_SIZE_INCREMENT
                int targetBufferSize = Math.min(_currentBufferSize * 2, _currentBufferSize + MAX_BUFFER_SIZE_INCREMENT);
                // And always stay at or below _maxBufferSize
                int newBufferSize = Math.min(targetBufferSize, _maxBufferSize);

                // If we haven't increased enough to accommodate a new line then bail out
                if (newBufferSize < _currentBufferSize + (_maxLineLength * 2))
                    return false;

                _currentBufferSize = newBufferSize;
                _readAheadLimitField.set(this, _currentBufferSize);
            }
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }

        return true;
    }
}
