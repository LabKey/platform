package org.labkey.api.dataiterator;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;


public class GenerateUniqueDataIterator<V> extends WrapperDataIterator
{
    final TableInfo target;
    final ColumnInfo column;
    final Class<V> clazz;
    final Function<Integer,List<V>> supplier;
    final int existingColCount;
    final int batchSize = 50;

    // NOTE: WrappedDataIterator._delegate may be a logger so keep the original dataiterator
    final CachingDataIterator cacheIterator;

    // validated unique values
    final LinkedHashSet<V> uniqueValues = new LinkedHashSet<>();
    V currentUniqueValue = null;

    GenerateUniqueDataIterator(CachingDataIterator in, TableInfo target, ColumnInfo column, Class<V> clazz, Function<Integer,List<V>> supplier)
    {
        super(in);
        // make sure we're not wrapping delegate with a logger
        cacheIterator = in;

        this.target = target;
        this.column = column;
        this.clazz = clazz;
        this.supplier = supplier;
        this.existingColCount = in.getColumnCount();
    }

    @Override
    public int getColumnCount()
    {
        return _delegate.getColumnCount()+1;
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        if (i < existingColCount)
            return _delegate.getColumnInfo(i);
        return column;
    }

    @Override
    public Supplier<Object> getSupplier(int i)
    {
        if (i < existingColCount)
            return _delegate.getSupplier(i);
        return () -> get(i);
    }

    @Override
    public Object get(int i)
    {
        if (i < existingColCount)
            return _delegate.get(i);

        if (null == currentUniqueValue)
        {
            var it = uniqueValues.iterator();
            currentUniqueValue = it.next();
            it.remove();
        }
        return currentUniqueValue;
    }

    @Override
    public boolean isConstant(int i)
    {
        if (i < existingColCount)
            return _delegate.isConstant(i);
        return false;
    }

    @Override
    public Object getConstantValue(int i)
    {
        if (i < existingColCount)
            return _delegate.getConstantValue(i);
        return null;
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        currentUniqueValue = null;
        // NOTE: we have to call mark() before we call next() if we want the 'next' row to be cached
        cacheIterator.mark();
        boolean ret = super.next();
        if (ret)
            generateUniqueValues();
        return ret;
    }

    private List<V> getValidatedList(List<V> proposedValues)
    {
        SQLFragment sqlf = new SQLFragment("WITH _values_ AS (\nSELECT * FROM (VALUES \n");
        String comma = "";
        for (V v : proposedValues)
        {
            sqlf.append(comma).append("(?)");
            sqlf.add(v);
            comma = "\n,";
        }
        sqlf.append("\n) AS _x (value))\n");

        sqlf.append("SELECT _values_.value\n");
        sqlf.append("FROM _values_ LEFT OUTER JOIN ").append(target.getFromSQL("_target_")).append(" ON ");
        sqlf.append("(_values_.value").append("=(").append(column.getValueSql("_target_")).append("))\n");
        sqlf.append("WHERE (").append(column.getValueSql("_target_")).append(") IS NULL");

        return new SqlSelector(target.getSchema(), sqlf).getArrayList(clazz);
    }

    protected void generateUniqueValues() throws BatchValidationException
    {
        if (!uniqueValues.isEmpty())
            return;

        int count = 1;
        for (int i=0 ; i<batchSize ; i++)
        {
            if (!_delegate.next())
                break;
            count++;
        }

        while (uniqueValues.isEmpty())
        {
            List<V> list = supplier.apply(count);
            List<V> validated = getValidatedList(list);
            uniqueValues.addAll(validated);
        }
        // backup to where we started so caller can iterate through them one at a time
        cacheIterator.reset();
        _delegate.next();
    }

    /**
     * NOTE: the caller must set the container filter for target appropriately.
     * e.g. for globally unique constraint the target must have containerFilter already set to ContainerFilter.EVERYTHING
     */
    public static <K> DataIteratorBuilder createBuilder(DataIteratorBuilder dib, TableInfo target, ColumnInfo column, Function<Integer,List<K>> supplier)
    {
        return context ->
        {
            DataIterator di = dib.getDataIterator(context);

            QueryUpdateService.InsertOption option = context.getInsertOption();
            // TODO behavior for merge/replace???

            CachingDataIterator cache = new CachingDataIterator(di);
            Class<K> clazz = column.getJdbcType().getJavaClass();
            return new GenerateUniqueDataIterator<>(cache, target, column, clazz, supplier);
        };
    }



    public static class TestCase extends Assert
    {
        private DataIterator make100Rows()
        {
            ArrayList<Map<String,Object>> maps = new ArrayList<>();
            for (int i=0 ; i<100 ; i++)
                maps.add(CaseInsensitiveHashMap.of("key",i));
            return new ListofMapsDataIterator(Set.of("key"), maps);
        }

        AtomicInteger valueGenerator = new AtomicInteger();
        List<Integer> supply(Integer n)
        {
            ArrayList<Integer> ret = new ArrayList<>(n);
            for (int i=0 ; i<n ; i++)
                ret.add(valueGenerator.incrementAndGet());
            return ret;
        }

        @Test
        public void testInsert() throws Exception
        {
            // get set<> of current container rowids
            var setExisting = new TreeSet<Integer>();
            new SqlSelector(CoreSchema.getInstance().getSchema(), "SELECT rowid from core.containers").fillSet(setExisting);
            valueGenerator.set(setExisting.first());

            // the generate unique dataiterator and make sure there are no collisions
            TableInfo t = Objects.requireNonNull(CoreSchema.getInstance().getSchema().getTable("containers"));
            ColumnInfo c = Objects.requireNonNull(t.getColumn("rowid"));
            DataIterator generate = GenerateUniqueDataIterator.createBuilder(make100Rows(),t,c, this::supply).getDataIterator(new DataIteratorContext());

            var value = generate.getSupplier(generate.getColumnCount());
            var setGenerated = new TreeSet<Integer>();
            int mn=1000000, mx=0;
            while (generate.next())
            {
                Integer I = (Integer)value.get();
                if (I < mn) mn = I;
                if (I > mx) mx = I;
                setGenerated.add(I);
            }
            // shouldn't be collisions
            assertFalse(setGenerated.removeAll(setExisting));
            // shouldn't be gaps either
            for (int i=mn ; i<=mx ; i++)
                assertTrue(setGenerated.contains(i) || setExisting.contains(i));
        }
    }
}
