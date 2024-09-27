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
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.AssayWellExclusionService;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RemappingDisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.flag.FlagColumnRenderer;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.WellTable;
import org.labkey.assay.query.AssayDbSchema;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TSVProtocolSchema extends AssayProtocolSchema
{
    public static final String PLATE_REPLICATE_STATS_TABLE = "PlateReplicateStats";

    public TSVProtocolSchema(User user, Container container, @NotNull TsvAssayProvider provider, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        super(user, container, provider, protocol, targetStudy);
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> names = super.getTableNames();

        if (getProvider().isPlateMetadataEnabled(getProtocol()))
            names.add(PLATE_REPLICATE_STATS_TABLE);
        return names;
    }

    @Override
    public FilteredTable createDataTable(ContainerFilter cf, boolean includeLinkedToStudyColumns)
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
            TableInfo replicateTable = createPlateReplicateStatsTable(cf);
            if (replicateTable != null)
                return replicateTable;
        }

        return super.createProviderTable(name, cf);
    }

    private TableInfo createExclusionReportTable(ContainerFilter cf)
    {
        FilteredTable result = new _AssayExcludedResultTable(this, cf, false);
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
            String flagConceptURI = org.labkey.api.gwt.client.ui.PropertyType.expFlag.getURI();
            for (ColumnInfo col : getColumns())
            {
                if (col.getJdbcType() == JdbcType.VARCHAR && flagConceptURI.equals(col.getConceptURI()))
                {
                    ((BaseColumnInfo)col).setDisplayColumnFactory(new _FlagDisplayColumnFactory(schema.getProtocol(), this.getName()));
                }
            }

            List<FieldKey> defaultColumns = new ArrayList<>(getDefaultVisibleColumns());
            if (getProvider().isPlateMetadataEnabled(getProtocol()))
            {
                // join to the well table which may have plate metadata
                ColumnInfo wellLsidCol = getColumn(AssayResultDomainKind.WELL_LSID_COLUMN_NAME);
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
                setDefaultVisibleColumns(defaultColumns);
            }
        }
    }

    @Nullable
    private TableInfo createPlateReplicateStatsTable(ContainerFilter cf)
    {
        Domain domain = AssayPlateMetadataService.get().getPlateReplicateStatsDomain(getProtocol());
        if (domain != null)
        {
            return new _AssayPlateReplicateStatsTable(domain, this, cf);
        }
        return null;
    }

    private class _AssayPlateReplicateStatsTable extends FilteredTable<AssayProtocolSchema>
    {
        public _AssayPlateReplicateStatsTable(@NotNull Domain domain, @NotNull AssayProtocolSchema userSchema, @Nullable ContainerFilter containerFilter)
        {
            super(StorageProvisioner.createTableInfo(domain), userSchema, containerFilter);

            setDescription("Represents the replicate statistics for a plate based assay containing replicate well groups.");
            setName("PlateReplicateStats");
            setPublicSchemaName(_userSchema.getSchemaName());

            for (ColumnInfo col : getRealTable().getColumns())
            {
                var columnInfo = wrapColumn(col);
                if (col.getName().equals("Lsid"))
                {
                    columnInfo.setHidden(true);
                    columnInfo.setKeyField(true);
                }
                addColumn(columnInfo);
            }
        }

        @Override
        public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
        {
            return _userSchema.getContainer().hasPermission(user, perm);
        }

        @Override
        public @Nullable QueryUpdateService getUpdateService()
        {
            return new DefaultQueryUpdateService(this, getRealTable());
        }
    }

    class _FlagDisplayColumnFactory implements RemappingDisplayColumnFactory
    {
        FieldKey rowId = new FieldKey(null, "RowId");
        final ExpProtocol protocol;
        final String dataregion;

        _FlagDisplayColumnFactory(ExpProtocol protocol, String dataregionName)
        {
            this.protocol = protocol;
            this.dataregion = dataregionName;
        }

        @Override
        public _FlagDisplayColumnFactory remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap)
        {
            _FlagDisplayColumnFactory remapped = this.clone();
            var fk = FieldKey.remap(rowId, parent, remap);
            if (null != fk)
                remapped.rowId = fk;
            return remapped;
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new _FlagColumnRenderer(colInfo, rowId, protocol, dataregion);
        }

        @Override
        protected _FlagDisplayColumnFactory clone()
        {
            try
            {
                return (_FlagDisplayColumnFactory)super.clone();
            }
            catch (CloneNotSupportedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * NOTE: The base class FlagColumnRenderer usually wraps an lsid and uses the
     * display column to find the comment.
     * This class turns that around.  It wraps a flag/comment column and uses
     * run/lsid and rowid to generate a fake lsid
     */
    class _FlagColumnRenderer extends FlagColumnRenderer
    {
        final FieldKey rowId;
        final ExpProtocol protocol;
        final String dataregion;

        _FlagColumnRenderer(ColumnInfo col, FieldKey rowId, ExpProtocol protocol, String dataregion)
        {
            super(col);
            this.rowId = rowId;
            this.protocol = protocol;
            ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getSetResultFlagURL(protocol.getContainer());
            url.addParameter("rowId", protocol.getRowId());
            url.addParameter("columnName", col.getName());
            this.endpoint = url.getLocalURIString();
            this.dataregion = dataregion;
            this.jsConvertPKToLSID = "function(pk){return " +
                    PageFlowUtil.jsString("protocol" + protocol.getRowId() + "." + getBoundColumn().getLegalName() + ":") + " + pk}";
        }

        @Override
        protected void renderFlag(RenderContext ctx, Writer out) throws IOException
        {
            renderFlagScript(ctx, out);
            Integer id = ctx.get(rowId, Integer.class);
            Object comment = getValue(ctx);
            String lsid = null==id ? null : "protocol" + protocol.getRowId() + "." + getBoundColumn().getLegalName() +  ":" + id;
            _renderFlag(ctx, out, lsid, null==comment?null:String.valueOf(comment));
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(rowId);
        }
    }
}
