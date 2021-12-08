/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.api.view;

import org.apache.commons.collections4.MultiValuedMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;

/**
 * {@link DisplayColumn} that use a React QuerySelect component input to allow for type-ahead search/filter
 * for a select input that has too many options for a user to meaningfully scroll through.
 * TODO: See Issue 43526 for more details on remaining TODOs for full replacement of DataColumn.renderSelectFormInput.
 */
public class TypeAheadSelectDisplayColumn extends DataColumn
{
    private Integer _maxRows;

    public TypeAheadSelectDisplayColumn(ColumnInfo col, Integer maxRows)
    {
        super(col);
        _maxRows = maxRows;
    }

    @Override
    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        ForeignKey fk = getBoundColumn().getFk();
        // currently only supported for lookup columns with a defined schema/query
        if (fk == null)
        {
            out.write("TypeAheadSelectDisplayColumn can only be used with a lookup column.");
            return;
        }

        String formFieldName = getFormFieldName(ctx);
        boolean disabledInput = isDisabledInput(ctx);
        String strVal = getStringValue(value, disabledInput);
        String renderId = "query-select-div-" + UniqueID.getRequestScopedUID(ctx.getRequest());

        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"text/javascript\">");
        //sb.append("LABKEY.requiresScript('http://localhost:3001/querySelectInput.js', function() {\n");
        sb.append("LABKEY.requiresScript('gen/querySelectInput', function() {\n");
        sb.append(" LABKEY.App.loadApp('querySelectInput', ").append(PageFlowUtil.jsString(renderId)).append(", {\n");
        sb.append("     name: ").append(PageFlowUtil.jsString(getInputPrefix() + formFieldName)).append("\n");
        sb.append("     ,value: ").append(PageFlowUtil.jsString(strVal)).append("\n");
        sb.append("     ,disabled: ").append(disabledInput).append("\n");
        sb.append("     ,schemaName: ").append(PageFlowUtil.jsString(fk.getLookupSchemaName())).append("\n");
        sb.append("     ,queryName: ").append(PageFlowUtil.jsString(fk.getLookupTableName())).append("\n");
        if (_maxRows != null)
            sb.append("     ,maxRows: ").append(_maxRows).append("\n");
        if (fk.getLookupContainer() != null)
            sb.append("     ,containerPath: ").append(PageFlowUtil.jsString(fk.getLookupContainer().getPath())).append("\n");
        sb.append(" });\n");
        sb.append("});\n");
        sb.append("</script>\n");
        sb.append("<div id=").append(PageFlowUtil.jsString(renderId)).append("></div>");
        out.write(sb.toString());

        // disabled inputs are not posted with the form, so we output a hidden form element:
        if (disabledInput)
            renderHiddenFormInput(ctx, out, formFieldName, value);
    }

    public static class Factory implements DisplayColumnFactory
    {
        private Integer _maxRows;

        public Factory()
        {}

        public Factory(MultiValuedMap<String, String> map)
        {
            String maxRowsStr = getProperty(map, "maxRows");
            _maxRows = maxRowsStr != null ? Integer.parseInt(maxRowsStr) : null;
        }

        private String getProperty(MultiValuedMap<String, String> map, String propertyName)
        {
            Collection<String> values = map == null ? Collections.emptyList() : map.get(propertyName);
            if (!values.isEmpty())
            {
                return values.iterator().next();
            }
            return null;
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new TypeAheadSelectDisplayColumn(colInfo, _maxRows);
        }
    }
}
