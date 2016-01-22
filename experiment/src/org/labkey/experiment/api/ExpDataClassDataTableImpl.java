package org.labkey.experiment.api;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.AttachmentParentEntity;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableExtension;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.DataIteratorUtil;
import org.labkey.api.etl.LoggingDataIterator;
import org.labkey.api.etl.MapDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.etl.TableInsertDataIterator;
import org.labkey.api.etl.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocolOutput;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SimpleRunRecord;
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
import org.labkey.api.query.LookupForeignKey;
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
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.samples.UploadSamplesHelper;

import java.io.IOException;
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

        setContainerFilter(new ContainerFilter.CurrentPlusProjectAndShared(_userSchema.getUser()));
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
            case Alias:
                ColumnInfo aliasCol = wrapColumn("Alias", getRealTable().getColumn("LSID"));
                aliasCol.setDescription("Contains the list of aliases for this data object");
                aliasCol.setFk(new MultiValuedForeignKey(new LookupForeignKey("LSID")
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return ExperimentService.get().getTinfoDataAliasMap();
                    }
                }, "Alias"));
                aliasCol.setCalculated(true);
                aliasCol.setRequired(false);
                aliasCol.setShownInInsertView(false);
                aliasCol.setShownInUpdateView(false);
                aliasCol.setUserEditable(false);
                return aliasCol;
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
        addColumn(Column.Alias).setHidden(true);

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
                    else if (pd.getPropertyType() == PropertyType.ATTACHMENT)
                    {
                        col.setURL(StringExpressionFactory.createURL(
                                new ActionURL(ExperimentController.DataClassAttachmentDownloadAction.class, schema.getContainer())
                                    .addParameter("lsid", "${LSID}")
                                    .addParameter("name", "${" + col.getName() + "}")
                        ));
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
        DataIteratorBuilder step0 = new DataClassDataIteratorBuilder(data, context, getUserSchema().getUser());
        return new DataClassAliasIteratorBuilder(step0, context, getUserSchema().getUser());
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
        private User _user;

        DataClassDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context, User user)
        {
            _context = context;
            _in = in;
            _user = user;
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
            step0.selectAll(Sets.newCaseInsensitiveHashSet("lsid", "dataClass", "genId", "alias"));
            final Map<String, Integer> colNameMap = DataIteratorUtil.createColumnNameMap(input);

            TableInfo expData = svc.getTinfoData();
            ColumnInfo lsidCol = expData.getColumn("lsid");

            // Generate LSID before inserting
            step0.addColumn(lsidCol, new Supplier(){
                @Override
                public Object get()
                {
                    return svc.generateGuidLSID(c, ExpData.class);
                }
            });

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
            DataIteratorBuilder step4 = new DerivationDataIteratorBuilder(step3, _user);

            // Hack: add the alias and lsid values back into the input so we can process them in the chained data iterator
            DataIteratorBuilder step5 = step4;
            if (colNameMap.containsKey("alias"))
            {
                SimpleTranslator st = new SimpleTranslator(step4.getDataIterator(context), context);
                st.selectAll();
                ColumnInfo aliasCol = getColumn(FieldKey.fromParts("alias"));
                st.addColumn(aliasCol, new Supplier(){
                    @Override
                    public Object get()
                    {
                        return input.get(colNameMap.get("alias"));
                    }
                });
                step5 = DataIteratorBuilder.wrap(st);
            }

            return LoggingDataIterator.wrap(step5.getDataIterator(context));
        }
    }

    /**
     * Data iterator to handle aliases
     */
    private class DataClassAliasIteratorBuilder implements DataIteratorBuilder
    {
        private DataIteratorContext _context;
        private final DataIteratorBuilder _in;
        private User _user;

        DataClassAliasIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context, User user)
        {
            _context = context;
            _in = in;
            _user = user;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            _context = context;
            DataIterator input = _in.getDataIterator(context);
            if (null == input)
                return null;           // Can happen if context has errors

            final ExperimentService.Interface svc = ExperimentService.get();
            SimpleTranslator step0 = new SimpleTranslator(input, context);
            step0.selectAll(Sets.newCaseInsensitiveHashSet("lsid", "alias"));

            ColumnInfo aliasCol = getColumn(FieldKey.fromParts("alias"));
            final Map<String, Integer> colNameMap = DataIteratorUtil.createColumnNameMap(input);

            if (colNameMap.containsKey("alias") && colNameMap.containsKey("lsid"))
            {
                step0.addColumn(aliasCol, new Supplier(){
                    @Override
                    public Object get()
                    {
                        Object value = input.get(colNameMap.get("alias"));
                        Object lsidValue = input.get(colNameMap.get("lsid"));

                        if (value instanceof List && lsidValue instanceof String)
                        {
                            List<Integer> params = new ArrayList<>();
                            List<String> aliasNames = new ArrayList<>();

                            ((List) value).stream().filter(item -> item instanceof String).forEach(item -> {

                                // an input list of alias rowIds, validate that they exist in the alias table before
                                // adding them to the dataAliasMap
                                if (NumberUtils.isDigits((String)item))
                                    params.add(NumberUtils.toInt((String)item));
                                else
                                {
                                    // parse out the comma separated names
                                    if (((String)item).contains(","))
                                    {
                                        String[] parts = ((String)item).split(",");
                                        aliasNames.addAll(Arrays.asList(parts));
                                    }
                                    else
                                        aliasNames.add((String) item);
                                }
                            });

                            // make sure that an alias entry exist for each string value passed in, else create it
                            for (String aliasName : aliasNames)
                            {
                                aliasName = aliasName.trim();
                                TableSelector selector = new TableSelector(svc.getTinfoAlias(), Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromParts("Name"), aliasName), null);
                                assert selector.getRowCount() <= 1;

                                if (selector.getRowCount() > 0)
                                {
                                    selector.forEach(rs -> params.add(rs.getInt("rowid")));
                                }
                                else
                                {
                                    // create a new alias
                                    DataAlias alias = new DataAlias();
                                    alias.setName(aliasName);

                                    alias = Table.insert(_user, svc.getTinfoAlias(), alias);
                                    assert alias.getRowId() != 0;

                                    params.add(alias.getRowId());
                                }
                            }

                            SimpleFilter filter = new SimpleFilter();
                            filter.addClause(new SimpleFilter.InClause(FieldKey.fromParts("rowid"), params));

                            if (new TableSelector(svc.getTinfoAlias(), filter, null).getRowCount() == params.size())
                            {
                                // insert the new rows into the mapping table
                                for (Integer aliasId : params)
                                {
                                    Table.insert(_user, svc.getTinfoDataAliasMap(), new DataAliasMap((String)lsidValue, aliasId, getContainer()));
                                }
                            }
                        }
                        return null;
                    }
                });
            }

            return LoggingDataIterator.wrap(step0);
        }
    }

    public static class DataAliasMap
    {
        int _alias;
        String _lsid;
        Container _container;

        public DataAliasMap(String lsid, int alias, Container container)
        {
            _lsid = lsid;
            _alias = alias;
            _container = container;
        }

        public DataAliasMap()
        {
        }

        public int getAlias()
        {
            return _alias;
        }

        public void setAlias(int alias)
        {
            _alias = alias;
        }

        public String getLsid()
        {
            return _lsid;
        }

        public void setLsid(String lsid)
        {
            _lsid = lsid;
        }

        public Container getContainer()
        {
            return _container;
        }

        public void setContainer(Container container)
        {
            _container = container;
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
        final User _user;

        DerivationDataIteratorBuilder(DataIteratorBuilder pre, User user)
        {
            _pre = pre;
            _user = user;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            return LoggingDataIterator.wrap(new DerivationDataIterator(pre, context, _user));
        }
    }

    private class DerivationDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        Integer _parentCol;
        String _parentColName;
        final Set<String> _allParentNames;
        final Map<String, Set<String>> _parentNames;
        final User _user;

        protected DerivationDataIterator(DataIterator di, DataIteratorContext context, User user)
        {
            super(di);
            _context = context;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _allParentNames = new HashSet<>();
            _parentNames = new LinkedHashMap<>();
            _user = user;

            for (Map.Entry<String, Integer> entry : map.entrySet())
            {
                if (entry.getKey().toLowerCase().startsWith(UploadSamplesHelper.DATA_INPUT_PARENT.toLowerCase()) ||
                    entry.getKey().toLowerCase().startsWith(UploadSamplesHelper.MATERIAL_INPUT_PARENT.toLowerCase()))
                {
                    _parentCol = entry.getValue();
                    _parentColName = entry.getKey();
                    break;
                }
            }
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // For each iteration, collect the parent col values
            if (_parentCol != null)
            {
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
            }

            if (!hasNext)
            {
                try
                {
                    List<SimpleRunRecord> runRecords = new ArrayList<>();
                    for (Map.Entry<String, Set<String>> entry : _parentNames.entrySet())
                    {
                        Map<ExpProtocolOutput, String> parentMap = new HashMap<>();
                        for (String name : entry.getValue())
                        {
                            UploadSamplesHelper.resolveParentInputs(_user, getContainer(), _parentColName, name, null, parentMap);
                        }

                        if (!parentMap.isEmpty())
                        {
                            // Need to create a new derivation run
                            Map<ExpMaterial, String> parentMaterialMap = new HashMap<>();
                            Map<ExpData, String> parentDataMap = new HashMap<>();

                            for (Map.Entry<ExpProtocolOutput, String> mapEntry : parentMap.entrySet())
                            {
                                if (mapEntry.getKey() instanceof ExpMaterial)
                                    parentMaterialMap.put((ExpMaterial)mapEntry.getKey(), mapEntry.getValue());
                                else if (mapEntry.getKey() instanceof ExpData)
                                    parentDataMap.put((ExpData)mapEntry.getKey(), mapEntry.getValue());
                            }

                            // get the output data
                            ExpData outputData = ExperimentService.get().getExpData(entry.getKey());
                            if (outputData != null)
                            {
                                runRecords.add(new UploadSamplesHelper.UploadSampleRunRecord(parentMaterialMap, Collections.emptyMap(),
                                        parentDataMap, Collections.singletonMap(outputData, "Data")));
                            }
                        }
                    }

                    if (!runRecords.isEmpty())
                    {
                        ExperimentService.get().deriveSamplesBulk(runRecords, new ViewBackgroundInfo(getContainer(), _user, null), null);
                    }
                }
                catch (ExperimentException e)
                {
                    throw new RuntimeException(e);
                }
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
            List<Map<String, Object>> results = super._insertRowsUsingETL(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);

            // handle attachments
            if (results != null && !results.isEmpty())
            {
                for (Map<String, Object> result : results)
                {
                    String lsid = (String) result.get("LSID");
                    addAttachments(user, container, result, lsid);
                }
            }

            return results;
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

            // handle attachments
            removePreviousAttachments(user, c, row, oldRow);
            addAttachments(user, c, ret, lsid);

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

            ExperimentServiceImpl.get().deleteDataClassAttachments(c, Collections.singletonList(lsid));
        }

        @Override
        protected int truncateRows(User user, Container container) throws QueryUpdateServiceException, SQLException
        {
            return ExperimentServiceImpl.get().truncateDataClass(_dataClass, container);
        }

        private void removePreviousAttachments(User user, Container c, Map<String, Object> newRow, Map<String, Object> oldRow)
        {
            Lsid lsid = new Lsid((String)oldRow.get("LSID"));

            for (Map.Entry<String, Object> entry : newRow.entrySet())
            {
                if (isAttachmentProperty(entry.getKey()) && oldRow.get(entry.getKey()) != null)
                {
                    AttachmentParentEntity parent = new AttachmentParentEntity();
                    parent.setEntityId(lsid.getObjectId());
                    parent.setContainer(c.getId());

                    AttachmentService.get().deleteAttachment(parent, (String) oldRow.get(entry.getKey()), user);
                }
            }
        }

        private boolean isAttachmentProperty(String name)
        {
            DomainProperty dp = _dataClass.getDomain().getPropertyByName(name);
            if (dp != null)
                return PropertyType.ATTACHMENT.equals(dp.getPropertyDescriptor().getPropertyType());
            return false;
        }

        private void addAttachments(User user, Container c, Map<String, Object> row, String lsidStr)
        {
            if (row != null && lsidStr != null)
            {
                ArrayList<AttachmentFile> attachmentFiles = new ArrayList<>();
                for (Map.Entry<String, Object> entry : row.entrySet())
                {
                    if (isAttachmentProperty(entry.getKey()) && entry.getValue() instanceof AttachmentFile)
                    {
                        AttachmentFile file = (AttachmentFile) entry.getValue();
                        if (null != file.getFilename())
                            attachmentFiles.add(file);
                    }
                }

                if (!attachmentFiles.isEmpty())
                {
                    Lsid lsid = new Lsid(lsidStr);

                    AttachmentParentEntity parent = new AttachmentParentEntity();
                    parent.setEntityId(lsid.getObjectId());
                    parent.setContainer(c.getId());

                    try
                    {
                        AttachmentService.get().addAttachments(parent, attachmentFiles, user);
                    }
                    catch (IOException e)
                    {
                        throw UnexpectedException.wrap(e);
                    }
                }
            }
        }
    }
}
