/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.study.actions;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/**
* User: jeckels
* Date: Aug 30, 2010
*/
public class StudyPickerColumn extends UploadWizardAction.InputDisplayColumn
{
    ColumnInfo _colInfo;

    public StudyPickerColumn(ColumnInfo col)
    {
        super("Target Study", "targetStudy");
        _colInfo = col;
    }

    public void renderDetailsCaptionCell(RenderContext ctx, Writer out) throws IOException
    {
        if (null == _caption)
            return;

        out.write("<td class='labkey-form-label'>");
        renderTitle(ctx, out);
        int mode = ctx.getMode();
        if (mode == DataRegion.MODE_INSERT || mode == DataRegion.MODE_UPDATE)
        {
            if (_colInfo != null)
            {
                String helpPopupText = ((_colInfo.getFriendlyTypeName() != null) ? "Type: " + _colInfo.getFriendlyTypeName() + "\n" : "") +
                            ((_colInfo.getDescription() != null) ? "Description: " + _colInfo.getDescription() + "\n" : "");
                out.write(PageFlowUtil.helpPopup(_colInfo.getName(), helpPopupText));
                if (!_colInfo.isNullable())
                    out.write(" *");
            }
        }
        out.write("</td>");
    }

    public void renderDetailsData(RenderContext ctx, Writer out, int span) throws IOException
    {
        super.renderDetailsData(ctx, out, 1);
    }

    protected boolean isDisabledInput()
    {
        return getColumnInfo().getDefaultValueType() == DefaultValueType.FIXED_NON_EDITABLE;
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        Map<Container, String> studies = AssayPublishService.get().getValidPublishTargets(ctx.getViewContext().getUser(), ReadPermission.class);

        boolean disabled = isDisabledInput();
        out.write("<select name=\"" + _inputName + "\"" + (disabled ? " DISABLED" : "") + ">\n");
        out.write("    <option value=\"\">[None]</option>\n");
        for (Map.Entry<Container, String> entry : studies.entrySet())
        {
            Container container = entry.getKey();
            out.write("    <option value=\"" + PageFlowUtil.filter(container.getId()) + "\"");
            if (container.getId().equals(value))
                out.write(" SELECTED");
            out.write(">" + PageFlowUtil.filter(container.getPath() + " (" + entry.getValue()) + ")</option>\n");
        }
        out.write("</select>");
        if (disabled)
            out.write("<input type=\"hidden\" name=\"" +_inputName + "\" value=\"" + PageFlowUtil.filter(value) + "\">");
    }

    public ColumnInfo getColumnInfo()
    {
        return _colInfo;
    }

    public boolean isQueryColumn()
    {
        return true;
    }
}
