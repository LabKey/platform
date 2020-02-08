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
import org.labkey.api.assay.AssayResultTable;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.AssayWellExclusionService;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.PlateMetadataDataHandler;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RemappingDisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.flag.FlagColumnRenderer;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: 10/19/12
 */
public class TSVProtocolSchema extends AssayProtocolSchema
{
    public TSVProtocolSchema(User user, Container container, @NotNull TsvAssayProvider provider, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        super(user, container, provider, protocol, targetStudy);
    }

    @Override
    public FilteredTable createDataTable(ContainerFilter cf, boolean includeCopiedToStudyColumns)
    {
        return new _AssayResultTable(this, cf, includeCopiedToStudyColumns);
    }

    @Override
    public TableInfo createProviderTable(String name, ContainerFilter cf)
    {
        if (name.equalsIgnoreCase(EXCLUSION_REPORT_TABLE_NAME))
        {
            return createExclusionReportTable(cf);
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
        _AssayExcludedResultTable(AssayProtocolSchema schema, ContainerFilter cf, boolean includeCopiedToStudyColumns)
        {
            super(schema, cf, includeCopiedToStudyColumns);

            List<FieldKey> defaultCols = new ArrayList<>(getDefaultVisibleColumns());
            defaultCols.add(FieldKey.fromParts("Run"));
            defaultCols.add(FieldKey.fromParts("RowId"));

            AssayWellExclusionService svc = AssayWellExclusionService.getProvider(getProtocol());
            if (svc != null)
            {
                var excludedByColumn = svc.createExcludedByColumn(this, getProtocol());
                var excludedAtColumn = svc.createExcludedAtColumn(this, getProtocol());
                var excludedCommentColumn = svc.createExclusionCommentColumn(this, getProtocol());

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
        _AssayResultTable(AssayProtocolSchema schema, ContainerFilter cf, boolean includeCopiedToStudyColumns)
        {
            super(schema, cf, includeCopiedToStudyColumns);
            String flagConceptURI = org.labkey.api.gwt.client.ui.PropertyType.expFlag.getURI();
            for (ColumnInfo col : getColumns())
            {
                if (col.getJdbcType() == JdbcType.VARCHAR && flagConceptURI.equals(col.getConceptURI()))
                {
                    ((BaseColumnInfo)col).setDisplayColumnFactory(new _FlagDisplayColumnFactory(schema.getProtocol(), this.getName()));
                }
            }

            // placeholder : inject in plate metadata property columns until we can add them in a more structured way
            List<FieldKey> defaultColumns = new ArrayList<>(getDefaultVisibleColumns());
            Domain plateDataDomain = AssayPlateMetadataService.getService(PlateMetadataDataHandler.DATA_TYPE).getPlateDataDomain(getProtocol());
            if (plateDataDomain != null)
            {
                ColumnInfo lsidCol = getColumn("lsid");
                if (lsidCol != null)
                {

                    BaseColumnInfo colProperty = new AliasedColumn("PlateData", lsidCol);
                    colProperty.setFk(new PropertyForeignKey(_userSchema, getContainerFilter(), plateDataDomain));
                    colProperty.setUserEditable(false);
                    colProperty.setCalculated(true);
                    addColumn(colProperty);

                    for (DomainProperty prop : plateDataDomain.getProperties())
                    {
                        defaultColumns.add(FieldKey.fromParts("PlateData", prop.getName()));
                    }
                }
            }
            setDefaultVisibleColumns(defaultColumns);
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
            remapped.rowId = FieldKey.remap(rowId, parent, remap);
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
            String lsid = null==id ? null : "protocol" + protocol.getRowId() + "." + getBoundColumn().getLegalName() +  ":" + String.valueOf(id);
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
