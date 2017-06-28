/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Column type that renders an indicator if there is an associated missing value indicator to accompany the normal
 * value. There is a discrete, configured list of allowable indicators, on a per-container basis. See {@link MvUtil}.
 *
 * User: jgarms
 * Date: Jan 08, 2009
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
            mvIndicatorObject = ctx.get("quf_" + mvIndicatorColumn.getName());
        if (mvIndicatorObject != null)
            return mvIndicatorObject.toString();
        return null;
    }

    public Object getRawValue(RenderContext ctx)
    {
        return super.getValue(ctx);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String mvIndicator = getMvIndicator(ctx);
        if (mvIndicator != null)
        {
            out.write("<font class=\"labkey-mv\">");
            out.write(h(mvIndicator));
            out.write("</font>");

            String imgHtml = "<img align=\"top\" src=\"" +
                    HttpView.currentContext().getContextPath() +
                    "/_images/mv_indicator.gif\" class=\"labkey-mv-indicator\">";

            String popupText = PageFlowUtil.filter(MvUtil.getMvLabel(mvIndicator, ctx.getContainer()));

            // If we have a raw value, include it in the popup
            String value = super.getFormattedValue(ctx);
            if (value != null && !"".equals(value))
            {
                popupText += ("<p>The value as originally entered was: '" + value + "'.");
            }

            out.write(PageFlowUtil.helpPopup("Missing Value Indicator: " + mvIndicator, popupText, true, imgHtml, 0));
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
        // For non-mv-aware clients, we need to return null
        // if we have an mv indicator
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

    @Override @NotNull
    public String getFormattedValue(RenderContext ctx)
    {
        if (getMvIndicator(ctx) != null)
            return "";
        return super.getFormattedValue(ctx);
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
            if (null != viewForm && viewForm.getStrings().containsKey(formFieldName))
                val = viewForm.get(formFieldName);
            else if (ctx.getRow() != null)
            {
                val = getRawValue(ctx);
            }
        }

        return val;
    }

    @Override
    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        super.renderInputHtml(ctx, out, value);
        renderMVPicker(ctx, out);
    }

    private void renderMVPicker(RenderContext ctx, Writer out) throws IOException
    {
        String formFieldName = ctx.getForm().getFormFieldName(mvIndicatorColumn);
        String selectedMvIndicator = getMvIndicator(ctx);
        Set<String> mvIndicators = MvUtil.getMvIndicators(ctx.getContainer());
        out.write("<br>Missing Value Indicator:");
        out.write("<select");
        outputName(ctx, out, formFieldName);
        if (isDisabledInput())
            out.write(" DISABLED");
        out.write(">\n");
        out.write("<option value=\"\"></option>");
        for (String mvIndicator : mvIndicators)
        {
            out.write("  <option value=\"");
            out.write(mvIndicator);
            out.write("\"");
            if (null != selectedMvIndicator && mvIndicator.equals(selectedMvIndicator))
                out.write(" selected ");
            out.write(" >");
            out.write(mvIndicator);
            out.write("</option>\n");
        }
        out.write("</select>");
        // disabled inputs are not posted with the form, so we output a hidden form element:
        //if (isDisabledInput())
        //    renderHiddenFormInput(ctx, out, formFieldName, value);
    }
}
