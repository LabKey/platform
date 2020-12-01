package org.labkey.api.dataiterator;

import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.Crypt;

import java.util.Set;
import java.util.function.Supplier;

/*
 * This iterator should be put as close as possible to the source data
 * e.g. before any coercion or missing value handling
 */
public class HashDataIterator extends SimpleTranslator
{
    // The row_number is special because of its index==0.  We don't have room for second special index so we'll use a special name.
    // We could use a crazy name like "/*~~DataLoader.InputRowHash~~" + GUID.makeHash()+"*/, but using TransformInputHash is easier
    // NOTE: We don't have visibility to symbol DataIntegrationService.Columns.TransformImportHash
    public static final String HASH_COLUMN_NAME = "diImportHash"; //"DataIntegrationService.Columns.TransformImportHash.getColumnName()";
    public enum Option { generateInputHash }

    final Supplier<Object> inputHashColumn;
    final int outputHashColumnIndex;

    public static class Builder implements DataIteratorBuilder
    {
        final DataIteratorBuilder builder;
        Builder(DataIteratorBuilder builder)
        {
            this.builder = builder;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator in = builder.getDataIterator(context);
            return wrap(in, context);
        }
    }

    public static DataIterator wrap(DataIterator in, DataIteratorContext context)
    {
        // always wrap input data iterator because DataLoader may return HASH_COLUMN_NAME, but it might not be filled in.
        return new HashDataIterator(in, context);
    }

    private HashDataIterator(DataIterator in, DataIteratorContext context)
    {
        super(in, context);
        selectAll(Set.of(HASH_COLUMN_NAME));

        var map = DataIteratorUtil.createColumnNameMap(in);
        Integer I = map.get(HASH_COLUMN_NAME);
        inputHashColumn = null == I ? null : in.getSupplier(I);

        int index;
        if (inputHashColumn != null) // passthrough
            index = addColumn(I);
        else
            index = addColumn(new BaseColumnInfo(HASH_COLUMN_NAME, JdbcType.VARCHAR), (Supplier<String>) ()->null);
        outputHashColumnIndex = index;
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        // compute hash for entire row except the first value (rownumber) and last (hash)
        boolean ret = super.next();
        if (ret)
        {
            if (null == _row[outputHashColumnIndex])
                _row[outputHashColumnIndex] = calculateRowHash(_row,1,outputHashColumnIndex);
        }
        return ret;
    }

    /*
     * Shared implementation, so other iterators can duplicate this functionality (e.g. DataLoader)
     * setRowHash() is like calculateRowHash(), but actally modifies the passed in map.
     */
    public static void setRowHash(ArrayListMap<String,Object> map, boolean skipElementZero, int hashIndex)
    {
        if (null != map.get(hashIndex))
            return;
        Object[] row = new Object[map.getFindMap().size()-(skipElementZero?1:2)];
        int dst=0;
        for (int src=skipElementZero?1:0 ; src<map.size() ; src++)
        {
            if (src!=hashIndex)
                row[dst++] = map.get(src);
        }
        String hashValue = calculateRowHash(row, 0, row.length);
        map.set(hashIndex, hashValue);
    }

    /*
     * Generate and MD5 hash representing these values.  The goal is to be very reproducible,
     * so that is why we assert the list of expected object.  More acceptable objects may be added
     * as needed.
     */
    public static String calculateRowHash(Object[] arr, int start, int length)
    {
        synchronized (sharedDigest)
        {
            sharedDigest.reset();
            for (int i = start; i < length; i++)
            {
                Object v = arr[i];
                assert null == v || v instanceof String || v instanceof Number;
                sharedDigest.update(String.valueOf(v));
            }
            return sharedDigest.base64Digest();
        }
    }

    static final Crypt.StringMessageDigest sharedDigest = new Crypt.StringMessageDigest();
}
