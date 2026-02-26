/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssayResultTable;
import org.labkey.api.assay.AssayWellExclusionService;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.assay.plate.AssayPlateTriggerFactory;
import org.labkey.assay.plate.PlateReplicateStatsDomainKind;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.WellTable;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class TSVProtocolSchema extends AssayProtocolSchema
{
    public static final String PLATE_REPLICATE_STATS_TABLE = "PlateReplicateStats";

    public TSVProtocolSchema(User user, Container container, @NotNull TsvAssayProvider provider, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        super(user, container, provider, protocol, targetStudy);
    }

    @Override
    public FilteredTable<?> createDataTable(ContainerFilter cf, boolean includeLinkedToStudyColumns)
    {
        return new _AssayResultTable(this, cf, includeLinkedToStudyColumns);
    }

    @Override
    public TableInfo createProviderTable(String name, ContainerFilter cf)
    {
        if (EXCLUSION_REPORT_TABLE_NAME.equalsIgnoreCase(name))
        {
            return createExclusionReportTable(cf);
        }
        else if (name.equalsIgnoreCase(PLATE_REPLICATE_STATS_TABLE))
        {
            return createPlateReplicateStatsTable(cf, false);
        }

        return super.createProviderTable(name, cf);
    }

    private TableInfo createExclusionReportTable(ContainerFilter cf)
    {
        FilteredTable<?> result = new _AssayExcludedResultTable(this, cf, false);
        result.setName(EXCLUSION_REPORT_TABLE_NAME);
        return result;
    }

    private class _AssayExcludedResultTable extends AssayResultTable
    {
        _AssayExcludedResultTable(AssayProtocolSchema schema, ContainerFilter cf, boolean includeLinkedToStudyColumns)
        {
            super(schema, cf, includeLinkedToStudyColumns);

            List<FieldKey> defaultCols = new ArrayList<>(getDefaultVisibleColumns());
            defaultCols.add(FieldKey.fromParts("Run"));
            defaultCols.add(FieldKey.fromParts("RowId"));

            AssayWellExclusionService svc = AssayWellExclusionService.getProvider(getProtocol());
            if (svc != null)
            {
                var excludedByColumn = svc.createExcludedByColumn(this, getProvider());
                var excludedAtColumn = svc.createExcludedAtColumn(this, getProvider());
                var excludedCommentColumn = svc.createExclusionCommentColumn(this, getProvider());

                addColumn(excludedByColumn);
                addColumn(excludedAtColumn);

                defaultCols.add(excludedByColumn.getFieldKey());
                defaultCols.add(excludedAtColumn.getFieldKey());
                defaultCols.add(excludedCommentColumn.getFieldKey());
            }
            setDefaultVisibleColumns(defaultCols);
        }
    }

    /* the FlagColumn functionality should be in AssayResultTable
    * need to refactor FlagColumn into [API] or AssayResultTable into [Internal] (or new Assay module?)
    */
    private class _AssayResultTable extends AssayResultTable
    {
        _AssayResultTable(AssayProtocolSchema schema, ContainerFilter cf, boolean includeLinkedToStudyColumns)
        {
            super(schema, cf, includeLinkedToStudyColumns);

            if (getProvider().isPlateMetadataEnabled(getProtocol()))
            {
                List<FieldKey> defaultColumns = new ArrayList<>(getDefaultVisibleColumns());

                // plate related triggers
                addTriggerFactory(new AssayPlateTriggerFactory(getProvider(), getProtocol()));

                // join to the well table which may have plate metadata
                ColumnInfo wellLsidCol = getColumn(AssayResultDomainKind.Column.WellLsid.name());
                if (wellLsidCol != null)
                {
                    BaseColumnInfo col = new AliasedColumn("Well", wellLsidCol);
                    col.setFk(QueryForeignKey
                            .from(getUserSchema(), getContainerFilter())
                            .schema(PlateSchema.SCHEMA_NAME).table(WellTable.NAME).key("Lsid")
                    );
                    col.setUserEditable(false);
                    col.setCalculated(true);
                    addColumn(col);
                }

                // Join to assay.hit to display hit selections
                {
                    SqlDialect dialect = getSchema().getSqlDialect();
                    SQLFragment plateHitsSQL = new SQLFragment("(CASE WHEN (SELECT ResultId FROM ")
                            .append(AssayDbSchema.getInstance().getTableInfoHit(), "h")
                            .append(" WHERE h.ResultId = ").append(ExprColumn.STR_TABLE_ALIAS + ".RowId")
                            .append(" AND h.RunId = ").append(ExprColumn.STR_TABLE_ALIAS + ".Run").append(")")
                            .append(" IS NULL THEN ").append(dialect.getBooleanFALSE())
                            .append(" ELSE ").append(dialect.getBooleanTRUE()).append(" END")
                            .append(")");

                    ExprColumn plateHitsColumn = new ExprColumn(this, "Hit", plateHitsSQL, JdbcType.BOOLEAN);
                    plateHitsColumn.setConceptURI("hit-selection");
                    plateHitsColumn.setLabel("Hit Selection");
                    addColumn(plateHitsColumn);
                    defaultColumns.add(0, plateHitsColumn.getFieldKey());
                }

                defaultColumns.add(0, FieldKey.fromParts("Well", "SampleId"));

                // join to any replicate roll ups
                Domain replicateDomain = AssayPlateMetadataService.get().getPlateReplicateStatsDomain(getProtocol());
                if (replicateDomain != null)
                {
                    ColumnInfo replicateLsidCol = getColumn(AssayResultDomainKind.Column.ReplicateLsid.name());
                    if (replicateLsidCol != null)
                    {
                        BaseColumnInfo replicateCol = new AliasedColumn(AssayResultDomainKind.Column.Replicate.name(), replicateLsidCol);
                        replicateCol.setFk(QueryForeignKey
                                .from(getUserSchema(), getContainerFilter())
                                .to(PLATE_REPLICATE_STATS_TABLE, PlateReplicateStatsDomainKind.Column.Lsid.name(), null)
                        );
                        replicateCol.setUserEditable(false);
                        replicateCol.setCalculated(true);
                        addColumn(replicateCol);

                        // adjust the default columns to position the replicate columns adjacent to the measures they track
                        Map<String, FieldKey> replicateFields = new HashMap<>();
                        for (DomainProperty prop : replicateDomain.getProperties())
                            replicateFields.put(prop.getName(), FieldKey.fromParts(AssayResultDomainKind.Column.Replicate.name(), prop.getName()));

                        List<FieldKey> newDefaultColumns = new ArrayList<>();
                        for (FieldKey fk : defaultColumns)
                        {
                            newDefaultColumns.add(fk);
                            ColumnInfo col = getColumn(fk);
                            if (col != null && col.isMeasure() && col.getJdbcType().isNumeric())
                                addReplicateColsForMeasure(newDefaultColumns, col, replicateFields);
                        }
                        defaultColumns = newDefaultColumns;
                    }
                }

                setDefaultVisibleColumns(defaultColumns);
            }
        }
    }

    private void addReplicateColsForMeasure(List<FieldKey> defaultCols, ColumnInfo measure, Map<String, FieldKey> replicateFields)
    {
        for (String replicateName : PlateReplicateStatsDomainKind.getStatsFieldNames(measure.getName()))
        {
            if (replicateFields.containsKey(replicateName))
                defaultCols.add(replicateFields.get(replicateName));
        }
    }

    @Nullable
    public TableInfo createPlateReplicateStatsTable(@Nullable ContainerFilter cf, boolean allowInsertUpdate)
    {
        Domain domain = AssayPlateMetadataService.get().getPlateReplicateStatsDomain(getProtocol());
        if (domain != null)
        {
            return new _AssayPlateReplicateStatsTable(domain, this, cf, allowInsertUpdate);
        }
        return null;
    }

    private static class _AssayPlateReplicateStatsTable extends FilteredTable<AssayProtocolSchema>
    {
        private final boolean _allowInsertUpdate;

        public _AssayPlateReplicateStatsTable(@NotNull Domain domain, @NotNull AssayProtocolSchema userSchema, @Nullable ContainerFilter containerFilter, boolean allowInsertUpdate)
        {
            super(StorageProvisioner.createTableInfo(domain), userSchema, containerFilter);
            _allowInsertUpdate = allowInsertUpdate;

            setDescription("Represents the replicate statistics for a plate based assay containing replicate well groups.");
            setName("PlateReplicateStats");
            setPublicSchemaName(_userSchema.getSchemaName());
            FieldKey lsidFieldKey = FieldKey.fromParts("Lsid");
            Supplier<Map<DomainProperty, Object>> defaultsSupplier = null;

            for (ColumnInfo col : getRealTable().getColumns())
            {
                var columnInfo = wrapColumn(col);
                if (col.isHidden())
                    columnInfo.setHidden(true);

                if (lsidFieldKey.equals(col.getFieldKey()))
                {
                    columnInfo.setHidden(true);
                    columnInfo.setKeyField(true);
                }

                // Issue 52283 : copy the property descriptor settings
                String propertyURI = col.getPropertyURI();
                DomainProperty dp = propertyURI != null ? domain.getPropertyByURI(propertyURI) : null;
                if (dp != null)
                {
                    PropertyDescriptor pd = dp.getPropertyDescriptor();
                    if (pd != null)
                    {
                        defaultsSupplier = PropertyColumn.copyAttributes(userSchema.getUser(), columnInfo, dp, getContainer(), null, containerFilter, defaultsSupplier);
                        columnInfo.setFieldKey(FieldKey.fromParts(dp.getName()));
                    }
                }
                addColumn(columnInfo);
            }
        }

        @Override
        public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
        {
            if (!_allowInsertUpdate && (perm.equals(InsertPermission.class) || perm.equals(UpdatePermission.class)))
                return false;

            return _userSchema.getContainer().hasPermission(user, perm);
        }

        @Override
        public @Nullable QueryUpdateService getUpdateService()
        {
            return new DefaultQueryUpdateService(this, getRealTable());
        }
    }
}
