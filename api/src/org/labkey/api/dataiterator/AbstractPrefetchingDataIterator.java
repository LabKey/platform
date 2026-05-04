package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.labkey.api.util.IntegerUtils.asInteger;

/**
 * Abstract base for data iterators that prefetch data in batches using a single primary key column.
 * Provides {@link #buildBatch()} to collect up to 50 rows and their keys into lookup maps,
 * and a standard {@link #next()} implementation that marks, advances, prefetches, and checks errors.
 * <p>
 * Used by {@link SampleUpdateAddColumnsDataIterator} and {@link DataClassUpdateAddColumnsDataIterator}.
 */
public abstract class AbstractPrefetchingDataIterator extends WrapperDataIterator
{
    final DataIteratorContext _context;

    // NOTE: in may be wrapped with a LoggingDataIterator by WrapperDataIterator; _unwrapped retains the original
    final CachingDataIterator _unwrapped;

    final TableInfo target;
    final ColumnInfo pkColumn;
    final Supplier<Object> pkSupplier;

    int lastPrefetchRowNumber = -1;

    /**
     * @param in            the DataIterator to wrap; must be a {@link CachingDataIterator}
     * @param context       the current data iterator context
     * @param target        the target table; used to resolve the key column
     * @param keyColumnName name of the primary key column present in {@code in}
     */
    AbstractPrefetchingDataIterator(CachingDataIterator in, DataIteratorContext context, TableInfo target, String keyColumnName)
    {
        super(in);
        this._unwrapped = in;
        this._context = context;
        this.target = target;

        var map = DataIteratorUtil.createColumnNameMap(in);
        Integer index = map.get(keyColumnName);
        ColumnInfo col = target.getColumn(keyColumnName);
        if (null == index || null == col)
            throw new IllegalArgumentException("Key column not found: " + keyColumnName);
        pkSupplier = in.getSupplier(index);
        pkColumn = col;
    }

    abstract void prefetchExisting() throws BatchValidationException;

    /**
     * Holds the result of collecting a batch of rows for prefetching.
     * <ul>
     *   <li>{@code rowKeyMap} — maps each row number to its key value</li>
     *   <li>{@code keyRowMap} — maps each key value to the list of row numbers that carry it
     *       (normally one row per key, but supports duplicates gracefully)</li>
     * </ul>
     */
    record BatchResult(Map<Integer, Object> rowKeyMap, Map<Object, List<Integer>> keyRowMap) {}

    /**
     * Collects up to 50 rows from the current position into key-lookup maps.
     * Validates that each key value is non-null (numeric keys) or non-blank (string keys).
     * Updates {@link #lastPrefetchRowNumber}.
     * Call {@link #resetAfterBatch()} after processing to rewind the iterator.
     */
    BatchResult buildBatch() throws BatchValidationException
    {
        int rowsToFetch = 50;
        String keyFieldName = pkColumn.getName();
        boolean numericKey = pkColumn.isNumericType();
        JdbcType jdbcType = pkColumn.getJdbcType();
        Map<Integer, Object> rowKeyMap = new LinkedHashMap<>();
        Map<Object, List<Integer>> keyRowMap = new LinkedHashMap<>();
        do
        {
            lastPrefetchRowNumber = asInteger(_delegate.get(0));
            Object keyObj = pkSupplier.get();
            Object key = jdbcType.convert(keyObj);

            if (numericKey)
            {
                if (null == key)
                    throw new IllegalArgumentException(keyFieldName + " value not provided on row " + lastPrefetchRowNumber);
            }
            else if (StringUtils.isEmpty((String) key))
                throw new IllegalArgumentException(keyFieldName + " value not provided on row " + lastPrefetchRowNumber);

            rowKeyMap.put(lastPrefetchRowNumber, key);
            keyRowMap.computeIfAbsent(key, ignored -> new ArrayList<>()).add(lastPrefetchRowNumber);
        }
        while (--rowsToFetch > 0 && _delegate.next());
        return new BatchResult(rowKeyMap, keyRowMap);
    }

    /**
     * Rewinds the iterator to the start of the current batch so the caller can process rows one at a time.
     */
    void resetAfterBatch() throws BatchValidationException
    {
        _unwrapped.reset(); // unwrapped _delegate
        _delegate.next();
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        if (_context.getErrors().hasErrors())
            return false;

        // NOTE: we have to call mark() before we call next() if we want the 'next' row to be cached
        _unwrapped.mark();  // unwrapped _delegate
        boolean ret = super.next();
        if (ret && !_context.getErrors().hasErrors())
        {
            prefetchExisting();
            if (_context.getErrors().hasErrors())
                return false;
        }
        return ret;
    }
}
