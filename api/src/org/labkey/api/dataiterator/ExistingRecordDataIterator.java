package org.labkey.api.dataiterator;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.labkey.api.gwt.client.AuditBehaviorType.DETAILED;
import static org.labkey.api.util.IntegerUtils.asInteger;

public abstract class ExistingRecordDataIterator extends WrapperDataIterator
{
    public static final String EXISTING_RECORD_COLUMN_NAME = "_" + ExistingRecordDataIterator.class.getName() + "#EXISTING_RECORD_COLUMN_NAME";

    final CachingDataIterator _unwrapped;
    final TableInfo target;
    final ArrayList<ColumnInfo> pkColumns = new ArrayList<>();
    final ArrayList<Supplier<Object>> pkSuppliers = new ArrayList<>();
    final int existingColIndex;
    final Integer containerCol;

    // prefetch of existing records
    final boolean useMark;
    int lastPrefetchRowNumber = -1;
    final HashMap<Integer,Map<String,Object>> existingRecords = new IntHashMap<>();
    final Set<String> _dataColumnNames = new CaseInsensitiveHashSet();

    final User user;
    final Container c;
    final boolean _checkCrossFolderData;
    final boolean _verifyExisting;

    final Set<String> _sharedKeys = new CaseInsensitiveHashSet(); // common keys, such as data.classId, material.MaterialSourceId
    final Set<Object> _pkKeysSeen = new HashSet<>();

    final DataIteratorContext _context;

    ExistingRecordDataIterator(DataIterator in, TableInfo target, @Nullable Set<String> keys, @Nullable Set<String> sharedKeys, boolean useMark, DataIteratorContext context, boolean detailed)
    {
        super(in);
        _context = context;

        QueryUpdateService.InsertOption option = context.getInsertOption();

        // NOTE it might get wrapped with a LoggingDataIterator, so remember the original DataIterator
        this._unwrapped = useMark ? (CachingDataIterator)in : null;

        this.target = target;
        this.existingColIndex = in.getColumnCount()+1;
        this.useMark = useMark;

        UserSchema userSchema = target.getUserSchema();
        user = userSchema != null ? userSchema.getUser() : null;
        c = userSchema != null ? userSchema.getContainer() : null;
        _checkCrossFolderData = context.getConfigParameterBoolean(QueryUpdateService.ConfigParameters.CheckForCrossProjectData);
        _verifyExisting = option.updateOnly;

        var map = DataIteratorUtil.createColumnNameMap(in);
        containerCol = map.get("Container");

        Set<String> keyNames = new CaseInsensitiveHashSet();
        if (keys == null)
            keyNames.addAll(target.getPkColumnNames());
        else
            keyNames.addAll(keys);

        if (sharedKeys != null)
            _sharedKeys.addAll(sharedKeys);

        if (detailed)
            _dataColumnNames.addAll(map.keySet());

        for (String name : keyNames)
        {
            if (!map.containsKey(name))
                continue;

            Integer index = map.get(name);
            ColumnInfo col = target.getColumn(name);
            if (null == index || null == col)
            {
                pkSuppliers.clear();
                pkColumns.clear();
                throw new IllegalArgumentException("Key column not found: " + name);
            }
            pkSuppliers.add(in.getSupplier(index));
            pkColumns.add(col);
            _dataColumnNames.add(name);
        }

        if (pkColumns.isEmpty())
            throw new IllegalArgumentException("At least one key column is required.");
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
        Integer rowNumber = asInteger(_delegate.get(0));
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
        if (_context.getErrors().hasErrors())
            return false;

        // NOTE: we have to call mark() before we call next() if we want the 'next' row to be cached
        if (useMark)
            _unwrapped.mark();  // unwrapped _delegate
        boolean ret = super.next();
        if (!_context.getErrors().hasErrors() && ret && !pkColumns.isEmpty())
        {
            prefetchExisting();
            if (_context.shouldCancel())
                return false;
        }
        return ret;
    }

    protected void checkDuplicateKeys(List<String> pkKeys)
    {
        Object pkKeysObj = pkKeys.size() == 1 ? pkKeys.get(0) : pkKeys;
        if (_pkKeysSeen.contains(pkKeysObj))
            _context.getErrors().addRowError(new ValidationException("Duplicate key provided: " + StringUtils.join(pkKeys, ", ")));
        _pkKeysSeen.add(pkKeysObj);
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
        return createBuilder(dib, target, keys, null, false);
    }

    public static DataIteratorBuilder createBuilder(DataIteratorBuilder dib, TableInfo target, @Nullable Set<String> keys, @Nullable Set<String> sharedKeys, boolean useGetRows)
    {
        return context ->
        {
            DataIterator di = dib.getDataIterator(context);
            if (null == di)
                return null;           // Can happen if context has errors

            if (di.supportsGetExistingRecord())
                return di;
            QueryUpdateService.InsertOption option = context.getInsertOption();
            if (option.allowUpdate)
            {
                boolean hasAttachmentProperties = false;
                QueryUpdateService qus = target.getUpdateService();
                if (qus instanceof DefaultQueryUpdateService dQus)
                    hasAttachmentProperties = dQus.hasAttachmentProperties(); // if true, we need to fetch existing records to properly handle old attachment delete
                AuditBehaviorType auditType = AuditBehaviorType.NONE;
                if (target.supportsAuditTracking())
                    auditType = target.getEffectiveAuditBehavior((AuditBehaviorType) context.getConfigParameter(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior));
                boolean detailed = auditType == DETAILED || hasAttachmentProperties;
                if (useGetRows)
                    return new ExistingDataIteratorsGetRows(new CachingDataIterator(di), target, keys, sharedKeys, context, detailed);
                else
                    return new ExistingDataIteratorsTableInfo(new CachingDataIterator(di), target, keys, sharedKeys, context, detailed);
            }
            return di;
        };
    }


    /* Select using normal TableInfo stuff */
    static class ExistingDataIteratorsTableInfo extends ExistingRecordDataIterator
    {
        final Set<String> allowedContainers = new HashSet<>();

        ExistingDataIteratorsTableInfo(CachingDataIterator in, TableInfo target, @Nullable Set<String> keys, @Nullable Set<String> sharedKeys, DataIteratorContext context, boolean detailed)
        {
            super(in, target, keys, sharedKeys, true, context, detailed);
            if (c != null)
                allowedContainers.add(c.getId());
        }

        private Pair<SQLFragment, Map<Integer, String>> getSelectExistingSql(int rows) throws BatchValidationException
        {
            SQLFragment sqlf = new SQLFragment("WITH _key_columns_ AS (\nSELECT * FROM (VALUES \n");
            String comma = "";
            Map<Integer, String> rowNumContainers = new IntHashMap<>();
            String container;
            do
            {
                container = null;
                lastPrefetchRowNumber = asInteger(_delegate.get(0));
                if (containerCol != null)
                {
                    Object containerObj = _delegate.get(containerCol);
                    if (containerObj != null)
                        container = (String) containerObj;
                }

                sqlf.append(comma).append("(").appendValue(lastPrefetchRowNumber);
                comma = "\n,";
                List<String> pkKeys = new ArrayList<>();
                for (int p = 0; p < pkColumns.size(); p++)
                {
                    sqlf.append(",?");
                    Object pkVal = pkSuppliers.get(p).get();

                    // Issue 52922: Rows with blank key in the file are getting ignored in update from file
                    if (pkVal == null)
                    {
                        _context.getErrors().addRowError(new ValidationException(pkColumns.get(p).getColumnName() + " value not provided on row " + lastPrefetchRowNumber));
                        continue;
                    }

                    sqlf.add(pkVal);
                    if (!_sharedKeys.contains(pkColumns.get(p).getColumnName()))
                        pkKeys.add(pkVal.toString());
                }
                sqlf.append(")");
                checkDuplicateKeys(pkKeys);
                rowNumContainers.put(lastPrefetchRowNumber, container);
            }
            while (--rows > 0 && _delegate.next());

            sqlf.append("\n) AS _values_ (_row_number_");
            for (int p = 0; p < pkColumns.size(); p++)
                sqlf.append(",").append("key").appendValue(p);
            sqlf.append("))\n");

            sqlf.append("SELECT _key_columns_._row_number_, _target_.* FROM ");
            sqlf.append("_key_columns_ INNER JOIN ");
            sqlf.append(target.getFromSQL("_target_ "));
            sqlf.append(" ON ");
            String and = "";
            for (int p = 0; p < pkColumns.size(); p++)
            {
                sqlf.append(and);
                sqlf.append("(_key_columns_.key").appendValue(p).append("=(").append(pkColumns.get(p).getValueSql("_target_")).append("))");
                and = " AND ";
            }
            return new Pair<>(sqlf, rowNumContainers);
        }

        @Override
        protected void prefetchExisting() throws BatchValidationException
        {
            Integer rowNumber = asInteger(_delegate.get(0));
            if (rowNumber <= lastPrefetchRowNumber)
                return;

            // fetch N new rows into the existingRecords map
            Pair<SQLFragment, Map<Integer, String>> selectRowsSql = getSelectExistingSql(50);

            SQLFragment select = selectRowsSql.first;
            Map<Integer, String> rowNumContainers = selectRowsSql.second;
            var list = new SqlSelector(target.getSchema(), select, QueryLogging.noValidationNeededQueryLogging()).getArrayList(Map.class);
            existingRecords.clear();
            for (int r=rowNumber ; r<=lastPrefetchRowNumber ;r++)
                existingRecords.put(r,Map.of());

            for (Map map : list)
            {
                Integer r = asInteger(map.get("_row_number_"));

                if (_verifyExisting)
                {
                    String existingContainerId = null;
                    if (map.containsKey("container"))
                        existingContainerId = (String) map.getOrDefault("container", "");
                    else if (map.containsKey("folder"))
                        existingContainerId = (String) map.getOrDefault("folder", "");

                    if (!StringUtils.isEmpty(existingContainerId))
                    {
                        if (!allowedContainers.contains(existingContainerId))
                        {
                            String providedContainer = rowNumContainers.get(r);

                            // if a Container value is provided in the rows, its allowRowMutationForContainer has already been validated in SimpleTranslator.ContainerColumn
                            if (!existingContainerId.equals(providedContainer))
                                _context.getErrors().addRowError(new ValidationException("Data doesn't belong to the current container at row number: " + r));
                        }

                    }
                }

                map.remove("_row_number_");
                map.remove("_row"); // I think CachedResultSet adds "_row"
                existingRecords.put(r,(Map<String,Object>)map);
                rowNumContainers.remove(r);
            }

            if (_verifyExisting && !rowNumContainers.isEmpty())
                _context.getErrors().addRowError(new ValidationException("No record found at row number: " + rowNumContainers.keySet().iterator().next() + "."));

            // backup to where we started so caller can iterate through them one at a time
            _unwrapped.reset(); // unwrapped _delegate
            _delegate.next();
        }
    }


    /* If you want to fetch your existing records the hard way */
    static class ExistingDataIteratorsGetRows extends ExistingRecordDataIterator
    {
        final QueryUpdateService qus;

        ExistingDataIteratorsGetRows(CachingDataIterator in, TableInfo target, @Nullable Set<String> keys, @Nullable Set<String> sharedKeys, DataIteratorContext context, boolean detailed)
        {
            super(in, target, keys, sharedKeys, true, context, detailed);
            qus = target.getUpdateService();
        }

        @Override
        protected void prefetchExisting() throws BatchValidationException
        {
            try
            {
                Integer rowNumber = asInteger(_delegate.get(0));
                if (rowNumber <= lastPrefetchRowNumber)
                    return;

                existingRecords.clear();

                int rowsToFetch = 50;
                Map<Integer, Map<String,Object>> keysMap = new LinkedHashMap<>();
                do
                {
                    lastPrefetchRowNumber = asInteger(_delegate.get(0));
                    Map<String,Object> keyMap = CaseInsensitiveHashMap.of();
                    List<String> pkKeys = new ArrayList<>();
                    for (int p=0 ; p<pkColumns.size() ; p++)
                    {
                        Object pkVal = pkSuppliers.get(p).get();
                        // Issue 52922: Rows with blank key in the file are getting ignored in update from file
                        if (pkVal == null)
                        {
                            _context.getErrors().addRowError(new ValidationException(pkColumns.get(p).getColumnName() + " value not provided on row " + lastPrefetchRowNumber));
                            return;
                        }

                        keyMap.put(pkColumns.get(p).getColumnName(), pkVal);
                        if (!_sharedKeys.contains(pkColumns.get(p).getColumnName()))
                            pkKeys.add(pkVal.toString());
                    }

                    checkDuplicateKeys(pkKeys);

                    keysMap.put(lastPrefetchRowNumber, keyMap);
                    existingRecords.put(lastPrefetchRowNumber, Map.of());
                }
                while (--rowsToFetch > 0 && _delegate.next());

                Map<Integer, Map<String, Object>> rowsMap = qus.getExistingRows(user, c, keysMap, _checkCrossFolderData, _verifyExisting, _dataColumnNames);
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
            catch (InvalidKeyException x)
            {
                _context.getErrors().addRowError(new ValidationException(x.getMessage()));
            }
            catch (SQLException sqlx)
            {
                throw new RuntimeSQLException(sqlx);
            }
            catch (QueryUpdateServiceException x)
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
            var context = new DataIteratorContext();
            context.setInsertOption(QueryUpdateService.InsertOption.INSERT);
            DataIterator existing = new ExistingDataIteratorsTableInfo(new CachingDataIterator(di), CoreSchema.getInstance().getTableInfoModules(), null, null, context, true);
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
