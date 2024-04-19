/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditHandler;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.NameExpressionOptionService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSampleTypeTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.inventory.InventoryService;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUrls;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.security.permissions.MoveEntitiesPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;
import org.labkey.experiment.ExpDataIterators;
import org.labkey.experiment.ExpDataIterators.AliasDataIteratorBuilder;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.labkey.api.util.StringExpressionFactory.AbstractStringExpression.NullValueBehavior.NullResult;

public class ExpMaterialTableImpl extends ExpRunItemTableImpl<ExpMaterialTable.Column> implements ExpMaterialTable
{
    ExpSampleTypeImpl _ss;
    Set<String> _uniqueIdFields;

    public static final Set<String> MATERIAL_ALT_MERGE_KEYS;
    public static final Set<String> MATERIAL_ALT_UPDATE_KEYS;
    static {
        MATERIAL_ALT_MERGE_KEYS = Set.of(Column.MaterialSourceId.name(), Column.Name.name());
        MATERIAL_ALT_UPDATE_KEYS = Set.of(Column.LSID.name());
    }

    public ExpMaterialTableImpl(UserSchema schema, ContainerFilter cf, @Nullable ExpSampleType sampleType)
    {
        super(ExpSchema.TableType.Materials.name(), ExperimentServiceImpl.get().getTinfoMaterial(), schema, cf);
        setDetailsURL(new DetailsURL(new ActionURL(ExperimentController.ShowMaterialAction.class, schema.getContainer()), Collections.singletonMap("rowId", "rowId"), NullResult));
        setPublicSchemaName(ExpSchema.SCHEMA_NAME);
        addAllowablePermission(InsertPermission.class);
        addAllowablePermission(UpdatePermission.class);
        addAllowablePermission(MoveEntitiesPermission.class);
        setAllowedInsertOption(QueryUpdateService.InsertOption.MERGE);
        setSampleType(sampleType);
    }

    public Set<String> getUniqueIdFields()
    {
        if (_uniqueIdFields == null)
        {
            _uniqueIdFields = new CaseInsensitiveHashSet();
            _uniqueIdFields.addAll(getColumns().stream().filter(ColumnInfo::isUniqueIdField).map(ColumnInfo::getName).collect(Collectors.toSet()));
        }
        return _uniqueIdFields;
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        ColumnInfo result = super.resolveColumn(name);
        if (result == null)
        {
            if ("CpasType".equalsIgnoreCase(name))
                result = createColumn(Column.SampleSet.name(), Column.SampleSet);
            else if (Column.Property.name().equalsIgnoreCase(name))
                result = createPropertyColumn(Column.Property.name());
            else if (Column.QueryableInputs.name().equalsIgnoreCase(name))
                result = createColumn(Column.QueryableInputs.name(), Column.QueryableInputs);
        }
        return result;
    }

    @Override
    public ColumnInfo getExpObjectColumn()
    {
        var ret = wrapColumn("ExpMaterialTableImpl_object_", _rootTable.getColumn("objectid"));
        ret.setConceptURI(BuiltInColumnTypes.EXPOBJECTID_CONCEPT_URI);
        return ret;
    }

    @Override
    public AuditHandler getAuditHandler(AuditBehaviorType auditBehaviorType)
    {
        if (getUserSchema().getName().equalsIgnoreCase(SamplesSchema.SCHEMA_NAME))
        {
            // Special case sample auditing to help build a useful timeline view
            return SampleTypeServiceImpl.get();
        }

        return super.getAuditHandler(auditBehaviorType);
    }

    @Override
    public MutableColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case Folder ->
            {
                return wrapColumn(alias, _rootTable.getColumn("Container"));
            }
            case LSID ->
            {
                return wrapColumn(alias, _rootTable.getColumn(Column.LSID.name()));
            }
            case MaterialSourceId ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(Column.MaterialSourceId.name()));
                columnInfo.setFk(new LookupForeignKey(getLookupContainerFilter(), null, null, null, null, "RowId", "Name")
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        ExpSampleTypeTable sampleTypeTable = ExperimentService.get().createSampleTypeTable(ExpSchema.TableType.SampleSets.toString(), _userSchema, getLookupContainerFilter());
                        sampleTypeTable.populate();
                        return sampleTypeTable;
                    }

                    @Override
                    public StringExpression getURL(ColumnInfo parent)
                    {
                        return super.getURL(parent, true);
                    }
                });
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(true);
                return columnInfo;
            }
            case RootMaterialRowId ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(Column.RootMaterialRowId.name()));
                columnInfo.setFk(getExpSchema().getMaterialForeignKey(getLookupContainerFilter(), Column.RowId.name()));
                columnInfo.setLabel("Root Material");
                columnInfo.setUserEditable(false);

                // NK: Here we mark the column as not required AND nullable which is the opposite of the database where
                // a NOT NULL constraint is in place. This is done to avoid the RequiredValidator check upon updating a row.
                // See ExpMaterialValidatorIterator.
                columnInfo.setRequired(false);
                columnInfo.setNullable(true);

                return columnInfo;
            }
            case AliquotedFromLSID ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(Column.AliquotedFromLSID.name()));
                columnInfo.setSqlTypeName("lsidtype");
                columnInfo.setFk(getExpSchema().getMaterialForeignKey(getLookupContainerFilter(), Column.LSID.name()));
                columnInfo.setLabel("Aliquoted From Parent");
                return columnInfo;
            }
            case IsAliquot ->
            {
                String rootMaterialRowIdField = ExprColumn.STR_TABLE_ALIAS + "." + Column.RootMaterialRowId.name();
                String rowIdField = ExprColumn.STR_TABLE_ALIAS + "." + Column.RowId.name();
                ExprColumn columnInfo = new ExprColumn(this, FieldKey.fromParts(Column.IsAliquot.name()), new SQLFragment(
                        "(CASE WHEN (" + rootMaterialRowIdField + " = " + rowIdField + ") THEN ").append(getSqlDialect().getBooleanFALSE()).append(" ELSE ").append(getSqlDialect().getBooleanTRUE()).append(" END)"), JdbcType.BOOLEAN);
                columnInfo.setLabel("Is Aliquot");
                columnInfo.setDescription("Identifies if the material is a sample or an aliquot");
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(false);
                return columnInfo;
            }
            case Name ->
            {
                var nameCol = wrapColumn(alias, _rootTable.getColumn(column.toString()));
                // shut off this field in insert and update views if user specified names are not allowed
                if (!NameExpressionOptionService.get().getAllowUserSpecificNamesValue(getContainer()))
                {
                    nameCol.setShownInInsertView(false);
                    nameCol.setShownInUpdateView(false);
                }
                return nameCol;
            }
            case RawAmount ->
            {
                return wrapColumn(alias, _rootTable.getColumn(Column.StoredAmount.name()));
            }
            case StoredAmount ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(Column.StoredAmount.name()));
                columnInfo.setLabel("Amount");
                columnInfo.setImportAliasesSet(Set.of("Amount"));
                return columnInfo;
            }
            case RawUnits ->
            {
                return wrapColumn(alias, _rootTable.getColumn(Column.Units.name()));
            }
            case Units ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn(Column.Units.name()));
                columnInfo.setFk(new LookupForeignKey("Value", "Value")
                {
                    @Override
                    public @Nullable TableInfo getLookupTableInfo()
                    {
                        return getExpSchema().getTable(ExpSchema.MEASUREMENT_UNITS_TABLE);
                    }
                });
                return columnInfo;
            }
            case Description ->
            {
                return wrapColumn(alias, _rootTable.getColumn(Column.Description.name()));
            }
            case SampleSet ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("CpasType"));
                // NOTE: populateColumns() overwrites this with a QueryForeignKey.  Can this be removed?
                columnInfo.setFk(new LookupForeignKey(getContainerFilter(), null, null, null, null, "LSID", "Name")
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        ExpSampleTypeTable sampleTypeTable = ExperimentService.get().createSampleTypeTable(ExpSchema.TableType.SampleSets.toString(), _userSchema, getLookupContainerFilter());
                        sampleTypeTable.populate();
                        return sampleTypeTable;
                    }

                    @Override
                    public StringExpression getURL(ColumnInfo parent)
                    {
                        return super.getURL(parent, true);
                    }
                });
                return columnInfo;
            }
            case SourceProtocolLSID ->
            {
                // NOTE: This column is incorrectly named "Protocol", but we are keeping it for backwards compatibility to avoid breaking queries in hvtnFlow module
                ExprColumn columnInfo = new ExprColumn(this, ExpDataTable.Column.Protocol.toString(), new SQLFragment(
                        "(SELECT ProtocolLSID FROM " + ExperimentServiceImpl.get().getTinfoProtocolApplication() + " pa " +
                                " WHERE pa.RowId = " + ExprColumn.STR_TABLE_ALIAS + ".SourceApplicationId)"), JdbcType.VARCHAR);
                columnInfo.setSqlTypeName("lsidtype");
                columnInfo.setFk(getExpSchema().getProtocolForeignKey(getContainerFilter(), "LSID"));
                columnInfo.setLabel("Source Protocol");
                columnInfo.setDescription("Contains a reference to the protocol for the protocol application that created this sample");
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(true);
                return columnInfo;
            }
            case SourceProtocolApplication ->
            {
                var columnInfo = wrapColumn(alias, _rootTable.getColumn("SourceApplicationId"));
                columnInfo.setFk(getExpSchema().getProtocolApplicationForeignKey(getContainerFilter()));
                columnInfo.setUserEditable(false);
                columnInfo.setReadOnly(true);
                columnInfo.setHidden(true);
                columnInfo.setAutoIncrement(false);
                return columnInfo;
            }
            case SourceApplicationInput ->
            {
                var col = createEdgeColumn(alias, Column.SourceProtocolApplication, ExpSchema.TableType.MaterialInputs);
                col.setDescription("Contains a reference to the MaterialInput row between this ExpMaterial and it's SourceProtocolApplication");
                col.setHidden(true);
                return col;
            }
            case RunApplication ->
            {
                SQLFragment sql = new SQLFragment("(SELECT pa.rowId FROM ")
                        .append(ExperimentService.get().getTinfoProtocolApplication(), "pa")
                        .append(" WHERE pa.runId = ").append(ExprColumn.STR_TABLE_ALIAS).append(".runId")
                        .append(" AND pa.cpasType = ").appendValue(ExpProtocol.ApplicationType.ExperimentRunOutput)
                        .append(")");

                var col = new ExprColumn(this, alias, sql, JdbcType.INTEGER);
                col.setFk(getExpSchema().getProtocolApplicationForeignKey(getContainerFilter()));
                col.setDescription("Contains a reference to the ExperimentRunOutput protocol application of the run that created this sample");
                col.setUserEditable(false);
                col.setReadOnly(true);
                col.setHidden(true);
                return col;
            }
            case RunApplicationOutput ->
            {
                var col = createEdgeColumn(alias, Column.RunApplication, ExpSchema.TableType.MaterialInputs);
                col.setDescription("Contains a reference to the MaterialInput row between this ExpMaterial and it's RunOutputApplication");
                return col;
            }
            case Run ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("RunId"));
                ret.setReadOnly(true);
                return ret;
            }
            case RowId ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("RowId"));
                // When no sorts are added by views, QueryServiceImpl.createDefaultSort() adds the primary key's default sort direction
                ret.setSortDirection(Sort.SortDirection.DESC);
                ret.setFk(new RowIdForeignKey(ret));
                ret.setUserEditable(false);
                ret.setHidden(true);
                ret.setShownInInsertView(false);
                ret.setHasDbSequence(true);
                ret.setIsRootDbSequence(true);
                return ret;
            }
            case Property ->
            {
                return createPropertyColumn(alias);
            }
            case Flag ->
            {
                return createFlagColumn(alias);
            }
            case Created ->
            {
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            }
            case CreatedBy ->
            {
                return createUserColumn(alias, _rootTable.getColumn("CreatedBy"));
            }
            case Modified ->
            {
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            }
            case ModifiedBy ->
            {
                return createUserColumn(alias, _rootTable.getColumn("ModifiedBy"));
            }
            case Alias ->
            {
                return createAliasColumn(alias, ExperimentService.get()::getTinfoMaterialAliasMap);
            }
            case Inputs ->
            {
                return createLineageColumn(this, alias, true, false);
            }
            case QueryableInputs ->
            {
                return createLineageColumn(this, alias, true, true);
            }
            case Outputs ->
            {
                return createLineageColumn(this, alias, false, false);
            }
            case Properties ->
            {
                return createPropertiesColumn(alias);
            }
            case SampleState ->
            {
                boolean statusEnabled = SampleStatusService.get().supportsSampleStatus() && !SampleStatusService.get().getAllProjectStates(getContainer()).isEmpty();
                var ret = wrapColumn(alias, _rootTable.getColumn(column.name()));
                ret.setLabel("Status");
                ret.setHidden(!statusEnabled);
                ret.setShownInDetailsView(statusEnabled);
                ret.setShownInInsertView(statusEnabled);
                ret.setShownInUpdateView(statusEnabled);
                ret.setRemapMissingBehavior(SimpleTranslator.RemapMissingBehavior.Error);
                ret.setFk(new QueryForeignKey.Builder(getUserSchema(), getSampleStatusLookupContainerFilter())
                        .schema(getExpSchema()).table(ExpSchema.TableType.SampleStatus).display("Label"));
                return ret;
            }
            case AliquotCount ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("AliquotCount"));
                ret.setLabel("Aliquots Created Count");
                return ret;
            }
            case AliquotVolume ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("AliquotVolume"));
                ret.setLabel("Aliquot Total Amount");
                return ret;
            }
            case AvailableAliquotVolume ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("AvailableAliquotVolume"));
                ret.setLabel("Available Aliquot Amount");
                return ret;
            }
            case AvailableAliquotCount ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("AvailableAliquotCount"));
                ret.setLabel("Available Aliquot Count");
                return ret;
            }
            case AliquotUnit ->
            {
                var ret =  wrapColumn(alias, _rootTable.getColumn("AliquotUnit"));
                ret.setShownInDetailsView(false);
                return ret;
            }
            case MaterialExpDate ->
            {
                var ret = wrapColumn(alias, _rootTable.getColumn("MaterialExpDate"));
                ret.setLabel("Expiration Date");
                ret.setShownInDetailsView(true);
                ret.setShownInInsertView(true);
                ret.setShownInUpdateView(true);
                return ret;
            }
            default -> throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    public MutableColumnInfo createPropertyColumn(String alias)
    {
        var ret = super.createPropertyColumn(alias);
        if (_ss != null)
        {
            final TableInfo t = _ss.getTinfo();
            if (t != null)
            {
                ret.setFk(new LookupForeignKey()
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return t;
                    }

                    @Override
                    protected ColumnInfo getPkColumn(TableInfo table)
                    {
                        return t.getColumn("lsid");
                    }
                });
            }
        }
        ret.setIsUnselectable(true);
        ret.setDescription("A holder for any custom fields associated with this sample");
        ret.setHidden(true);
        return ret;
    }

    private void setSampleType(@Nullable ExpSampleType st)
    {
        checkLocked();
        if (_ss != null)
        {
            throw new IllegalStateException("Cannot unset sample type");
        }
        if (st != null && !(st instanceof ExpSampleTypeImpl))
        {
            throw new IllegalArgumentException("Expected sample type to be an instance of " + ExpSampleTypeImpl.class.getName() + " but was a " + st.getClass().getName());
        }
        _ss = (ExpSampleTypeImpl) st;
        if (_ss != null)
        {
            setPublicSchemaName(SamplesSchema.SCHEMA_NAME);
            setName(st.getName());

            String description = _ss.getDescription();
            if (StringUtils.isEmpty(description))
                description = "Contains one row per sample in the " + _ss.getName() + " sample type";
            setDescription(description);

            if (canUserAccessPhi())
            {
                ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getImportSamplesURL(getContainer(), _ss.getName());
                setImportURL(new DetailsURL(url));
            }
        }
    }

    public ExpSampleType getSampleType()
    {
        return _ss;
    }

    @Override
    protected void populateColumns()
    {
        var st = getSampleType();
        var rowIdCol = addColumn(Column.RowId);
        addColumn(Column.MaterialSourceId);
        addColumn(Column.SourceProtocolApplication);
        addColumn(Column.SourceApplicationInput);
        addColumn(Column.RunApplication);
        addColumn(Column.RunApplicationOutput);
        addColumn(Column.SourceProtocolLSID);

        var nameCol = addColumn(Column.Name);
        if (st != null && st.hasNameAsIdCol())
        {
            // Show the Name field but don't mark is as required when using name expressions
            if (st.hasNameExpression())
            {
                var nameExpression = st.getNameExpression();
                nameCol.setNameExpression(nameExpression);
                nameCol.setNullable(true);
                String nameExpressionPreview = getExpNameExpressionPreview(getUserSchema().getSchemaName(), st.getName(), getUserSchema().getUser());
                String desc = appendNameExpressionDescription(nameCol.getDescription(), nameExpression, nameExpressionPreview);
                nameCol.setDescription(desc);
            }
            else
            {
                nameCol.setNullable(false);
            }
        }
        else
        {
            nameCol.setReadOnly(true);
            nameCol.setShownInInsertView(false);
        }

        addColumn(Column.Alias);
        addColumn(Column.Description);

        var typeColumnInfo = addColumn(Column.SampleSet);
        typeColumnInfo.setFk(new QueryForeignKey(_userSchema, getContainerFilter(), ExpSchema.SCHEMA_NAME, getContainer(), null, getUserSchema().getUser(), ExpSchema.TableType.SampleSets.name(), "lsid", null)
        {
            @Override
            protected ContainerFilter getLookupContainerFilter()
            {
                // Be sure that we can resolve the sample type if it's defined in a separate container.
                // Same as CurrentPlusProjectAndShared but includes SampleSet's container as well.
                // Issue 37982: Sample Type: Link to precursor sample type does not resolve correctly if sample has
                // parents in current sample type and a sample type in the parent container
                Set<Container> containers = new HashSet<>();
                if (null != st)
                    containers.add(st.getContainer());
                containers.add(getContainer());
                if (getContainer().getProject() != null)
                    containers.add(getContainer().getProject());
                containers.add(ContainerManager.getSharedContainer());
                ContainerFilter cf = new ContainerFilter.CurrentPlusExtras(_userSchema.getContainer(), _userSchema.getUser(), containers);

                if (null != _containerFilter && _containerFilter.getType() != ContainerFilter.Type.Current)
                    cf = new UnionContainerFilter(_containerFilter, cf);
                return cf;
            }
        });

        typeColumnInfo.setReadOnly(true);
        typeColumnInfo.setUserEditable(false);
        typeColumnInfo.setShownInInsertView(false);

        addColumn(Column.MaterialExpDate);

        var folderCol = addContainerColumn(Column.Folder, null);
        boolean hasProductProjects = getContainer().hasProductProjects();
        if (hasProductProjects)
            folderCol.setLabel("Project");

        var runCol = addColumn(Column.Run);
        runCol.setFk(new ExpSchema(_userSchema.getUser(), getContainer()).getRunIdForeignKey(getContainerFilter()));
        runCol.setShownInInsertView(false);
        runCol.setShownInUpdateView(false);

        var colLSID = addColumn(Column.LSID);
        colLSID.setHidden(true);
        colLSID.setReadOnly(true);
        colLSID.setUserEditable(false);
        colLSID.setShownInInsertView(false);
        colLSID.setShownInDetailsView(false);
        colLSID.setShownInUpdateView(false);

        var rootRowId = addColumn(Column.RootMaterialRowId);
        rootRowId.setHidden(true);
        rootRowId.setReadOnly(true);
        rootRowId.setUserEditable(false);
        rootRowId.setShownInInsertView(false);
        rootRowId.setShownInDetailsView(false);
        rootRowId.setShownInUpdateView(false);

        var aliquotParentLSID = addColumn(Column.AliquotedFromLSID);
        aliquotParentLSID.setHidden(true);
        aliquotParentLSID.setReadOnly(true);
        aliquotParentLSID.setUserEditable(false);
        aliquotParentLSID.setShownInInsertView(false);
        aliquotParentLSID.setShownInDetailsView(false);
        aliquotParentLSID.setShownInUpdateView(false);

        addColumn(Column.IsAliquot);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);

        List<FieldKey> defaultCols = new ArrayList<>();
        defaultCols.add(FieldKey.fromParts(Column.Name));
        defaultCols.add(FieldKey.fromParts(Column.MaterialExpDate));
        if (hasProductProjects)
            defaultCols.add(FieldKey.fromParts(Column.Folder));
        defaultCols.add(FieldKey.fromParts(Column.Run));

        if (st == null)
            defaultCols.add(FieldKey.fromParts(Column.SampleSet));

        addColumn(Column.Flag);

        var statusColInfo = addColumn(Column.SampleState);
        boolean statusEnabled = SampleStatusService.get().supportsSampleStatus() && !SampleStatusService.get().getAllProjectStates(getContainer()).isEmpty();
        statusColInfo.setShownInDetailsView(statusEnabled);
        statusColInfo.setShownInInsertView(statusEnabled);
        statusColInfo.setShownInUpdateView(statusEnabled);
        statusColInfo.setHidden(!statusEnabled);
        statusColInfo.setRemapMissingBehavior(SimpleTranslator.RemapMissingBehavior.Error);
        if (statusEnabled)
            defaultCols.add(FieldKey.fromParts(Column.SampleState));
        statusColInfo.setFk(new QueryForeignKey.Builder(getUserSchema(), getSampleStatusLookupContainerFilter())
                .schema(getExpSchema()).table(ExpSchema.TableType.SampleStatus).display("Label"));

        // TODO is this a real Domain???
        if (st != null && !"urn:lsid:labkey.com:SampleSource:Default".equals(st.getDomain().getTypeURI()))
        {
            defaultCols.add(FieldKey.fromParts(Column.Flag));
            addSampleTypeColumns(st, defaultCols);

            setName(_ss.getName());

            ActionURL gridUrl = new ActionURL(ExperimentController.ShowSampleTypeAction.class, getContainer());
            gridUrl.addParameter("rowId", st.getRowId());
            setGridURL(new DetailsURL(gridUrl));
        }

        addColumn(Column.AliquotCount);
        addColumn(Column.AliquotVolume);
        addColumn(Column.AliquotUnit);
        addColumn(Column.AvailableAliquotCount);
        addColumn(Column.AvailableAliquotVolume);

        addColumn(Column.StoredAmount);
        defaultCols.add(FieldKey.fromParts(Column.StoredAmount));

        addColumn(Column.Units);
        defaultCols.add(FieldKey.fromParts(Column.Units));

        var rawAmountColumn = addColumn(Column.RawAmount);
        rawAmountColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo)
                {
                    @Override
                    public void addQueryFieldKeys(Set<FieldKey> keys)
                    {
                        super.addQueryFieldKeys(keys);
                        keys.add(FieldKey.fromParts(Column.StoredAmount));

                    }
                };
            }
        });
        rawAmountColumn.setHidden(true);
        rawAmountColumn.setShownInDetailsView(false);
        rawAmountColumn.setShownInInsertView(false);
        rawAmountColumn.setShownInUpdateView(false);

        var rawUnitsColumn = addColumn(Column.RawUnits);
        rawUnitsColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo)
                {
                    @Override
                    public void addQueryFieldKeys(Set<FieldKey> keys)
                    {
                        super.addQueryFieldKeys(keys);
                        keys.add(FieldKey.fromParts(Column.Units));

                    }
                };
            }
        });
        rawUnitsColumn.setHidden(true);
        rawUnitsColumn.setShownInDetailsView(false);
        rawUnitsColumn.setShownInInsertView(false);
        rawUnitsColumn.setShownInUpdateView(false);

        if (InventoryService.get() != null && (st == null || !st.isMedia()))
            defaultCols.addAll(InventoryService.get().addInventoryStatusColumns(st == null ? null : st.getMetricUnit(), this, getContainer(), _userSchema.getUser()));

        addVocabularyDomains();
        addColumn(Column.Properties);

        var colInputs = addColumn(Column.Inputs);
        addMethod("Inputs", new LineageMethod(colInputs, true), Set.of(colInputs.getFieldKey()));

        var colOutputs = addColumn(Column.Outputs);
        addMethod("Outputs", new LineageMethod(colOutputs, false), Set.of(colOutputs.getFieldKey()));

        addExpObjectMethod();

        ActionURL detailsUrl = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        DetailsURL url = new DetailsURL(detailsUrl, Collections.singletonMap("rowId", "RowId"), NullResult);
        nameCol.setURL(url);
        rowIdCol.setURL(url);
        setDetailsURL(url);

        if (canUserAccessPhi())
        {
            ActionURL updateActionURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getUpdateMaterialQueryRowAction(getContainer(), this);
            setUpdateURL(new DetailsURL(updateActionURL, Collections.singletonMap("RowId", "RowId")));

            ActionURL insertActionURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getInsertMaterialQueryRowAction(getContainer(), this);
            setInsertURL(new DetailsURL(insertActionURL));
        }
        else
        {
            setImportURL(LINK_DISABLER);
            setInsertURL(LINK_DISABLER);
            setUpdateURL(LINK_DISABLER);
        }

        setTitleColumn(Column.Name.toString());

        setDefaultVisibleColumns(defaultCols);

        if (null != _ss)
        {
            MutableColumnInfo lineageLookup = ClosureQueryHelper.createLineageLookupColumnInfo("Ancestors", this, _rootTable.getColumn("rowid"), _ss);
            addColumn(lineageLookup);
        }
    }

    private ContainerFilter getSampleStatusLookupContainerFilter()
    {
        // The default lookup container filter is Current, but we want to have the default be CurrentPlusProjectAndShared
        // for the sample status lookup since in the app project context we want to share status definitions across
        // a given project instead of creating duplicate statuses in each subfolder project.
        ContainerFilter.Type type = QueryService.get().getContainerFilterTypeForLookups(getContainer());
        type = type == null ? ContainerFilter.Type.CurrentPlusProjectAndShared : type;
        return type.create(getUserSchema());
    }

    @Override
    public Domain getDomain()
    {
        return _ss == null ? null : _ss.getDomain();
    }

    public static String appendNameExpressionDescription(String currentDescription, String nameExpression, String nameExpressionPreview)
    {
        if (nameExpression == null)
            return currentDescription;

        StringBuilder sb = new StringBuilder();
        if (currentDescription != null && !currentDescription.isEmpty())
        {
            sb.append(currentDescription);
            if (!currentDescription.endsWith("."))
                sb.append(".");
            sb.append("\n");
        }

        sb.append("\nIf not provided, a unique name will be generated from the expression:\n");
        sb.append(nameExpression);
        sb.append(".");
        if (!StringUtils.isEmpty(nameExpressionPreview))
        {
            sb.append("\nExample of name that will be generated from the current pattern: \n");
            sb.append(nameExpressionPreview);
        }

        return sb.toString();
    }

    private void addSampleTypeColumns(ExpSampleType st, List<FieldKey> visibleColumns)
    {
        TableInfo dbTable = ((ExpSampleTypeImpl)st).getTinfo();
        if (null == dbTable)
            return;

        UserSchema schema = getUserSchema();
        Domain domain = st.getDomain();
        ColumnInfo rowIdColumn = getColumn(Column.RowId);
        ColumnInfo lsidColumn = getColumn(Column.LSID);
        ColumnInfo nameColumn = getColumn(Column.Name);

        visibleColumns.remove(FieldKey.fromParts(Column.Run.name()));

        // When not using name expressions, mark the ID columns as required.
        // NOTE: If not explicitly set, the first domain property will be chosen as the ID column.
        final List<DomainProperty> idCols = st.hasNameExpression() ? Collections.emptyList() : st.getIdCols();

        Set<FieldKey> mvColumns = domain.getProperties().stream()
                .filter(ImportAliasable::isMvEnabled)
                .map(dp -> FieldKey.fromParts(dp.getPropertyDescriptor().getMvIndicatorStorageColumnName()))
                .collect(Collectors.toSet());

        for (ColumnInfo dbColumn : dbTable.getColumns())
        {
            // Don't include PHI columns in full text search index
            // CONSIDER: Can we move this to a base class? Maybe in .addColumn()
            if (schema.getUser().isSearchUser() && !dbColumn.getPHI().isLevelAllowed(PHI.NotPHI))
                continue;

            if (
                rowIdColumn.getFieldKey().equals(dbColumn.getFieldKey()) ||
                lsidColumn.getFieldKey().equals(dbColumn.getFieldKey()) ||
                nameColumn.getFieldKey().equals(dbColumn.getFieldKey())
            )
            {
                continue;
            }

            // TODO this seems bad to me, why isn't this done in ss.getTinfo()
            if (dbColumn.getName().equalsIgnoreCase("genid"))
            {
                ((BaseColumnInfo)dbColumn).setHidden(true);
                ((BaseColumnInfo)dbColumn).setUserEditable(false);
                ((BaseColumnInfo)dbColumn).setShownInDetailsView(false);
                ((BaseColumnInfo)dbColumn).setShownInInsertView(false);
                ((BaseColumnInfo)dbColumn).setShownInUpdateView(false);
            }

            // TODO missing values? comments? flags?
            DomainProperty dp = domain.getPropertyByURI(dbColumn.getPropertyURI());
            var propColumn = copyColumnFromJoinedTable(null==dp?dbColumn.getName():dp.getName(), dbColumn);
            if (null != dp)
            {
                PropertyColumn.copyAttributes(schema.getUser(), propColumn, dp.getPropertyDescriptor(), schema.getContainer(),
                    SchemaKey.fromParts("samples"), st.getName(), FieldKey.fromParts("RowId"), null, getLookupContainerFilter());

                if (idCols.contains(dp))
                {
                    propColumn.setNullable(false);
                    propColumn.setDisplayColumnFactory(new IdColumnRendererFactory());
                }

                // Issue 38341: domain designer advanced settings 'show in default view' setting is not respected
                if (!propColumn.isHidden())
                {
                    visibleColumns.add(propColumn.getFieldKey());
                }

                if (propColumn.isMvEnabled())
                {
                    // The column in the physical table has a "_MVIndicator" suffix, but we want to expose
                    // it with a "MVIndicator" suffix (no underscore)
                    var mvColumn = new AliasedColumn(this, dp.getName() + MvColumn.MV_INDICATOR_SUFFIX,
                            StorageProvisioner.get().getMvIndicatorColumn(dbTable, dp.getPropertyDescriptor(), "No MV column found for '" + dp.getName() + "' in sample type '" + getName() + "'"));
                    mvColumn.setLabel(dp.getLabel() != null ? dp.getLabel() : dp.getName() + " MV Indicator");
                    mvColumn.setSqlTypeName("VARCHAR");
                    mvColumn.setPropertyURI(dp.getPropertyURI());
                    mvColumn.setNullable(true);
                    mvColumn.setUserEditable(false);
                    mvColumn.setHidden(true);
                    mvColumn.setMvIndicatorColumn(true);

                    addColumn(mvColumn);
                    propColumn.setMvColumnName(FieldKey.fromParts(dp.getName() + MvColumn.MV_INDICATOR_SUFFIX));
                }
            }

            if (!mvColumns.contains(propColumn.getFieldKey()))
                addColumn(propColumn);
        }

        setDefaultVisibleColumns(visibleColumns);
    }

    // These are mostly fields that are wrapped by fields with different names (see createColumn())
    // we could handle each case separately, but this is easier
    static final Set<FieldKey> wrappedFieldKeys = Set.of(
            new FieldKey(null, "objectid"),
            new FieldKey(null, "RowId"),
            new FieldKey(null, "LSID"),                 // Flag
            new FieldKey(null, "SourceApplicationId"),  // SourceProtocolApplication
            new FieldKey(null, "runId"),                // Run, RunApplication
            new FieldKey(null, "CpasType"));            // SampleSet
    static final Set<FieldKey> ALL_COLUMNS = Set.of();

    private @NotNull Set<FieldKey> computeInnerSelectedColumns(Set<FieldKey> selectedColumns)
    {
        if (null == selectedColumns)
            return ALL_COLUMNS;
        selectedColumns = new TreeSet<>(selectedColumns);
        if (selectedColumns.contains(new FieldKey(null, ExpMaterial.ALIQUOTED_FROM_INPUT)))
            selectedColumns.add(new FieldKey(null, Column.AliquotedFromLSID.name()));
        if (selectedColumns.contains(new FieldKey(null, Column.IsAliquot.name())))
            selectedColumns.add(new FieldKey(null, Column.RootMaterialRowId.name()));
        selectedColumns.addAll(wrappedFieldKeys);
        if (null != getFilter())
            selectedColumns.addAll(getFilter().getAllFieldKeys());
        return selectedColumns;
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        return getFromSQL(alias, null);
    }

    @Override
    public SQLFragment getFromSQLExpanded(String alias, Set<FieldKey> selectedColumns)
    {
        SQLFragment sql = new SQLFragment("(");
        boolean usedMaterialized;

        // SELECT FROM
        if (null != _ss && null != _ss.getTinfo() && !getExpSchema().getDbSchema().getScope().isTransactionActive())
        {
            sql.append(getMaterializedSQL());
            usedMaterialized = true;
        }
        else
        {
            sql.append(getJoinSQL(selectedColumns));
            usedMaterialized = false;
        }

        // WHERE
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable, null);
        sql.append("\n").append(filterFrag);
        if (_ss != null && !usedMaterialized)
        {
            if (!filterFrag.isEmpty())
                sql.append(" AND ");
            else
                sql.append(" WHERE ");
            sql.append("CpasType = ").appendValue(_ss.getLSID());
        }
        sql.append(") ").append(alias);

        return sql;
    }

    static final BlockingCache<String,MaterializedQueryHelper> _materializedQueries = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.HOUR, "materialized sample types", null);
    static final Map<String, AtomicLong> _invalidationCounters = Collections.synchronizedMap(new HashMap<>());
    static final AtomicBoolean initializedListeners = new AtomicBoolean(false);

    // used by SampleTypeServiceImpl.refreshSampleTypeMaterializedView()
    public static void refreshMaterializedView(final String lsid, boolean schemaChange)
    {
        /* NOTE: MaterializedQueryHelper can detect data changes and refresh the materialized view using the provided SQL.
         * It does not handle schema changes where the SQL itself needs to be updated.  In this case, we remove the
         * MQH from the cache to force the SQL to be regenerated.
         */
        var scope = ExperimentServiceImpl.getExpSchema().getScope();
        if (schemaChange)
            scope.addCommitTask(() -> _materializedQueries.remove(lsid), DbScope.CommitTaskOption.POSTCOMMIT);
        scope.addCommitTask(new RefreshMaterializedViewRunnable(lsid), DbScope.CommitTaskOption.POSTCOMMIT);
    }

    private static class RefreshMaterializedViewRunnable implements Runnable
    {
        private final String _lsid;

        public RefreshMaterializedViewRunnable(String lsid)
        {
            _lsid = lsid;
        }

        @Override
        public void run()
        {
            getInvalidateCounter(_lsid).incrementAndGet();
        }

        @Override
        public boolean equals(Object obj)
        {
            return obj instanceof RefreshMaterializedViewRunnable other && _lsid.equals(other._lsid);
        }
    }

    private static AtomicLong getInvalidateCounter(String lsid)
    {
        if (!initializedListeners.getAndSet(true))
        {
            CacheManager.addListener(_invalidationCounters::clear);
        }
        return _invalidationCounters.computeIfAbsent(lsid, (unused) ->
                new AtomicLong(System.currentTimeMillis())
        );
    }

    /* SELECT and JOIN, does not include WHERE, same as getJoinSQL() */
    private SQLFragment getMaterializedSQL()
    {
        if (null == _ss)
            return getJoinSQL(null);

        var mqh = _materializedQueries.get(_ss.getLSID(), null, (unusedKey, unusedArg) ->
        {
            /* NOTE: MaterializedQueryHelper does have a pattern to help with detecting schema changes.
             * Previously it has been used on non-provisioned tables.  It might be helpful to have a pattern,
             * even if just to help with race-conditions.
             *
             * Maybe have a callback to generate the SQL dynamically, and verify that the sql is unchanged.
             */
            SQLFragment viewSql = getJoinSQL(null).append(" WHERE CpasType = ").appendValue(_ss.getLSID());
            SQLFragment provisioned = Objects.requireNonNull(_ss.getTinfo().getSQLName());
            return new MaterializedQueryHelper.Builder("", getExpSchema().getDbSchema().getScope(), viewSql)
                .addIndex("CREATE UNIQUE INDEX uq_${NAME}_rowid ON temp.${NAME} (rowid)")
                .addIndex("CREATE UNIQUE INDEX uq_${NAME}_lsid ON temp.${NAME} (lsid)")
                .addIndex("CREATE INDEX idx_${NAME}_container ON temp.${NAME} (container)")
                .addIndex("CREATE INDEX idx_${NAME}_root ON temp.${NAME} (rootmaterialrowid)")
                .addInvalidCheck(() -> String.valueOf(getInvalidateCounter(_ss.getLSID()).get()))
                .upToDateSql(new SQLFragment("SELECT CAST(COUNT(*) AS VARCHAR) FROM ").append(provisioned))  // MAX(modified) would probably be better if it were a) in the materialized table b) indexed
                .build();
        });
        return new SQLFragment("SELECT * FROM ").append(mqh.getFromSql("_cached_view_"));
    }

    /* SELECT and JOIN, does not include WHERE */
    private SQLFragment getJoinSQL(Set<FieldKey> selectedColumns)
    {
        TableInfo provisioned = null == _ss ? null : _ss.getTinfo();
        Set<String> provisionedCols = new CaseInsensitiveHashSet(provisioned != null ? provisioned.getColumnNameSet() : Collections.emptySet());
        provisionedCols.remove(Column.RowId.name());
        provisionedCols.remove(Column.LSID.name());
        provisionedCols.remove(Column.Name.name());
        boolean hasProvisionedColumns = containsProvisionedColumns(selectedColumns, provisionedCols);

        boolean hasSampleColumns = false;
        boolean hasAliquotColumns = false;

        Set<String> materialCols = new CaseInsensitiveHashSet(_rootTable.getColumnNameSet());
        selectedColumns = computeInnerSelectedColumns(selectedColumns);

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<ExpMaterialTableImpl.getJoinSQL(" + (null == _ss ? "" : _ss.getName()) + ")>", getSqlDialect());
        sql.append("SELECT ");
        String comma = "";
        for (String materialCol : materialCols)
        {
            // don't need to generate SQL for columns that aren't selected
            if (ALL_COLUMNS == selectedColumns || selectedColumns.contains(new FieldKey(null, materialCol)))
            {
                sql.append(comma).append("m.").appendIdentifier(materialCol);
                comma = ", ";
            }
        }
        if (null != provisioned && hasProvisionedColumns)
        {
            for (ColumnInfo propertyColumn : provisioned.getColumns())
            {
                // don't select twice
                if (
                    Column.RowId.name().equalsIgnoreCase(propertyColumn.getColumnName()) ||
                    Column.LSID.name().equalsIgnoreCase(propertyColumn.getColumnName()) ||
                    Column.Name.name().equalsIgnoreCase(propertyColumn.getColumnName())
                )
                {
                    continue;
                }

                // don't need to generate SQL for columns that aren't selected
                if (ALL_COLUMNS == selectedColumns || selectedColumns.contains(propertyColumn.getFieldKey()) || propertyColumn.isMvIndicatorColumn())
                {
                    sql.append(comma);
                    boolean rootField = StringUtils.isEmpty(propertyColumn.getDerivationDataScope())
                            || ExpSchema.DerivationDataScopeType.ParentOnly.name().equalsIgnoreCase(propertyColumn.getDerivationDataScope());
                    if ("genid".equalsIgnoreCase(propertyColumn.getColumnName()) || propertyColumn.isUniqueIdField())
                    {
                        sql.append(propertyColumn.getValueSql("m_aliquot")).append(" AS ").appendIdentifier(propertyColumn.getSelectName());
                        hasAliquotColumns = true;
                    }
                    else if (rootField)
                    {
                        sql.append(propertyColumn.getValueSql("m_sample")).append(" AS ").appendIdentifier(propertyColumn.getSelectName());
                        hasSampleColumns = true;
                    }
                    else
                    {
                        sql.append(propertyColumn.getValueSql("m_aliquot")).append(" AS ").appendIdentifier(propertyColumn.getSelectName());
                        hasAliquotColumns = true;
                    }
                    comma = ", ";
                }
            }
        }

        sql.append("\nFROM ");
        sql.append(_rootTable, "m");
        if (hasSampleColumns)
            sql.append(" INNER JOIN ").append(provisioned, "m_sample").append(" ON m.RootMaterialRowId = m_sample.RowId");
        if (hasAliquotColumns)
            sql.append(" INNER JOIN ").append(provisioned, "m_aliquot").append(" ON m.RowId = m_aliquot.RowId");

        sql.appendComment("</ExpMaterialTableImpl.getJoinSQL()>", getSqlDialect());
        return sql;
    }

    private class IdColumnRendererFactory implements DisplayColumnFactory
    {
        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new IdColumnRenderer(colInfo);
        }
    }

    private class IdColumnRenderer extends DataColumn
    {
        public IdColumnRenderer(ColumnInfo col)
        {
            super(col);
        }

        @Override
        protected boolean isDisabledInput(RenderContext ctx)
        {
            return !super.isDisabledInput() && ctx.getMode() != DataRegion.MODE_INSERT;
        }
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new SampleTypeUpdateServiceDI(this, _ss);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (_ss == null)
        {
            // Allow read and delete for exp.Materials.
            // Don't allow insert/update on exp.Materials without a sample type.
            if (perm == DeletePermission.class || perm == ReadPermission.class)
                return getContainer().hasPermission(user, perm);
            return false;
        }

        if (_ss.isMedia() && perm == ReadPermission.class)
            return getContainer().hasPermission(user, MediaReadPermission.class);

        return super.hasPermission(user, perm);
    }

    @NotNull
    @Override
    public Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices()
    {
        // Rewrite the "idx_material_ak" unique index over "Folder", "SampleSet", "Name" to just "Name"
        // Issue 25397: Don't include the "idx_material_ak" index if the "Name" column hasn't been added to the table.
        // Some FKs to ExpMaterialTable don't include the "Name" column (e.g. NabBaseTable.Specimen)
        Map<String, Pair<IndexType, List<ColumnInfo>>> ret = new HashMap<>(super.getUniqueIndices());
        if (getColumn("Name") != null)
            ret.put("idx_material_ak", Pair.of(IndexType.Unique, Arrays.asList(getColumn("Name"))));
        else
            ret.remove("idx_material_ak");
        return Collections.unmodifiableMap(ret);
    }


    //
    // UpdatableTableInfo
    //


    @Override
    public @Nullable Integer getOwnerObjectId()
    {
        return OntologyManager.ensureObject(_ss.getContainer(), _ss.getLSID(), (Integer) null);
    }

    @Nullable
    @Override
    public CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<>();

        if (null != getRealTable().getColumn("container") && null != getColumn("folder"))
        {
            m.put("container", "folder");
        }

        for (ColumnInfo col : getColumns())
        {
            if (col.getMvColumnName() != null)
                m.put(col.getName() + "_" + MvColumn.MV_INDICATOR_SUFFIX, col.getMvColumnName().getName());
        }

        return m;
    }

    @Override
    public Set<String> getAltMergeKeys(DataIteratorContext context)
    {
        if (context.getInsertOption().updateOnly && context.getConfigParameterBoolean(ExperimentService.QueryOptions.UseLsidForUpdate))
            return getAltKeysForUpdate();

        return MATERIAL_ALT_MERGE_KEYS;
    }

    @NotNull
    @Override
    public Set<String> getAltKeysForUpdate()
    {
        return MATERIAL_ALT_UPDATE_KEYS;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        TableInfo propertiesTable = _ss.getTinfo();

        int sampleTypeObjectId = requireNonNull(getOwnerObjectId());

        // TODO: subclass PersistDataIteratorBuilder to index Materials! not DataClass!
        try
        {
            var persist = new ExpDataIterators.PersistDataIteratorBuilder(data, this, propertiesTable, _ss, getUserSchema().getContainer(), getUserSchema().getUser(), _ss.getImportAliasMap(), sampleTypeObjectId)
                    .setFileLinkDirectory("sampletype");
            SearchService searchService = SearchService.get();
            if (null != searchService)
            {
                persist.setIndexFunction(lsids -> propertiesTable.getSchema().getScope().addCommitTask(() -> {
                    ListUtils.partition(lsids, 100).forEach(sublist ->
                        searchService.defaultTask().addRunnable(SearchService.PRIORITY.group, () ->
                        {
                            for (ExpMaterialImpl expMaterial : ExperimentServiceImpl.get().getExpMaterialsByLsid(sublist))
                                expMaterial.index(searchService.defaultTask());
                        })
                    );
                }, DbScope.CommitTaskOption.POSTCOMMIT)
                );
            }

            DataIteratorBuilder builder = LoggingDataIterator.wrap(persist);
            return LoggingDataIterator.wrap(new AliasDataIteratorBuilder(builder, getUserSchema().getContainer(), getUserSchema().getUser(), ExperimentService.get().getTinfoMaterialAliasMap()));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    static final Set<String> excludeFromDetailedAuditField;
    static
    {
        var set = new CaseInsensitiveHashSet();
        set.addAll(TableInfo.defaultExcludedDetailedUpdateAuditFields);
        set.addAll(ExpDataIterators.NOT_FOR_UPDATE);
        // We don't want the inventory columns to show up in the sample timeline audit record;
        // they are captured in their own audit record.
        set.addAll(InventoryService.INVENTORY_STATUS_COLUMN_NAMES);
        excludeFromDetailedAuditField = Collections.unmodifiableSet(set);
    }

    @Override
    public @NotNull Set<String> getExcludedDetailedUpdateAuditFields()
    {
        // uniqueId fields don't change in reality, so exclude them from the audit updates
        Set<String> excluded = new CaseInsensitiveHashSet();
        excluded.addAll(this.getUniqueIdFields());
        excluded.addAll(excludeFromDetailedAuditField);
        return excluded;
    }

    @Override
    public List<Pair<String, String>> getImportTemplates(ViewContext ctx)
    {
        // respect any metadata overrides
        if (getRawImportTemplates() != null)
            return super.getImportTemplates(ctx);

        List<Pair<String, String>> templates = new ArrayList<>();
        ActionURL url = PageFlowUtil.urlProvider(QueryUrls.class).urlCreateExcelTemplate(ctx.getContainer(), getPublicSchemaName(), getName());
        url.addParameter("headerType", ColumnHeaderType.DisplayFieldKey.name());
        try
        {
            if (getSampleType() != null && !getSampleType().getImportAliasMap().isEmpty())
            {
                for (String aliasKey : getSampleType().getImportAliasMap().keySet())
                    url.addParameter("includeColumn", aliasKey);
            }
        }
        catch (IOException e)
        {}
        templates.add(Pair.of("Download Template", url.toString()));
        return templates;
    }

    @Override
    public void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors)
    {
        if (SamplesSchema.SCHEMA_NAME.equals(schema.getName()))
        {
            Collection<TableType> metadata = QueryService.get().findMetadataOverride(schema, SamplesSchema.SCHEMA_METADATA_NAME, false, false, errors, null);
            if (null != metadata)
            {
                overlayMetadata(metadata, schema, errors);
            }
        }
        super.overlayMetadata(tableName, schema, errors);
    }
}
