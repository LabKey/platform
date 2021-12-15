package org.labkey.api.dataiterator;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.labkey.api.gwt.client.AuditBehaviorType.DETAILED;

public abstract class ExistingRecordDataIterator extends WrapperDataIterator
{
    public static final String EXISTING_RECORD_COLUMN_NAME = "_" + ExistingRecordDataIterator.class.getName() + "#EXISTING_RECORD_COLUMN_NAME";

    final CachingDataIterator _unwrapped;
    final TableInfo target;
    final ArrayList<ColumnInfo> pkColumns = new ArrayList<>();
    final ArrayList<Supplier<Object>> pkSuppliers = new ArrayList<>();
    final int existingColIndex;

    // prefetch of existing records
    final boolean useMark;
    int lastPrefetchRowNumber = -1;
    final HashMap<Integer,Map<String,Object>> existingRecords = new HashMap<>();

    ExistingRecordDataIterator(DataIterator in, TableInfo target, @Nullable Set<String> keys, boolean useMark)
    {
        super(in);

        // NOTE it might get wrapped with a LoggingDataIterator, so remember the original DataIterator
        this._unwrapped = useMark ? (CachingDataIterator)in : null;

        this.target = target;
        this.existingColIndex = in.getColumnCount()+1;
        this.useMark = useMark;

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
        return () -> get(i);
    }

    @Override
    public Object get(int i)
    {
        if (i<existingColIndex)
            return _delegate.get(i);
        Integer rowNumber = (Integer)_delegate.get(0);
        Map<String,Object> existingRow = existingRecords.get(rowNumber);
        assert null != existingRow;
        return existingRow;
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

    abstract void prefetchExisting() throws BatchValidationException;


    @Override
    public boolean next() throws BatchValidationException
    {
        // NOTE: we have to call mark() before we call next() if we want the 'next' row to be cached
        if (useMark)
            _unwrapped.mark();  // unwrapped _delegate
        boolean ret = super.next();
        if (ret && !pkColumns.isEmpty())
            prefetchExisting();
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
        return (Map<String,Object>)get(existingColIndex);
    }



    public static DataIteratorBuilder createBuilder(DataIteratorBuilder dib, TableInfo target, @Nullable Set<String> keys)
    {
        return createBuilder(dib, target, keys, false);
    }


    public static DataIteratorBuilder createBuilder(DataIteratorBuilder dib, TableInfo target, @Nullable Set<String> keys, boolean useGetRows)
    {
        return context ->
        {
            DataIterator di = dib.getDataIterator(context);
            if (null == di)
                return null;           // Can happen if context has errors

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
                {
                    if (useGetRows)
                        return new ExistingDataIteratorsGetRows(new CachingDataIterator(di), target, keys);
                    else
                        return new ExistingDataIteratorsTableInfo(new CachingDataIterator(di), target, keys);
                }
            }
            return di;
        };
    }


    /* Select using normal TableInfo stuff */
    static class ExistingDataIteratorsTableInfo extends ExistingRecordDataIterator
    {
        ExistingDataIteratorsTableInfo(CachingDataIterator in, TableInfo target, @Nullable Set<String> keys)
        {
            super(in, target, keys, true);
        }

        private SQLFragment getSelectExistingSql(int rows) throws BatchValidationException
        {
            SQLFragment sqlf = new SQLFragment("WITH _key_columns_ AS (\nSELECT * FROM (VALUES \n");
            String comma = "";
            do
            {
                lastPrefetchRowNumber = (Integer) _delegate.get(0);
                sqlf.append(comma).append("(").append(lastPrefetchRowNumber);
                comma = "\n,";
                for (int p = 0; p < pkColumns.size(); p++)
                {
                    sqlf.append(",?");
                    sqlf.add(pkSuppliers.get(p).get());
                }
                sqlf.append(")");
            }
            while (--rows > 0 && _delegate.next());

            sqlf.append("\n) AS _values_ (_row_number_");
            for (int p = 0; p < pkColumns.size(); p++)
                sqlf.append(",").append("key").append(p);
            sqlf.append("))\n");

            sqlf.append("SELECT _key_columns_._row_number_, _target_.* FROM ");
            sqlf.append("_key_columns_ INNER JOIN ");
            sqlf.append(target.getFromSQL("_target_ "));
            sqlf.append(" ON ");
            String and = "";
            for (int p = 0; p < pkColumns.size(); p++)
            {
                sqlf.append(and);
                sqlf.append("(_key_columns_.key").append(p).append("=(").append(pkColumns.get(p).getValueSql("_target_")).append("))");
                and = " AND ";
            }
            return sqlf;
        }

        protected void prefetchExisting() throws BatchValidationException
        {
            Integer rowNumber = (Integer)_delegate.get(0);
            if (rowNumber <= lastPrefetchRowNumber)
                return;

            // fetch N new rows into the existingRecords map
            SQLFragment select = getSelectExistingSql(50);
            var list = new SqlSelector(target.getSchema(), select).getArrayList(Map.class);
            existingRecords.clear();
            for (int r=rowNumber ; r<=lastPrefetchRowNumber ;r++)
                existingRecords.put(r,Map.of());
            list.forEach(map -> {
                int r = (Integer)map.get("_row_number_");
                map.remove("_row_number_");
                map.remove("_row"); // I think CachedResultSet adds "_row"
                existingRecords.put(r,(Map<String,Object>)map);
            });
            // backup to where we started so caller can iterate through them one at a time
            _unwrapped.reset(); // unwrapped _delegate
            _delegate.next();
        }
    }


    /* If you want to fetch your existing records the hard way */
    static class ExistingDataIteratorsGetRows extends ExistingRecordDataIterator
    {
        final QueryUpdateService qus;
        final User user;
        final Container c;

        ExistingDataIteratorsGetRows(CachingDataIterator in, TableInfo target, @Nullable Set<String> keys)
        {
            super(in, target, keys, true);
            qus = target.getUpdateService();
            user = target.getUserSchema().getUser();
            c = target.getUserSchema().getContainer();
        }

        @Override
        protected void prefetchExisting() throws BatchValidationException
        {
            try
            {
                Integer rowNumber = (Integer)_delegate.get(0);
                if (rowNumber <= lastPrefetchRowNumber)
                    return;

                existingRecords.clear();

                int rowsToFetch = 50;
                Map<Integer, Map<String,Object>> keysMap = new LinkedHashMap<>();
                do
                {
                    lastPrefetchRowNumber = (Integer) _delegate.get(0);
                    Map<String,Object> keyMap = CaseInsensitiveHashMap.of();
                    for (int p=0 ; p<pkColumns.size() ; p++)
                        keyMap.put(pkColumns.get(p).getColumnName(), pkSuppliers.get(p).get());
                    keysMap.put(lastPrefetchRowNumber, keyMap);
                    existingRecords.put(lastPrefetchRowNumber, Map.of());
                }
                while (--rowsToFetch > 0 && _delegate.next());

                Map<Integer, Map<String, Object>> rowsMap = qus.getExistingRows(user, c, keysMap);
                for (Map.Entry<Integer, Map<String, Object>> rowMap : rowsMap.entrySet())
                {
                    Map<String, Object> map = rowMap.getValue();
                    Map<String,Object> existing = map == null || map.isEmpty() ? Map.of() : map;
                    existingRecords.put(rowMap.getKey(), existing);
                }

                // backup to where we started so caller can iterate through them one at a time
                _unwrapped.reset(); // unwrapped _delegate
                _delegate.next();
            }
            catch (SQLException sqlx)
            {
                throw new RuntimeSQLException(sqlx);
            }
            catch (QueryUpdateServiceException|InvalidKeyException x)
            {
                throw UnexpectedException.wrap(x);
            }
        }
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
            DataIterator existing = new ExistingDataIteratorsTableInfo(new CachingDataIterator(di), CoreSchema.getInstance().getTableInfoModules(), null);
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
