/*
 * Copyright (c) 2009-2026 LabKey Corporation
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

package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SelectBuilder;
import org.labkey.api.writer.HtmlWriter;

import java.util.Set;

import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.FONT;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;

/**
 * Column type that renders an indicator if there is an associated missing value indicator to accompany the normal
 * value. There is a discrete, configured list of allowable indicators, on a per-container basis. See {@link MvUtil}.
 */
public class MVDisplayColumn extends DataColumn
{
    private final ColumnInfo mvIndicatorColumn;

    public MVDisplayColumn(ColumnInfo dataColumn, ColumnInfo mvIndicatorColumn)
    {
        super(dataColumn);
        this.mvIndicatorColumn = mvIndicatorColumn;
    }

    public String getMvIndicator(RenderContext ctx)
    {
        Object mvIndicatorObject = mvIndicatorColumn.getValue(ctx);
        if (null == mvIndicatorObject)
            mvIndicatorObject = ctx.get(QueryUpdateForm.PREFIX + mvIndicatorColumn.getName());
        if (mvIndicatorObject != null)
            return mvIndicatorObject.toString();
        return null;
    }

    public Object getRawValue(RenderContext ctx)
    {
        return super.getValue(ctx);
    }

    @Override
    public @NotNull String getDisplayClass(RenderContext ctx)
    {
        String displayClass = super.getDisplayClass(ctx);
        if (getMvIndicator(ctx) != null)
            displayClass += " labkey-mv-indicator";
        return displayClass;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        String mvIndicator = getMvIndicator(ctx);
        if (mvIndicator != null)
        {
            HtmlStringBuilder popupText = HtmlStringBuilder.of(MvUtil.getMvLabel(mvIndicator, ctx.getContainer()));

            // If we have a raw value, include it in the popup
            HtmlString value = super.getFormattedHtml(ctx);
            if (!value.isEmpty())
                popupText.unsafeAppend("<p>The value as originally entered was: '")
                    .append(value)
                    .unsafeAppend("'.</p>");

            FONT(
                cl("labkey-mv"),
                PageFlowUtil.popupHelp(popupText, "Missing Value Indicator: " + mvIndicator).link(HtmlString.of(mvIndicator))
            ).appendTo(out);
        }
        else
        {
            super.renderGridCellContents(ctx, out);
        }
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        columns.add(mvIndicatorColumn);
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        // For non-mv-aware clients, we need to return null
        // if we have an mv indicator
        if (getMvIndicator(ctx) != null)
        {
            return null;
        }
        else
        {
            // No MV indicator, so return the underlying data
            return super.getValue(ctx);
        }
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        // For non-MV-aware clients, we need to return null
        // if we have an MV indicator
        if (getMvIndicator(ctx) != null)
        {
            return null;
        }
        else
        {
            // No MV indicator, so return the underlying data
            return super.getDisplayValue(ctx);
        }
    }

    @Override
    public Object getExportCompatibleValue(RenderContext ctx)
    {
        if (getMvIndicator(ctx) != null)
            return getDisplayValue(ctx);
        else
            return super.getExportCompatibleValue(ctx);
    }

    @Override @NotNull
    public HtmlString getFormattedHtml(RenderContext ctx)
    {
        if (getMvIndicator(ctx) != null)
            return HtmlString.EMPTY_STRING;
        return super.getFormattedHtml(ctx);
    }

    @Override
    protected Object getInputValue(RenderContext ctx)
    {
        // bug 7479: MV indicator fields don't preserve value on reshow
        // If we have a form, we need to output what the user entered
        ColumnInfo col = getColumnInfo();
        Object val = null;
        TableViewForm viewForm = ctx.getForm();

        if (col != null)
        {
            String formFieldName = ctx.getForm().getFormFieldName(col);
            if (null != viewForm && viewForm.getValuesToBind().containsKey(formFieldName))
                val = viewForm.getAsString(formFieldName);
            else if (ctx.getRow() != null)
            {
                val = getRawValue(ctx);
            }
        }

        return val;
    }

    @Override
    public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
    {
        DIV(at(style,"margin-top:5px")).appendTo(out);
        super.renderInputHtml(ctx, out, value);
        renderMVPicker(ctx, out);
    }

    private void renderMVPicker(RenderContext ctx, HtmlWriter out)
    {
        String formFieldName = ctx.getForm().getFormFieldName(mvIndicatorColumn);
        String selectedMvIndicator = getMvIndicator(ctx);
        Set<String> mvIndicators = MvUtil.getMvIndicators(ctx.getContainer());
        String setFocusId = (String)ctx.get("setFocusId");
        if (null != setFocusId)
        {
            ctx.remove("setFocusId");
        }

        out.write("Missing Value Indicator:");
        out.write(HtmlString.NBSP);

        new SelectBuilder()
            .addStyle("margin-bottom:5px")
            .addStyle("margin-top:2px")
            .className(null)
            .name(formFieldName)
            .id(setFocusId)
            .disabled(isDisabledInput())
            .addOption("")
            .addOptions(mvIndicators)
            .selected(selectedMvIndicator)
            .appendTo(out);

        // disabled inputs are not posted with the form, so we output a hidden form element:
        //if (isDisabledInput())
        //    renderHiddenFormInput(ctx, out, formFieldName, value);
    }
}
