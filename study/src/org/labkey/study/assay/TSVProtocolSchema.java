/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RemappingDisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.flag.FlagColumnRenderer;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayResultTable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.study.controllers.assay.AssayController;

import java.io.IOException;
import java.io.Writer;
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
    public FilteredTable createDataTable(boolean includeCopiedToStudyColumns)
    {
        return new _AssayResultTable(this, includeCopiedToStudyColumns);
    }

    /* the FlagColumn functionality should be in AssayResultTable
    * need to refactor FlagColumn into [API] or AssayResultTable into [Internal] (or new Assay module?)
    */
    private class _AssayResultTable extends AssayResultTable
    {
        _AssayResultTable(AssayProtocolSchema schema, boolean includeCopiedToStudyColumns)
        {
            super(schema, includeCopiedToStudyColumns);
            String flagConceptURI = org.labkey.api.gwt.client.ui.PropertyType.expFlag.getURI();
            for (ColumnInfo col : getColumns())
            {
                if (col.getJdbcType() == JdbcType.VARCHAR && flagConceptURI.equals(col.getConceptURI()))
                {
                    col.setDisplayColumnFactory(new _FlagDisplayColumnFactory(schema.getProtocol(), this.getName()));
                }
            }
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
        public void remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap)
        {
            rowId = FieldKey.remap(rowId, parent, remap);
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new _FlagColumnRenderer(colInfo, rowId, protocol, dataregion);
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
            ActionURL url = new ActionURL(AssayController.SetResultFlagAction.class, protocol.getContainer());
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
