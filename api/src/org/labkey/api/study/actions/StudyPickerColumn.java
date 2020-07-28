/*
 * Copyright (c) 2010-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.actions.UploadWizardAction;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.element.Option;
import org.labkey.api.util.element.Option.OptionBuilder;
import org.labkey.api.util.element.Select;
import org.labkey.api.util.element.Select.SelectBuilder;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
* User: jeckels
* Date: Aug 30, 2010
*/
public class StudyPickerColumn extends UploadWizardAction.InputDisplayColumn
{
    ColumnInfo _colInfo;

    public StudyPickerColumn(ColumnInfo col)
    {
        this(col, "targetStudy");
    }

    public StudyPickerColumn(ColumnInfo col, String inputName)
    {
        super(AbstractAssayProvider.TARGET_STUDY_PROPERTY_CAPTION, inputName);
        _colInfo = col;
    }

    protected Object calculateValue(RenderContext ctx)
    {
        return super.getValue(ctx);
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        return calculateValue(ctx);
    }

    @Override
    public void renderDetailsCaptionCell(RenderContext ctx, Writer out, @Nullable String cls) throws IOException
    {
        if (null == _caption)
            return;

        out.write("<td class=\"" + (cls != null ? cls : "lk-form-label") + "\">");
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

    protected boolean isDisabledInput()
    {
        return getColumnInfo().getDefaultValueType() == DefaultValueType.FIXED_NON_EDITABLE;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        this.renderInputHtml(ctx, out, getValue(ctx));
    }

    @Override
    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        Set<Study> studies = AssayPublishService.get().getValidPublishTargets(ctx.getViewContext().getUser(), ReadPermission.class);

        boolean disabled = isDisabledInput();

        SelectBuilder select = new SelectBuilder()
            .name(_inputName)
            .disabled(disabled);
        select.addOption(new OptionBuilder().label("[None]"));
        for (Study study : studies)
        {
            Container container = study.getContainer();
            select.addOption(new OptionBuilder()
                .label(container.getPath() + " (" + study.getLabel() + ")")
                .value(container.getId())
                .selected(container.getId().equals(value))
            );
        }
        out.write(select.toString());
        
        if (disabled)
            out.write("<input type=\"hidden\" name=\"" +_inputName + "\" value=\"" + PageFlowUtil.filter(value) + "\">");
    }

    @Override
    public ColumnInfo getColumnInfo()
    {
        return _colInfo;
    }

    @Override
    public boolean isQueryColumn()
    {
        return true;
    }
}
