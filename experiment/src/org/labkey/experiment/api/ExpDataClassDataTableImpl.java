/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.NameExpressionDataIteratorBuilder;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
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
import org.labkey.api.study.assay.FileLinkDisplayColumn;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.ExpDataIterators;
import org.labkey.experiment.ExpDataIterators.AliasDataIteratorBuilder;
import org.labkey.experiment.ExpDataIterators.PersistDataIteratorBuilder;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * User: kevink
 * Date: 9/29/15
 */
public class ExpDataClassDataTableImpl extends ExpRunItemTableImpl<ExpDataClassDataTable.Column> implements ExpDataClassDataTable
{
    private @NotNull ExpDataClassImpl _dataClass;

    @Override
    protected ContainerFilter getDefaultContainerFilter()
    {
        return new ContainerFilter.CurrentPlusProjectAndShared(_userSchema.getUser());
    }

    public ExpDataClassDataTableImpl(String name, UserSchema schema, ContainerFilter cf, @NotNull ExpDataClassImpl dataClass)
    {
        super(name, ExperimentService.get().getTinfoData(), schema, dataClass, cf);
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
    public AuditBehaviorType getAuditBehavior()
    {
        // if there is xml config, use xml config
        if (_auditBehaviorType == AuditBehaviorType.NONE && getXmlAuditBehaviorType() == null)
        {
            ExpSchema.DataClassCategoryType categoryType = ExpSchema.DataClassCategoryType.fromString(_dataClass.getCategory());
            if (categoryType != null && categoryType.defaultBehavior != null)
                return categoryType.defaultBehavior;
        }

        return _auditBehaviorType;
    }

    @Override
    @Nullable
    public Set<String> getExtraDetailedUpdateAuditFields()
    {
        ExpSchema.DataClassCategoryType categoryType = ExpSchema.DataClassCategoryType.fromString(_dataClass.getCategory());
        if (categoryType != null)
            return categoryType.additionalAuditFields;

        return null;
    }

    @Override
    public BaseColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RowId:
            {
                var c = wrapColumn(alias, getRealTable().getColumn("RowId"));
                // When no sorts are added by views, QueryServiceImpl.createDefaultSort() adds the primary key's default sort direction
                c.setSortDirection(Sort.SortDirection.DESC);
                c.setFk(new RowIdForeignKey(c));
                c.setKeyField(true);
                c.setHidden(true);
                return c;
            }

            case LSID:
            {
                var c = wrapColumn(alias, getRealTable().getColumn("LSID"));
                c.setHidden(true);
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                c.setCalculated(true); // So DataIterator won't consider the column as required. See c.isRequiredForInsert()
                return c;
            }

            case Name:
            {
                var c = wrapColumn(alias, getRealTable().getColumn(column.name()));
                // TODO: Name is editable in insert view, but not in update view
                String nameExpression = _dataClass.getNameExpression();
                c.setNullable(nameExpression != null);
                String desc = ExpMaterialTableImpl.appendNameExpressionDescription(c.getDescription(), nameExpression);
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
                var c = wrapColumn(alias, getRealTable().getColumn(column.name()));
                c.setFk(new UserIdForeignKey(getUserSchema()));
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                return c;
            }

            case DataClass:
            {
                var c = wrapColumn(alias, getRealTable().getColumn("classId"));
                c.setFk(QueryForeignKey.from(getUserSchema(), getContainerFilter()).schema(ExpSchema.SCHEMA_NAME).to(ExpSchema.TableType.DataClasses.name(), "RowId", "Name"));
                c.setShownInInsertView(false);
                c.setShownInUpdateView(false);
                c.setUserEditable(false);
                return c;
            }

            case Flag:
                return createFlagColumn(Column.Flag.toString());

            case Folder:
            {
                var c = wrapColumn("Container", getRealTable().getColumn("Container"));
                ContainerForeignKey.initColumn(c, getUserSchema());
                c.setLabel("Folder");
                c.setShownInDetailsView(false);
                return c;
            }
            case Alias:
                var aliasCol = wrapColumn("Alias", getRealTable().getColumn("LSID"));
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
                aliasCol.setConceptURI("http://www.labkey.org/exp/xml#alias");
                aliasCol.setPropertyURI("http://www.labkey.org/exp/xml#alias");
                return aliasCol;

            case Inputs:
                return createLineageColumn(this, alias, true);

            case Outputs:
                return createLineageColumn(this, alias, false);

            case DataFileUrl:
                var dataFileUrl = wrapColumn(alias, getRealTable().getColumn("DataFileUrl"));
                dataFileUrl.setUserEditable(false);
                DetailsURL url = new DetailsURL(new ActionURL(ExperimentController.ShowFileAction.class, getContainer()), Collections.singletonMap("rowId", "rowId"));
                dataFileUrl.setDisplayColumnFactory(new FileLinkDisplayColumn.Factory(url, getContainer(), FieldKey.fromParts("RowId")));

                return dataFileUrl;

            case Properties:
                return (BaseColumnInfo) createPropertiesColumn(alias);

            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    protected void populateColumns()
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

        addColumn(Column.LSID);
        var rowIdCol = addColumn(Column.RowId);
        var nameCol = addColumn(Column.Name);

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
        Collection<BaseColumnInfo> cols = new ArrayList<>(20);
        Set skipCols = new CaseInsensitiveHashSet();
        skipCols.add("lsid");
        skipCols.add("rowid");
        skipCols.add("name");
        skipCols.add("classid");
        for (ColumnInfo col : extTable.getColumns())
        {
            // Skip the lookup column itself, LSID, and exp.data.rowid -- it is added above
            String colName = col.getName();
            if (skipCols.contains(colName))
                continue;

            if (colName.equalsIgnoreCase("genid"))
            {
                ((BaseColumnInfo)col).setHidden(true);
                ((BaseColumnInfo)col).setUserEditable(false);
                ((BaseColumnInfo)col).setShownInDetailsView(false);
                ((BaseColumnInfo)col).setShownInInsertView(false);
                ((BaseColumnInfo)col).setShownInUpdateView(false);
            }
            String newName = col.getName();
            for (int i = 0; null != getColumn(newName); i++)
                newName = newName + i;

            // TODO use wrapColumnFromTable()
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

        for (var col : cols)
        {
            String propertyURI = col.getPropertyURI();
            // TODO use PropertyColumn.copyAttributes()
            if (null != propertyURI)
            {
                DomainProperty dp = properties.get(propertyURI);
                PropertyDescriptor pd = (null==dp) ? null : dp.getPropertyDescriptor();

                if (null != dp && null != pd)
                {
                    if (pd.getLookupQuery() != null || pd.getConceptURI() != null)
                    {
                        col.setFk(PdLookupForeignKey.create(schema, pd));
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

        addColumn(Column.DataFileUrl);

        addVocabularyDomains();
        addColumn(Column.Properties);

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

        // all columns from dataclass property table except name and classid
        Set<String> pCols = new CaseInsensitiveHashSet(provisioned.getColumnNameSet());
        pCols.remove("name");
        pCols.remove("classid");

        SQLFragment sql = new SQLFragment();
        sql.append("(SELECT ");
        String comma = "";
        for (String dataCol : dataCols)
        {
            sql.append(comma);
            sql.append("d.").append(dataCol);
            comma = ", ";
        }

        for (String pCol : pCols)
        {
            sql.append(comma);
            sql.append("p.").append(pCol);
        }
        sql.append(" FROM ");
        sql.append(_rootTable, "d");
        sql.append(" INNER JOIN ").append(provisioned, "p").append(" ON d.lsid = p.lsid");

        // WHERE
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getAliasedFilterSQLFragment(_rootTable.getSqlDialect(), columnMap, getFilter().getClauses());
        sql.append("\n").append(filterFrag).append(") ").append(alias);

        return sql;
    }

    public SQLFragment getAliasedFilterSQLFragment(SqlDialect dialect, Map<FieldKey, ? extends ColumnInfo> columnMap, List<SimpleFilter.FilterClause> clauses)
    {
        SQLFragment ret = new SQLFragment();

        if (null == clauses || 0 == clauses.size())
            return ret;

        String sAND = "WHERE ";

        for (SimpleFilter.FilterClause fc : clauses)
        {
            ret.append(sAND);
            ret.append("(");
            try
            {
                if (fc instanceof CompareType.EqualsCompareClause && fc.getFieldKeys().size() == 1 && fc.getFieldKeys().get(0).getName().equalsIgnoreCase("classid"))
                {
                    SQLFragment fragment = new SQLFragment("d.classid = ?");
                    fragment.addAll(fc.getParamVals());
                    ret.append(fragment);
                }
                else
                    ret.append(fc.toSQLFragment(columnMap, dialect));
            }
            catch (RuntimeSQLException e)
            {
                // Deal with unparseable filter values - see issue 23321
                if (e.getSQLException() instanceof SQLGenerationException)
                {
                    ret.append("0 = 1");
                }
                else
                {
                    throw e;
                }
            }
            ret.append(")");
            sAND = " AND ";
        }

        return ret;
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
    public @Nullable CaseInsensitiveHashSet skipProperties()
    {
        return super.skipProperties();
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

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        TableInfo propertiesTable = _dataClass.getTinfo();
        PersistDataIteratorBuilder step0 = new ExpDataIterators.PersistDataIteratorBuilder(data, this, propertiesTable, getUserSchema().getContainer(), getUserSchema().getUser(), Collections.emptyMap(), null)
            .setIndexFunction( lsids -> () ->
            {
                List<ExpDataImpl> expDatas = ExperimentServiceImpl.get().getExpDatasByLSID(lsids);
                if (expDatas != null)
                {
                    for (ExpDataImpl expData : expDatas)
                        expData.index(null);
                }
            });
        return new AliasDataIteratorBuilder(step0, getUserSchema().getContainer(), getUserSchema().getUser(), ExperimentService.get().getTinfoDataAliasMap());
    }

    private class PreTriggerDataIteratorBuilder implements DataIteratorBuilder
    {
        private static final int BATCH_SIZE = 100;

        private DataIteratorContext _context;
        private final DataIteratorBuilder _in;

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
            step0.setDebugName("step0");
            step0.selectAll(Sets.newCaseInsensitiveHashSet("lsid", "dataClass", "genId"));

            TableInfo expData = svc.getTinfoData();
            ColumnInfo lsidCol = expData.getColumn("lsid");

            // TODO: validate dataFileUrl column, it will be saved later

            // Generate LSID before inserting
            step0.addColumn(lsidCol, (Supplier) () -> svc.generateGuidLSID(c, ExpData.class));

            // auto gen a sequence number for genId - reserve BATCH_SIZE numbers at a time so we don't select the next sequence value for every row
            ColumnInfo genIdCol = _dataClass.getTinfo().getColumn(FieldKey.fromParts("genId"));
            final int batchSize = _context.getInsertOption().batch ? BATCH_SIZE : 1;
            step0.addSequenceColumn(genIdCol, _dataClass.getContainer(), ExpDataClassImpl.SEQUENCE_PREFIX, _dataClass.getRowId(), batchSize);

            // Ensure we have a dataClass column and it is of the right value
            // use materialized classId so that parameter binding works for both exp.data as well as materializeed table
            ColumnInfo classIdCol = _dataClass.getTinfo().getColumn("classId");
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

            // Table Counters
            ExpDataClassDataTableImpl queryTable = ExpDataClassDataTableImpl.this;
            DataIteratorBuilder step1 = ExpDataIterators.CounterDataIteratorBuilder.create(DataIteratorBuilder.wrap(step0), _dataClass.getContainer(), queryTable, ExpDataClassImpl.SEQUENCE_PREFIX, _dataClass.getRowId());

            // Generate names
            if (_dataClass.getNameExpression() != null)
            {
                step0.addColumn(new BaseColumnInfo("nameExpression", JdbcType.VARCHAR), (Supplier) () -> _dataClass.getNameExpression());
                step1 = new NameExpressionDataIteratorBuilder(step1,  ExpDataClassDataTableImpl.this);
            }

            return LoggingDataIterator.wrap(step1.getDataIterator(context));
        }
    }

    @Nullable
    @Override
    public QueryUpdateService getUpdateService()
    {
        return new DataClassDataUpdateService(this);
    }

    class DataClassDataUpdateService extends DefaultQueryUpdateService
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
        {
            return _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        }

        @Override
        public int loadRows(User user, Container container, DataIteratorBuilder rows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext) throws SQLException
        {
            return super.loadRows(user, container, rows, context, extraScriptContext);
        }

        @Override
        public int mergeRows(User user, Container container, DataIteratorBuilder rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            return _importRowsUsingDIB(user, container, rows, null, getDataIteratorContext(errors, InsertOption.MERGE, configParameters), extraScriptContext);
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
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
        protected Map<String, Object> _select(Container container, Object[] keys) throws ConversionException
        {
            TableInfo d = getDbTable();
            TableInfo t = ExpDataClassDataTableImpl.this._dataClass.getTinfo();

            SQLFragment sql = new SQLFragment()
                    .append("SELECT t.*, d.RowId, d.Name, d.ClassId, d.Container, d.Description, d.CreatedBy, d.Created, d.ModifiedBy, d.Modified")
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
            keys = new Object[] {lsid};
            TableInfo t = ExpDataClassDataTableImpl.this._dataClass.getTinfo();
            if (t.getColumnNameSet().stream().anyMatch(rowStripped::containsKey))
            {
                ret.putAll(Table.update(user, t, rowStripped, t.getColumn("lsid"), keys, null, Level.DEBUG));
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

            ret.put("lsid", lsid);
            return ret;
        }

        @Override
        public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            var ret = super.updateRows(user, container, rows, oldKeys, configParameters, extraScriptContext);

            /* setup mini dataiterator pipeline to process lineage */
            DataIterator di = _toDataIterator("updateRows.lineage", ret);
            ExpDataIterators.derive(user, container, di, false);

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
        protected int truncateRows(User user, Container container)
        {
            return ExperimentServiceImpl.get().truncateDataClass(_dataClass, user, container);
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

}
