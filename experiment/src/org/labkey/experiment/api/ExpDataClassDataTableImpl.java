/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.experiment.api;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.*;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.TableInsertDataIterator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SimpleRunRecord;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
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
import org.labkey.experiment.controllers.exp.RunInputOutputBean;
import org.labkey.experiment.samples.UploadSamplesHelper;
import org.labkey.experiment.samples.UploadSamplesHelper.UploadSampleRunRecord;

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
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * User: kevink
 * Date: 9/29/15
 */
public class ExpDataClassDataTableImpl extends ExpProtocolOutputTableImpl<ExpDataClassDataTable.Column> implements ExpDataClassDataTable
{
    private static final Logger LOG = Logger.getLogger(ExpDataClassDataTableImpl.class);

    private @NotNull ExpDataClassImpl _dataClass;

    public ExpDataClassDataTableImpl(String name, UserSchema schema, @NotNull ExpDataClassImpl dataClass)
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
                // When no sorts are added by views, QueryServiceImpl.createDefaultSort() adds the primary key's default sort direction
                c.setSortDirection(Sort.SortDirection.DESC);
                c.setFk(new RowIdForeignKey(c));
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
                String desc = ExpMaterialTableImpl.appendNameExpressionDescription(c.getDescription(), _dataClass.getNameExpression());
                c.setDescription(desc);
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
                ContainerForeignKey.initColumn(c, getUserSchema());
                c.setLabel("Folder");
                c.setShownInDetailsView(false);
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
                aliasCol.setCalculated(false);
                aliasCol.setNullable(true);
                aliasCol.setRequired(false);
                aliasCol.setDisplayColumnFactory(new AliasDisplayColumnFactory());
                return aliasCol;

            case Inputs:
                return createLineageColumn(this, alias, true);

            case Outputs:
                return createLineageColumn(this, alias, false);

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


        TableInfo extTable = _dataClass.getTinfo();

        LinkedHashSet<FieldKey> defaultVisible = new LinkedHashSet<>();
        defaultVisible.add(FieldKey.fromParts(Column.Name));
        defaultVisible.add(FieldKey.fromParts(Column.Flag));

        ColumnInfo lsidCol = addColumn(Column.LSID);
        ColumnInfo rowIdCol = addColumn(Column.RowId);
        ColumnInfo nameCol = addColumn(Column.Name);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);
        addColumn(Column.Flag);
        addColumn(Column.DataClass);
        addColumn(Column.Folder);
        addColumn(Column.Description);
        addColumn(Column.Alias);

        //TODO: may need to expose ExpData.Run as well

        // Add the domain columns
        Collection<ColumnInfo> cols = new ArrayList<>(20);
        for (ColumnInfo col : extTable.getColumns())
        {
            // Skip the lookup column itself, LSID, and exp.data.rowid -- it is added above
            String colName = col.getName();
            if (colName.equalsIgnoreCase("lsid") || colName.equalsIgnoreCase("rowid"))
                continue;

            if (colName.equalsIgnoreCase("genid"))
            {
                col.setHidden(true);
                col.setUserEditable(false);
                col.setShownInDetailsView(false);
                col.setShownInInsertView(false);
                col.setShownInUpdateView(false);
            }
            String newName = col.getName();
            for (int i = 0; null != getColumn(newName); i++)
                newName = newName + i;

            ExprColumn expr = new ExprColumn(this, col.getName(), col.getValueSql(ExprColumn.STR_TABLE_ALIAS), col.getJdbcType());
            expr.copyAttributesFrom(col);
            if (col.isHidden())
                expr.setHidden(true);
            addColumn(expr);
            cols.add(expr);
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

        ColumnInfo colInputs = addColumn(Column.Inputs);
        addMethod("Inputs", new LineageMethod(getContainer(), colInputs, true));

        ColumnInfo colOutputs = addColumn(Column.Outputs);
        addMethod("Outputs", new LineageMethod(getContainer(), colOutputs, false));


        ActionURL gridUrl = new ActionURL(ExperimentController.ShowDataClassAction.class, getContainer());
        gridUrl.addParameter("rowId", _dataClass.getRowId());
        setGridURL(new DetailsURL(gridUrl));

        ActionURL actionURL = new ActionURL(ExperimentController.ShowDataAction.class, getContainer());
        DetailsURL detailsURL = new DetailsURL(actionURL, Collections.singletonMap("rowId", "rowId"));
        setDetailsURL(detailsURL);

        StringExpression url = StringExpressionFactory.create(detailsURL.getActionURL().getLocalURIString(true));
        rowIdCol.setURL(url);
        nameCol.setURL(url);

        ActionURL deleteUrl = ExperimentController.ExperimentUrlsImpl.get().getDeleteDatasURL(getContainer(), null);
        setDeleteURL(new DetailsURL(deleteUrl));

        setTitleColumn("Name");
        setDefaultVisibleColumns(defaultVisible);

    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        TableInfo provisioned = _dataClass.getTinfo();

        // all columns from exp.data except lsid
        Set<String> dataCols = new CaseInsensitiveHashSet(_rootTable.getColumnNameSet());
        dataCols.remove("lsid");

        SQLFragment sql = new SQLFragment();
        sql.append("(SELECT ");
        for (String dataCol : dataCols)
            sql.append("d.").append(dataCol).append(", ");
        sql.append("p.* FROM ");
        sql.append(_rootTable, "d");
        sql.append(" INNER JOIN ").append(provisioned, "p").append(" ON d.lsid = p.lsid");

        // WHERE
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), columnMap);
        sql.append("\n").append(filterFrag).append(") ").append(alias);

        return sql;
    }

    private static final Set<String> DEFAULT_HIDDEN_COLS = new CaseInsensitiveHashSet("Container", "Created", "CreatedBy", "ModifiedBy", "Modified", "Owner", "EntityId", "RowId");

    private boolean isVisibleByDefault(ColumnInfo col)
    {
        return (!col.isHidden() && !col.isUnselectable() && !DEFAULT_HIDDEN_COLS.contains(col.getName()));
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> indices = new HashMap<>(super.getUniqueIndices());
        indices.putAll(wrapTableIndices(_dataClass.getTinfo()));
        return Collections.unmodifiableMap(indices);
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getAllIndices()
    {
        Map<String, Pair<IndexType, List<ColumnInfo>>> indices = new HashMap<>(super.getAllIndices());
        indices.putAll(wrapTableIndices(_dataClass.getTinfo()));
        return Collections.unmodifiableMap(indices);
    }

    @Override
    public boolean hasDbTriggers()
    {
        return super.hasDbTriggers() || _dataClass.getTinfo().hasDbTriggers();
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

    private class PreTriggerDataIteratorBuilder implements DataIteratorBuilder
    {
        private static final int BATCH_SIZE = 100;

        private DataIteratorContext _context;
        private final DataIteratorBuilder _in;

        // genId sequence state
        private int _count = 0;
        private Integer _sequenceNum;

        public PreTriggerDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context)
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
            final ExperimentService svc = ExperimentService.get();

            SimpleTranslator step0 = new SimpleTranslator(input, context);
            step0.selectAll(Sets.newCaseInsensitiveHashSet("lsid", "dataClass", "genId"));

            TableInfo expData = svc.getTinfoData();
            ColumnInfo lsidCol = expData.getColumn("lsid");

            // TODO: validate dataFileUrl column, it will be saved later

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

            // Ensure we have a cpasType column and it is of the right value
            ColumnInfo cpasTypeCol = expData.getColumn("cpasType");
            step0.addColumn(cpasTypeCol, new SimpleTranslator.ConstantColumn(_dataClass.getLSID()));

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
                step1 = new NameExpressionDataIteratorBuilder(step1, _dataClass.getNameExpression());
            }

            return LoggingDataIterator.wrap(step1.getDataIterator(context));
        }
    }

    private class DataClassDataIteratorBuilder implements DataIteratorBuilder
    {
        private DataIteratorContext _context;
        private final DataIteratorBuilder _in;

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
            final Map<String, Integer> colNameMap = DataIteratorUtil.createColumnNameMap(input);

            SimpleTranslator step0 = new SimpleTranslator(input, context);
            step0.selectAll(Sets.newCaseInsensitiveHashSet("alias"));

            // TODO: save file path to exp.data.DataFileUrl

            // Insert into exp.data then the provisioned table
            DataIteratorBuilder step2 = TableInsertDataIterator.create(DataIteratorBuilder.wrap(step0), ExperimentService.get().getTinfoData(), c, context);
            DataIteratorBuilder step3 = TableInsertDataIterator.create(step2, ExpDataClassDataTableImpl.this._dataClass.getTinfo(), c, context);

            DataIteratorBuilder step4 = step3;
            if (colNameMap.containsKey("flag") || colNameMap.containsKey("comment"))
            {
                step4 = new FlagDataIteratorBuilder(step3, context, _user);
            }

            // Wire up derived parent/child data and materials
            DataIteratorBuilder step5 = new DerivationDataIteratorBuilder(step4, _user);

            // Hack: add the alias and lsid values back into the input so we can process them in the chained data iterator
            DataIteratorBuilder step6 = step5;
            if (colNameMap.containsKey("alias"))
            {
                SimpleTranslator st = new SimpleTranslator(step5.getDataIterator(context), context);
                st.selectAll();
                ColumnInfo aliasCol = getColumn(FieldKey.fromParts("alias"));
                st.addColumn(aliasCol, new Supplier(){
                    @Override
                    public Object get()
                    {
                        return input.get(colNameMap.get("alias"));
                    }
                });
                step6 = DataIteratorBuilder.wrap(st);
            }

            DataIteratorBuilder step7 = new SearchIndexIteratorBuilder(step6); // may need to add this after the aliases are set

            return LoggingDataIterator.wrap(step7.getDataIterator(context));
        }
    }

    private class FlagDataIteratorBuilder implements DataIteratorBuilder
    {
        private DataIteratorContext _context;
        private final DataIteratorBuilder _in;
        private final User _user;

        FlagDataIteratorBuilder(@NotNull DataIteratorBuilder in, DataIteratorContext context, User user)
        {
            _context = context;
            _in = in;
            _user = user;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new FlagDataIterator(pre, context, _user));
        }
    }

    private class FlagDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final User _user;
        final Integer _lsidCol;
        final Integer _flagCol;

        protected FlagDataIterator(DataIterator di, DataIteratorContext context, User user)
        {
            super(di);
            _context = context;
            _user = user;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _flagCol = map.containsKey("flag") ? map.get("flag") : map.get("comment");
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // skip processing if there are errors upstream
            if (getErrors().hasErrors())
                return hasNext;

            if (_lsidCol != null && _flagCol != null)
            {
                Object lsidValue = get(_lsidCol);
                Object flagValue = get(_flagCol);

                if (lsidValue instanceof String)
                {
                    String lsid = (String)lsidValue;
                    String flag = Objects.toString(flagValue, null);

                    ExpData data = ExperimentService.get().getExpData(lsid);
                    try
                    {
                        data.setComment(_user, flag);
                    }
                    catch (ValidationException e)
                    {
                        throw new BatchValidationException(e);
                    }
                }

            }
            return hasNext;
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
            DataIterator pre = _in.getDataIterator(context);
            return LoggingDataIterator.wrap(new AliasDataIterator(pre, context, _user));
        }
    }

    private class AliasDataIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final Integer _aliasCol;
        final User _user;
        Map<String, Object> _lsidAliasMap = new HashMap<>();

        protected AliasDataIterator(DataIterator di, DataIteratorContext context, User user)
        {
            super(di);
            _context = context;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _aliasCol = map.get("alias");

            _user = user;
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // skip processing if there are errors upstream
            if (getErrors().hasErrors())
                return hasNext;

            // For each iteration, collect the lsid and alias col values.
            if (_lsidCol != null && _aliasCol != null)
            {
                Object lsidValue = get(_lsidCol);
                Object aliasValue = get(_aliasCol);

                if (aliasValue != null && lsidValue instanceof String)
                {
                    _lsidAliasMap.put((String) lsidValue, aliasValue);
                }

                if (!hasNext)
                {
                    final ExperimentService svc = ExperimentService.get();

                    try (DbScope.Transaction transaction = svc.getTinfoDataClass().getSchema().getScope().ensureTransaction())
                    {
                        for (Map.Entry<String, Object> entry : _lsidAliasMap.entrySet())
                        {
                            String lsid = entry.getKey();
                            Object aliases = entry.getValue();
                            AliasInsertHelper.handleInsertUpdate(getContainer(), _user, lsid, svc.getTinfoDataAliasMap(), aliases);
                        }
                        transaction.commit();
                    }
                }
            }
            return hasNext;
        }
    }

    private class NameExpressionDataIteratorBuilder implements DataIteratorBuilder
    {
        final DataIteratorBuilder _pre;
        private final String _nameExpression;

        public NameExpressionDataIteratorBuilder(DataIteratorBuilder pre, String nameExpression)
        {
            _pre = pre;
            _nameExpression = nameExpression;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            return LoggingDataIterator.wrap(new NameExpressionDataIterator(pre, context, _nameExpression));
        }
    }

    private class NameExpressionDataIterator extends WrapperDataIterator
    {
        private final DataIteratorContext _context;
        private final NameGenerator _nameGen;
        private final Integer _nameCol;

        private NameGenerator.State _state;

        protected NameExpressionDataIterator(DataIterator di, DataIteratorContext context, String nameExpression)
        {
            super(DataIteratorUtil.wrapMap(di, false));
            _context = context;
            _nameGen = new NameGenerator(nameExpression, ExpDataClassDataTableImpl.this, false);
            _state = _nameGen.createState(false, false);

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _nameCol = map.get("name");
            assert _nameCol != null;
        }

        MapDataIterator getInput()
        {
            return (MapDataIterator)_delegate;
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
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

                try
                {
                    String newName = _nameGen.generateName(_state, currentRow);
                    if (!StringUtils.isEmpty(newName))
                        return newName;
                }
                catch (NameGenerator.NameGenerationException e)
                {
                    getErrors().addRowError(new ValidationException(e.getMessage()));
                }
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
        final Map<Integer, String> _parentCols;
        // Map from Data LSID to Set of (parentColName, parentName)
        final Map<String, Set<Pair<String, String>>> _parentNames;
        final User _user;

        protected DerivationDataIterator(DataIterator di, DataIteratorContext context, User user)
        {
            super(di);
            _context = context;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _parentNames = new LinkedHashMap<>();
            _parentCols = new HashMap<>();
            _user = user;

            for (Map.Entry<String, Integer> entry : map.entrySet())
            {
                String name = entry.getKey();
                if (UploadSamplesHelper.isInputOutputHeader(name))
                {
                    _parentCols.put(entry.getValue(), entry.getKey());
                }
            }
        }

        private BatchValidationException getErrors()
        {
            return _context.getErrors();
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            // skip processing if there are errors upstream
            if (getErrors().hasErrors())
                return hasNext;

            // For each iteration, collect the parent col values
            if (!_parentCols.isEmpty())
            {
                String lsid = (String) get(_lsidCol);
                Set<Pair<String, String>> allParts = new HashSet<>();
                for (Integer parentCol : _parentCols.keySet())
                {
                    Object o = get(parentCol);
                    if (o != null)
                    {
                        Collection<String> parentNames;
                        if (o instanceof String)
                        {
                            parentNames = Arrays.asList(((String) o).split(","));
                        }
                        else if (o instanceof JSONArray)
                        {
                            parentNames = Arrays.stream(((JSONArray) o).toArray()).map(String::valueOf).collect(Collectors.toSet());
                        }
                        else if (o instanceof Collection)
                        {
                            Collection<?> c = ((Collection)o);
                            parentNames = c.stream().map(String::valueOf).collect(Collectors.toSet());
                        }
                        else
                        {
                            getErrors().addRowError(new ValidationException("Expected comma separated list or a JSONArray of parent names: " + o, _parentCols.get(parentCol)));
                            continue;
                        }

                        String parentColName = _parentCols.get(parentCol);
                        Set<Pair<String, String>> parts = parentNames.stream()
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .map(s -> Pair.of(parentColName, s))
                                .collect(Collectors.toSet());

                        allParts.addAll(parts);
                    }
                }
                _parentNames.put(lsid, allParts);
            }

            if (getErrors().hasErrors())
                return hasNext;

            if (!hasNext)
            {
                try
                {
                    List<UploadSampleRunRecord> runRecords = new ArrayList<>();
                    for (Map.Entry<String, Set<Pair<String, String>>> entry : _parentNames.entrySet())
                    {
                        String lsid = entry.getKey();
                        Set<Pair<String, String>> parentNames = entry.getValue();

                        Pair<RunInputOutputBean, RunInputOutputBean> pair =
                                UploadSamplesHelper.resolveInputsAndOutputs(_user, getContainer(), parentNames, null);

                        ExpData data = null;
                        if (pair.first != null)
                        {
                            // Add parent derivation run
                            Map<ExpMaterial, String> parentMaterialMap = pair.first.getMaterials();
                            Map<ExpData, String> parentDataMap = pair.first.getDatas();

                            data = ExperimentService.get().getExpData(lsid);
                            if (data != null)
                            {
                                UploadSamplesHelper.record(false, runRecords, parentMaterialMap, Collections.emptyMap(),
                                        parentDataMap, new HashMap<>(Collections.singletonMap(data, "Data")));
                            }
                        }

                        if (pair.second != null)
                        {
                            // Add child derivation run
                            Map<ExpMaterial, String> childMaterialMap = pair.second.getMaterials();
                            Map<ExpData, String> childDataMap = pair.second.getDatas();

                            if (data == null)
                                data = ExperimentService.get().getExpData(lsid);
                            if (data != null)
                            {
                                UploadSamplesHelper.record(false, runRecords, Collections.emptyMap(), childMaterialMap,
                                        new HashMap<>(Collections.singletonMap(data, "Data")), childDataMap);
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
                catch (ValidationException e)
                {
                    getErrors().addRowError(e);
                }
            }
            return hasNext;
        }
    }

    private class SearchIndexIteratorBuilder implements DataIteratorBuilder
    {
        final DataIteratorBuilder _pre;

        SearchIndexIteratorBuilder(DataIteratorBuilder pre)
        {
            _pre = pre;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator pre = _pre.getDataIterator(context);
            return LoggingDataIterator.wrap(new SearchIndexIterator(pre, context));
        }
    }

    private class SearchIndexIterator extends WrapperDataIterator
    {
        final DataIteratorContext _context;
        final Integer _lsidCol;
        final ArrayList<String> _lsids;

        protected SearchIndexIterator(DataIterator di, DataIteratorContext context)
        {
            super(di);
            _context = context;

            Map<String, Integer> map = DataIteratorUtil.createColumnNameMap(di);
            _lsidCol = map.get("lsid");
            _lsids = new ArrayList<>(100);

            if (null != DbScope.getLabKeyScope() && null != DbScope.getLabKeyScope().getCurrentTransaction())
            {
                Runnable indexTask = () -> {
                    List<ExpDataImpl> datas = ExperimentServiceImpl.get().getExpDatasByLSID(_lsids);
                    if (datas != null)
                    {
                        for (ExpDataImpl data : datas)
                            data.index(null);
                    }
                };
                DbScope.getLabKeyScope().getCurrentTransaction().addCommitTask(indexTask, DbScope.CommitTaskOption.POSTCOMMIT);
            }
        }

        @Override
        public boolean next() throws BatchValidationException
        {
            boolean hasNext = super.next();

            if (hasNext)
            {
                String lsid = (String) get(_lsidCol);
                if (null != lsid)
                    _lsids.add(lsid);
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
        protected DataIteratorBuilder preTriggerDataIterator(DataIteratorBuilder in, DataIteratorContext context)
        {
            return new PreTriggerDataIteratorBuilder(in, context);
        }

        @Override
        public int importRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum,Object> configParameters, Map<String, Object> extraScriptContext)
                throws SQLException
        {
            // Temporary work around for Issue 26082 -- use INSERT instead of IMPORT
            return _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        }

        @Override
        public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext) throws SQLException
        {
            // Temporary work around for Issue 26082 -- use INSERT instead of IMPORT
            context.setInsertOption(InsertOption.INSERT);
            return super.loadRows(user, container, rows, context, extraScriptContext);
        }

        @Override
        public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
                throws SQLException
        {
            return _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
        {
            List<Map<String, Object>> results = super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);

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

            return new SqlSelector(getDbTable().getSchema(), sql).getMap();
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            // LSID was stripped by super.updateRows() and is needed to insert into the dataclass provisioned table
            String lsid = (String)oldRow.get("lsid");
            if (lsid == null)
                throw new ValidationException("lsid required to update row");

            // Replace attachment columns with filename and keep AttachmentFiles
            Map<String, Object> rowStripped = new CaseInsensitiveHashMap<>();
            Map<String, Object> attachments = new CaseInsensitiveHashMap<>();
            row.forEach((name, value) -> {
                if (isAttachmentProperty(name) && value instanceof AttachmentFile)
                {
                    AttachmentFile file = (AttachmentFile) value;
                    if (null != file.getFilename())
                    {
                        rowStripped.put(name, file.getFilename());
                        attachments.put(name, value);
                    }
                }
                else
                {
                    rowStripped.put(name, value);
                }
            });

            // update exp.data
            Map<String, Object> ret = new CaseInsensitiveHashMap<>(super._update(user, c, rowStripped, oldRow, keys));

            // update provisioned table -- note that LSID isn't the PK so we need to use the filter to update the correct row instead
            keys = new Object[] { };
            TableInfo t = ExpDataClassDataTableImpl.this._dataClass.getTinfo();
            if (t.getColumnNameSet().stream().anyMatch(rowStripped::containsKey))
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), lsid);
                ret.putAll(Table.update(user, t, rowStripped, keys, filter, Level.DEBUG));
            }

            // update comment
            ExpDataImpl data = null;
            if (row.containsKey("flag") || row.containsKey("comment"))
            {
                Object o = row.containsKey("flag") ? row.get("flag") : row.get("comment");
                String flag = Objects.toString(o, null);

                data = ExperimentServiceImpl.get().getExpData(lsid);
                data.setComment(user, flag);
            }

            // update aliases
            if (row.containsKey("Alias"))
                AliasInsertHelper.handleInsertUpdate(getContainer(), user, lsid, ExperimentService.get().getTinfoDataAliasMap(), row.get("Alias"));

            // handle attachments
            removePreviousAttachments(user, c, row, oldRow);
            ret.putAll(attachments);
            addAttachments(user, c, ret, lsid);

            // search index
            SearchService ss = SearchService.get();
            if (ss != null)
            {
                if (data == null)
                    data = ExperimentServiceImpl.get().getExpData(lsid);
                data.index(null);
            }

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
            try
            {
                return ExperimentServiceImpl.get().truncateDataClass(_dataClass, user, container);
            }
            catch (ExperimentException e)
            {
                throw new QueryUpdateServiceException(e);
            }
        }

        private void removePreviousAttachments(User user, Container c, Map<String, Object> newRow, Map<String, Object> oldRow)
        {
            Lsid lsid = new Lsid((String)oldRow.get("LSID"));

            for (Map.Entry<String, Object> entry : newRow.entrySet())
            {
                if (isAttachmentProperty(entry.getKey()) && oldRow.get(entry.getKey()) != null)
                {
                    AttachmentParent parent = new ExpDataClassAttachmentParent(c, lsid);

                    AttachmentService.get().deleteAttachment(parent, (String) oldRow.get(entry.getKey()), user);
                }
            }
        }

        protected Domain getDomain()
        {
            return _dataClass.getDomain();
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
                    AttachmentParent parent = new ExpDataClassAttachmentParent(c, lsid);

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

    static class AliasDisplayColumnFactory implements DisplayColumnFactory
    {
        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            DataColumn dataColumn = new DataColumn(colInfo);
            dataColumn.setInputType("text");

            return new MultiValuedDisplayColumn(dataColumn, true)
            {
                @Override
                public Object getInputValue(RenderContext ctx)
                {
                    Object value =  super.getInputValue(ctx);
                    StringBuilder sb = new StringBuilder();
                    if (value instanceof List)
                    {
                        String delim = "";
                        for (Object item : (List)value)
                        {
                            if (item != null)
                            {
                                String name = new TableSelector(ExperimentService.get().getTinfoAlias(), Collections.singleton("Name")).getObject(item, String.class);

                                sb.append(delim);
                                sb.append(name);
                                delim = ",";
                            }
                        }
                    }
                    return sb.toString();
                }
            };
        }
    }

    public static class AliasInsertHelper
    {
        public static void handleInsertUpdate(Container container, User user, String lsid, TableInfo aliasMapTable, Object value)
        {
            // parse the alias value into separate names and alias rowIds
            Set<String> aliasNames = new HashSet<>();
            Set<Integer> aliasIds = new HashSet<>();
            parseValue(value, aliasNames, aliasIds);

            aliasNames.forEach(s -> {
                if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")))
                    throw new IllegalArgumentException("Alias values must not be surrounded by quote characters: " + s);
            });

            // ensure the alias exist collect the alias' rowId
            aliasIds.addAll(ensureAliases(user, aliasNames));

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), lsid);
            Table.delete(aliasMapTable, filter);
            insertMapEntries(container, user, aliasMapTable, aliasIds, lsid);
        }

        private static void parseValue(Object value, Set<String> aliasNames, Set<Integer> aliasIds)
        {
            if (value == null)
                return;

            if (value instanceof String[])
            {
                String[] aa = (String[]) value;
                for (String a : aa)
                    parseValue(a, aliasNames, aliasIds); // recurse
            }
            else if (value instanceof JSONArray)
            {
                // LABKEY.Query.updateRows passes a JSONArray of individual alias names.
                for (Object o : ((JSONArray)value).toArray())
                    parseValue(o.toString(), aliasNames, aliasIds); // recurse
            }
            else if (value instanceof Collection)
            {
                // Generic query insert form and Excel/tsv import passes an ArrayList with a single String element containing the comma-separated list of values: "abc,def"
                // LABKEY.Query.insertRows passes an ArrayList containing individual alias names.
                for (Object o : (Collection)value)
                    parseValue(o, aliasNames, aliasIds); // recurse
            }
            else if (value instanceof String)
            {
                // Parse the single string element value submitted by the generic query insert and the tsv import forms.
                for (String s : splitAliases((String)value))
                {
                    aliasNames.add(s);
                }
            }
            else if (value instanceof Integer)
            {
                aliasIds.add((Integer)value);
            }
            else
            {
                throw new IllegalArgumentException("Unsupported value for column 'Alias': " + value);
            }
        }

        public static Set<String> splitAliases(String aliases)
        {
            if (aliases == null)
                return Collections.emptySet();

            aliases = aliases.trim();
            if (aliases.length() == 0)
                return Collections.emptySet();

            // If the user entered a string formatted as a JSONArray, parse it now.
            if (aliases.startsWith("[") && aliases.endsWith("]"))
            {
                Set<String> parts = new HashSet<>();
                JSONArray a = new JSONArray(aliases);
                for (Object o : a.toArray())
                {
                    if (o == null)
                        continue;
                    String s = StringUtils.trim(String.valueOf(o));
                    if (s.length() == 0)
                        continue;

                    parts.add(s);
                }

                return parts;
            }

            if (aliases.contains(","))
            {
                // The user entered a comma-separated list of values
                return Arrays.stream(aliases.split(","))
                        .map(String::trim)
                        .filter(StringUtils::isNotEmpty)
                        .collect(Collectors.toSet());
            }
            else
            {
                return Collections.singleton(aliases);
            }
        }

        private static void insertMapEntries(Container container, User user, TableInfo aliasMapTable, Set<Integer> aliasIds, String lsid)
        {
            for (Integer aliasId : aliasIds)
            {
                Map<String, Object> row = CaseInsensitiveHashMap.of(
                        "container", container.getEntityId(),
                        "alias", aliasId,
                        "lsid", lsid
                );
                Table.insert(user, aliasMapTable, row);
            }
        }

        private static Collection<Integer> ensureAliases(User user, Set<String> aliasNames)
        {
            return ExperimentServiceImpl.get().ensureAliases(user, aliasNames);
        }
    }
}
