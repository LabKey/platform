package org.labkey.experiment.api;

import org.apache.commons.beanutils.ConversionException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableExtension;
import org.labkey.api.data.TableInfo;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.MapDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.etl.TableInsertDataIterator;
import org.labkey.api.etl.WrapperDataIterator;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * User: kevink
 * Date: 9/29/15
 */
public class ExpDataClassDataTableImpl extends ExpTableImpl<ExpDataClassDataTable.Column> implements ExpDataClassDataTable
{
    private static final Logger LOG = Logger.getLogger(ExpDataClassDataTableImpl.class);

    private ExpDataClassImpl _dataClass;
    private TableExtension _extension;

    public ExpDataClassDataTableImpl(String name, UserSchema schema, ExpDataClassImpl dataClass)
    {
        super(name, ExperimentService.get().getTinfoData(), schema, dataClass);
        _dataClass = dataClass;
        addAllowablePermission(InsertPermission.class);
        addAllowablePermission(UpdatePermission.class);

        // Filter exp.data to only those rows that are members of the DataClass
        addCondition(new SimpleFilter(FieldKey.fromParts("classId"), _dataClass.getRowId()));
    }

    @NotNull
    public Domain getDomain()
    {
        return _dataClass.getDomain();
    }

    @Override
    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RowId:
            {
                ColumnInfo c = wrapColumn(alias, getRealTable().getColumn("RowId"));
                c.setKeyField(true);
                c.setHidden(true);
                return c;
            }

            case LSID:
            {
                ColumnInfo c = wrapColumn(alias, getRealTable().getColumn("LSID"));
                c.setHidden(true);
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                c.setCalculated(true); // So DataIterator won't consider the column as required. See c.isRequiredForInsert()
                return c;
            }

            case Name:
            {
                ColumnInfo c = wrapColumn(alias, getRealTable().getColumn(column.name()));
                // TODO: Name is editable in insert view, but not in update view
                return c;
            }

            case Created:
            case Modified:
            case Description:
                return wrapColumn(alias, getRealTable().getColumn(column.name()));

            case CreatedBy:
            case ModifiedBy:
            {
                ColumnInfo c = wrapColumn(alias, getRealTable().getColumn(column.name()));
                c.setFk(new UserIdForeignKey(getUserSchema()));
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                return c;
            }

            case DataClass:
            {
                ColumnInfo c = wrapColumn(alias, getRealTable().getColumn("classId"));
                c.setFk(new QueryForeignKey(ExpSchema.SCHEMA_NAME, getContainer(), getContainer(), getUserSchema().getUser(), ExpSchema.TableType.DataClasses.name(), "RowId", "Name"));
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                return c;
            }

            case Flag:
                return createFlagColumn(Column.Flag.toString());

            case Folder:
            {
                ColumnInfo c = wrapColumn("Container", getRealTable().getColumn("Container"));
                c.setLabel("Folder");
                ContainerForeignKey.initColumn(c, getUserSchema());
                return c;
            }

            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    public void populate()
    {
        UserSchema schema = getUserSchema();

        if (_dataClass.getDescription() != null)
            setDescription(_dataClass.getDescription());
        else
            setDescription("Contains one row per registered data in the " + _dataClass.getName() + " data class");

        if (_dataClass.getContainer().equals(getContainer()))
        {
            setContainerFilter(new ContainerFilter.CurrentPlusExtras(getUserSchema().getUser(), _dataClass.getContainer()));
        }

        // Need to add the LSID column before creating the TableExtension otherwise creating the TableExtension will fail.
        ColumnInfo lsidCol = addColumn(Column.LSID);

        TableInfo extTable = _dataClass.getTinfo();
        _extension = TableExtension.create(this, extTable,
                "lsid", "lsid", LookupColumn.JoinType.inner);

        LinkedHashSet<FieldKey> defaultVisible = new LinkedHashSet<>();
        defaultVisible.add(FieldKey.fromParts(Column.Name));
        defaultVisible.add(FieldKey.fromParts(Column.Flag));

        addColumn(Column.RowId);
        ColumnInfo nameCol = addColumn(Column.Name);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);
        addColumn(Column.Flag);
        addColumn(Column.DataClass);
        addColumn(Column.Folder);
        addColumn(Column.Description);
        //TODO: may need to expose ExpData.Run as well

        // Add the domain columns
        Collection<ColumnInfo> cols = new ArrayList<>(20);
        for (ColumnInfo col : extTable.getColumns())
        {
            // Skip the lookup column itself, LSID, and exp.data.rowid -- it is added above
            String colName = col.getName();
            if (colName.equalsIgnoreCase(_extension.getLookupColumnName()) || colName.equalsIgnoreCase("rowid"))
                continue;

            if (colName.equalsIgnoreCase("genid"))
            {
                col.setHidden(true);
                col.setKeyField(true);
                col.setShownInDetailsView(false);
                col.setShownInInsertView(false);
                col.setShownInUpdateView(false);
            }
            String newName = col.getName();
            for (int i = 0; null != getColumn(newName); i++)
                newName = newName + i;
            cols.add(_extension.addExtensionColumn(col, newName));
        }

        HashMap<String,DomainProperty> properties = new HashMap<>();
        for (DomainProperty dp : getDomain().getProperties())
            properties.put(dp.getPropertyURI(), dp);

        for (ColumnInfo col : cols)
        {
            String propertyURI = col.getPropertyURI();
            if (null != propertyURI)
            {
                DomainProperty dp = properties.get(propertyURI);
                PropertyDescriptor pd = (null==dp) ? null : dp.getPropertyDescriptor();

                if (null != dp && null != pd)
                {
                    if (pd.getLookupQuery() != null || pd.getConceptURI() != null)
                    {
                        col.setFk(new PdLookupForeignKey(schema.getUser(), pd, schema.getContainer()));
                    }

                    if (pd.getPropertyType() == PropertyType.MULTI_LINE)
                    {
                        col.setDisplayColumnFactory(colInfo -> {
                            DataColumn dc = new DataColumn(colInfo);
                            dc.setPreserveNewlines(true);
                            return dc;
                        });
                    }
                }
            }

            if (isVisibleByDefault(col))
                defaultVisible.add(FieldKey.fromParts(col.getName()));
        }


        ActionURL gridUrl = new ActionURL(ExperimentController.ShowDataClassAction.class, getContainer());
        gridUrl.addParameter("rowId", _dataClass.getRowId());
        setGridURL(new DetailsURL(gridUrl));

        ActionURL actionURL = new ActionURL(ExperimentController.ShowDataAction.class, getContainer());
        DetailsURL detailsURL = new DetailsURL(actionURL, Collections.singletonMap("rowId", "rowId"));
        setDetailsURL(detailsURL);

        StringExpression url = StringExpressionFactory.create(detailsURL.getActionURL().getLocalURIString(true));
        nameCol.setURL(url);

        setTitleColumn("Name");
        setDefaultVisibleColumns(defaultVisible);

    }

    private static final Set<String> DEFAULT_HIDDEN_COLS = new CaseInsensitiveHashSet("Container", "Created", "CreatedBy", "ModifiedBy", "Modified", "Owner", "EntityId", "RowId");

    private boolean isVisibleByDefault(ColumnInfo col)
    {
        return (!col.isHidden() && !col.isUnselectable() && !DEFAULT_HIDDEN_COLS.contains(col.getName()));
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getIndices()
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> indices = new HashMap<>(super.getIndices());
        indices.putAll(wrapTableIndices(_dataClass.getTinfo()));
        return Collections.unmodifiableMap(indices);
    }

    //
    // UpdatableTableInfo
    //


    @Override
    public boolean insertSupported()
    {
        return true;
    }

    @Override
    public boolean updateSupported()
    {
        return true;
    }

    @Override
    public boolean deleteSupported()
    {
        return true;
    }

    @Override
    public TableInfo getSchemaTableInfo()
    {
        return ((FilteredTable)getRealTable()).getRealTable();
    }

    @Override
    public ObjectUriType getObjectUriType()
    {
        return ObjectUriType.schemaColumn;
    }

    @Nullable
    @Override
    public String getObjectURIColumnName()
    {
        return "lsid";
    }

    @Nullable
    @Override
    public String getObjectIdColumnName()
    {
        return null;
    }

    @Nullable
    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        if (null != getRealTable().getColumn("container") && null != getColumn("folder"))
        {
            CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<>();
            m.put("container", "folder");
            return m;
        }
        return null;
    }

    @Nullable
    @Override
    public CaseInsensitiveHashSet skipProperties()
    {
        return null;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        return new DataClassDataIteratorBuilder(data, context);
    }

    @Override
    public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
    {
        return null;
    }

    @Override
    public Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException
    {
        return null;
    }

    @Override
    public Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException
    {
        return null;
    }

    private class DataClassDataIteratorBuilder implements DataIteratorBuilder
    {
        private static final int BATCH_SIZE = 100;

        private DataIteratorContext _context;
        private final DataIteratorBuilder _in;

        // genId sequence state
        private int _count = 0;
        private Integer _sequenceNum;

        DataClassDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
        {
            _context = context;
            _in = in;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            final Container c = getContainer();

            final ExperimentService.Interface svc = ExperimentService.get();

            SimpleTranslator step0 = new SimpleTranslator(input, context);
            step0.selectAll(Sets.newCaseInsensitiveHashSet("lsid", "dataClass"));

            TableInfo expData = svc.getTinfoData();
            ColumnInfo lsidCol = expData.getColumn("lsid");

            // Generate LSID before inserting
            step0.addColumn(lsidCol, (Supplier) () -> svc.generateGuidLSID(c, ExpData.class));

            // auto gen a sequence number for genId - reserve BATCH_SIZE numbers at a time so we don't select the next sequence value for every row
            ColumnInfo genIdCol = _dataClass.getTinfo().getColumn(FieldKey.fromParts("genId"));
            final int batchSize = _context.getInsertOption().batch ? BATCH_SIZE : 1;
            step0.addColumn(genIdCol, (Supplier) () -> {
                int genId;
                if (_sequenceNum == null || ((_count % batchSize) == 0))
                {
                    DbSequence sequence = DbSequenceManager.get(_dataClass.getContainer(), ExpDataClassImpl.GENID_SEQUENCE_NAME, _dataClass.getRowId());
                    _sequenceNum = sequence.next();
                    if (batchSize > 1)
                        sequence.ensureMinimum(_sequenceNum + batchSize - 1);
                    _count = 1;
                    genId = _sequenceNum;
                }
                else
                {
                    _count++;
                    genId = ++_sequenceNum;
                }

                return genId;
            });

            // Ensure we have a dataClass column and it is of the right value
            ColumnInfo classIdCol = expData.getColumn("classId");
            step0.addColumn(classIdCol, new SimpleTranslator.ConstantColumn(_dataClass.getRowId()));

            // Ensure we have a name column -- makes the NameExpressionDataIterator easier
            if (!DataIteratorUtil.createColumnNameMap(step0).containsKey("name"))
            {
                ColumnInfo nameCol = expData.getColumn("name");
                step0.addColumn(nameCol, (Supplier)() -> null);
            }

            // Generate names
            DataIteratorBuilder step1 = DataIteratorBuilder.wrap(step0);
            if (_dataClass.getNameExpression() != null)
            {
                StringExpression expr = StringExpressionFactory.create(_dataClass.getNameExpression());
                // TODO: name expression will fail if ${RowId} or ${DataId} auto-inc columns are used.
                // TODO: Change RowId to a plain integer column (not auto-inc) and maintain
                // TODO: a server-side AtomicInteger for each DataClass and use it for ${RowId}.
                step1 = new NameExpressionDataIteratorBuilder(step1, expr);
            }

            // Insert into exp.data then the provisioned table
            DataIteratorBuilder step2 = TableInsertDataIterator.create(step1, ExperimentService.get().getTinfoData(), c, context);
            DataIteratorBuilder step3 = TableInsertDataIterator.create(step2, ExpDataClassDataTableImpl.this._dataClass.getTinfo(), c, context);

            // Wire up derived parent/child data and materials
            // TODO: Don't hard-code the parent column name
            DataIteratorBuilder step4 = step3;
            if (_rootTable.getColumn("parent") != null)
                step4 = new DerivationDataIteratorBuilder(step3);

            return LoggingDataIterator.wrap(step4.getDataIterator(context));
        }
    }

    private class NameExpressionDataIteratorBuilder implements DataIteratorBuilder
    {
        final DataIteratorBuilder _pre;
        private final StringExpression _expr;

        public NameExpressionDataIteratorBuilder(DataIteratorBuilder pre, StringExpression expr)
        {
            _pre = pre;
            _expr = expr;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            return LoggingDataIterator.wrap(new NameExpressionDataIterator(pre, context, _expr));
        }
    }

    private class NameExpressionDataIterator extends WrapperDataIterator
    {
        private final StringExpression _expr;
        private final Integer _nameCol;

        protected NameExpressionDataIterator(DataIterator di, DataIteratorContext context, StringExpression expr)
        {
            super(DataIteratorUtil.wrapMap(di, false));
            _expr = expr;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _nameCol = map.get("name");
            assert _nameCol != null;
        }

        MapDataIterator getInput()
        {
            return (MapDataIterator)_delegate;
        }

        @Override
        public Object get(int i)
        {
            if (i == _nameCol)
            {
                Object curName = super.get(_nameCol);
                if (curName instanceof String)
                    curName = StringUtils.isEmpty((String)curName) ? null : curName;

                if (curName != null)
                    return curName;

                Map<String, Object> currentRow = getInput().getMap();
                String newName = _expr.eval(currentRow);
                if (!StringUtils.isEmpty(newName))
                    return newName;
            }

            return super.get(i);
        }
    }

    private class DerivationDataIteratorBuilder implements DataIteratorBuilder
    {
        final DataIteratorBuilder _pre;

        DerivationDataIteratorBuilder(DataIteratorBuilder pre)
        {
            _pre = pre;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            return LoggingDataIterator.wrap(new DerivationDataIterator(pre, context));
        }
    }

    private class DerivationDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final Integer _parentCol;
        final Set<String> _allParentNames;
        final Map<String, Set<String>> _parentNames;

        protected DerivationDataIterator(DataIterator di, DataIteratorContext context)
        {
            super(di);
            _context = context;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _parentCol = map.get("parent");
            _allParentNames = new HashSet<>();
            _parentNames = new LinkedHashMap<>();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // For each iteration, collect the parent col values
            String lsid = (String) get(_lsidCol);
            String parent = (String) get(_parentCol);
            if (parent != null)
            {
                Set<String> parts = Arrays.stream(parent.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
                _allParentNames.addAll(parts);
                _parentNames.put(lsid, parts);
            }

            if (!hasNext)
            {
                // TODO: On last iteration, perform all derivations
            }


            return hasNext;
        }
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DataClassDataUpdateService(this);
    }

    private class DataClassDataUpdateService extends DefaultQueryUpdateService
    {
        public DataClassDataUpdateService(ExpDataClassDataTableImpl table)
        {
            super(table, table.getRealTable());
        }

        @Override
        public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum,Object> configParameters, Map<String, Object> extraScriptContext)
                throws SQLException
        {
            return _importRowsUsingETL(user, container, rows, null, getDataIteratorContext(errors, InsertOption.IMPORT, configParameters), extraScriptContext);
        }


        @Override
        public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
                throws SQLException
        {
            return _importRowsUsingETL(user, container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
        {
            List<Map<String, Object>> result = super._insertRowsUsingETL(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
            return result;
        }

        @Override
        protected Map<String, Object> _select(Container container, Object[] keys) throws SQLException, ConversionException
        {
            TableInfo d = getDbTable();
            TableInfo t = ExpDataClassDataTableImpl.this._dataClass.getTinfo();

            SQLFragment sql = new SQLFragment()
                    .append("SELECT t.*, d.RowId, d.Name, d.Container, d.Description, d.CreatedBy, d.Created, d.ModifiedBy, d.Modified")
                    .append(" FROM ").append(d, "d")
                    .append(" LEFT OUTER JOIN ").append(t, "t")
                    .append(" ON d.lsid = t.lsid")
                    .append(" WHERE d.Container=?").add(container.getEntityId())
                    .append(" AND d.rowid=?").add(keys[0]);

            return new SqlSelector(getDbTable().getSchema(), sql).getObject(Map.class);
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            // LSID was stripped by super.updateRows() and is needed to insert into the dataclass provisioned table
            String lsid = (String)oldRow.get("lsid");
            if (lsid == null)
                throw new ValidationException("lsid required to update row");

            // update exp.data
            Map<String, Object> ret = new CaseInsensitiveHashMap<>(super._update(user, c, row, oldRow, keys));

            // update provisioned table -- note that LSID isn't the PK so we need to use the filter to update the correct row instead
            keys = new Object[] { };
            TableInfo t = ExpDataClassDataTableImpl.this._dataClass.getTinfo();
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), lsid);
            ret.putAll(Table.update(user, t, row, keys, filter, Level.DEBUG));

            return ret;
        }

        @Override
        protected void _delete(Container c, Map<String, Object> row) throws InvalidKeyException
        {
            String lsid = (String)row.get("lsid");
            if (lsid == null)
                throw new InvalidKeyException("lsid required to delete row");

            // NOTE: The provisioned table row will be deleted in ExperimentServiceImpl.deleteDataByRowIds()
            //Table.delete(getDbTable(), new SimpleFilter(FieldKey.fromParts("lsid"), lsid));
            ExpData data = ExperimentService.get().getExpData(lsid);
            data.delete(getUserSchema().getUser());
        }

        @Override
        protected int truncateRows(User user, Container container) throws QueryUpdateServiceException, SQLException
        {
            return ExperimentServiceImpl.get().truncateDataClass(_dataClass, container);
        }
    }
}
