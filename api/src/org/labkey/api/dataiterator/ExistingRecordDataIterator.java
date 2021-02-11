package org.labkey.api.dataiterator;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.labkey.api.gwt.client.AuditBehaviorType.DETAILED;

public class ExistingRecordDataIterator extends WrapperDataIterator
{
    public static final String EXISTING_RECORD_COLUMN_NAME = "_" + ExistingRecordDataIterator.class.getName() + "#EXISTING_RECORD_COLUMN_NAME";

    Map<String,Object> currentExistingRecord = null;

    final TableInfo target;
    final ArrayList<ColumnInfo> pkColumns = new ArrayList<>();
    final ArrayList<Supplier<Object>> pkSuppliers = new ArrayList<>();

    final int existingColIndex;

    ExistingRecordDataIterator(DataIterator in, TableInfo target, @Nullable Set<String> keys)
    {
        super(in);
        this.target = target;
        this.existingColIndex = in.getColumnCount()+1;

        var map = DataIteratorUtil.createColumnNameMap(in);
        Collection<String> keyNames = null==keys ? target.getPkColumnNames() : keys;
        for (String name : keyNames)
        {
            Integer index = map.get(name);
            ColumnInfo col = target.getColumn(name);
            if (null == index || null == col)
            {
                pkSuppliers.clear();
                pkColumns.clear();
                throw new IllegalStateException("Key column not found: " + name);
            }
            pkSuppliers.add(in.getSupplier(index));
            pkColumns.add(col);
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
        if (pkColumns.size() > 1)
            throw new UnsupportedOperationException("NYI");
        currentExistingRecord = null;
        boolean ret = super.next();
        if (ret && !pkColumns.isEmpty())
        {
            SimpleFilter filter = new SimpleFilter();
            for (int i=0 ; i<pkColumns.size() ; i++)
                filter.addClause(new CompareType.EqualsCompareClause(pkColumns.get(i).getFieldKey(), CompareType.EQUAL, pkSuppliers.get(i).get()));
            Map<String,Object> record = new TableSelector(target, TableSelector.ALL_COLUMNS, filter, null).getMap();
            currentExistingRecord = null==record ? Map.of() : record;
        }
        return ret;
    }


    @Override
    public boolean supportsGetExistingRecord()
    {
        return !pkColumns.isEmpty();
    }


    @Override
    public Map<String, Object> getExistingRecord()
    {
        return currentExistingRecord;
    }


    public static DataIteratorBuilder createBuilder(DataIteratorBuilder dib, TableInfo target, @Nullable Set<String> keys)
    {
        return context ->
        {
            DataIterator di = dib.getDataIterator(context);
            assert !di.supportsGetExistingRecord();
            if (di.supportsGetExistingRecord())
                return di;
            QueryUpdateService.InsertOption option = context.getInsertOption();
            if (option.mergeRows)
            {
                AuditBehaviorType auditType = AuditBehaviorType.NONE;
                if (target.supportsAuditTracking())
                    auditType = target.getAuditBehavior((AuditBehaviorType) context.getConfigParameter(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior));
                if (auditType == DETAILED)
                    return new ExistingRecordDataIterator(di, target, keys);
            }
            return di;
        };
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
            DataIterator existing = new ExistingRecordDataIterator(di, CoreSchema.getInstance().getTableInfoModules(), null);
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
                    assertNotNull(record);
                    assertTrue(record.isEmpty());
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
