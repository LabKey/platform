package org.labkey.api.dataiterator;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ExistingRecordDataIterator extends WrapperDataIterator
{
    public static final String EXISTING_RECORD_COLUMN_NAME = "_" + ExistingRecordDataIterator.class.getName() + "#EXISTING_RECORD_COLUMN_NAME";

    Map<String,Object> currentExistingRecord = null;

    final TableInfo target;
    final ArrayList<Supplier<Object>> pks = new ArrayList<>();
    final ArrayList<Object> pkValues = new ArrayList<>();

    final int existingColIndex;

    ExistingRecordDataIterator(DataIterator in, TableInfo target)
    {
        super(in);
        this.target = target;
        this.existingColIndex = in.getColumnCount()+1;

        var map = DataIteratorUtil.createColumnNameMap(in);
        var keyNames = target.getPkColumnNames();
        for (String name : keyNames)
        {
            Integer index = map.get(name);
            if (null == index)
            {
                pks.clear();
                return;
            }
            pks.add(in.getSupplier(index));
        }
    }

    @Override
    public int getColumnCount()
    {
        return existingColIndex;
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        if (i<existingColIndex)
            return _delegate.getColumnInfo(i);
        return new BaseColumnInfo(EXISTING_RECORD_COLUMN_NAME, JdbcType.OTHER);
    }

    @Override
    public Supplier<Object> getSupplier(int i)
    {
        if (i<existingColIndex)
            return _delegate.getSupplier(i);
        return () -> currentExistingRecord;
    }

    @Override
    public Object get(int i)
    {
        if (i<existingColIndex)
            return _delegate.get(i);
        return currentExistingRecord;
    }

    @Override
    public boolean isConstant(int i)
    {
        if (i<existingColIndex)
            return _delegate.isConstant(i);
        return false;
    }

    @Override
    public Object getConstantValue(int i)
    {
        if (i<existingColIndex)
            return _delegate.getConstantValue(i);
        return null;
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        if (pkValues.size() > 1)
            throw new UnsupportedOperationException("NYI");
        currentExistingRecord = null;
        boolean ret = super.next();
        if (ret && !pks.isEmpty())
        {
            pkValues.clear();
            for (var s : pks)
                pkValues.add(s.get());
            currentExistingRecord = new TableSelector(target, TableSelector.ALL_COLUMNS, null, null).getMap(pkValues.get(0));
        }
        return ret;
    }


    @Override
    public boolean supportsGetExistingRecord()
    {
        return !pks.isEmpty();
    }


    @Override
    public Map<String, Object> getExistingRecord()
    {
        return currentExistingRecord;
    }


    public static class TestCase extends Assert
    {
        private DataIterator makeModulesDI()
        {
            Set<String> nameSet = ModuleLoader.getInstance().getModules().stream().map(Module::getName)
                    .collect(Collectors.toSet());
            ArrayList<Map<String,Object>> namesArrayList = nameSet.stream().map(name -> CaseInsensitiveHashMap.of("name", (Object)name))
                    .collect(Collectors.toCollection(ArrayList::new));
            namesArrayList.add(CaseInsensitiveHashMap.of("name","NO_SUCH_MODULE"));

            DataIterator di = new ListofMapsDataIterator(Set.of("Name"), namesArrayList);
            assertFalse(di.supportsGetExistingRecord());
            DataIterator existing = new ExistingRecordDataIterator(di, CoreSchema.getInstance().getTableInfoModules());
            assertTrue(existing.supportsGetExistingRecord());
            return existing;
        }

        private void validateModulesDI(DataIterator existing) throws Exception
        {
            while (existing.next())
            {
                String name = (String)existing.get(1);
                assertNotNull(name);
                Map<String,Object> record = existing.getExistingRecord();
                if ("NO_SUCH_MODULE".equals(name))
                {
                    assertNull(record);
                }
                else
                {
                    assertNotNull(record);
                    assertTrue(name.equalsIgnoreCase((String)record.get("name")));
                }
            }
        }


        @Test
        public void testSinglePK() throws Exception
        {
            DataIterator existing = makeModulesDI();
            assertTrue(existing.supportsGetExistingRecord());
            validateModulesDI(existing);

            DataIterator logging = LoggingDataIterator.wrap(makeModulesDI());
            assertTrue(logging.supportsGetExistingRecord());
            validateModulesDI(logging);

            DataIterator caching = new CachingDataIterator(makeModulesDI());
            assertTrue(caching.supportsGetExistingRecord());
            validateModulesDI(caching);
        }
    }
}
