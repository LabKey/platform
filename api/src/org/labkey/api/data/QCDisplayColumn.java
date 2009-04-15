/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * User: jgarms
 * Date: Jan 08, 2009
 */
public class QCDisplayColumn extends DataColumn
{
    private final ColumnInfo qcValueColumn;

    public QCDisplayColumn(ColumnInfo dataColumn, ColumnInfo qcValueColumn)
    {
        super(dataColumn);
        this.qcValueColumn = qcValueColumn;
    }

    public String getQcValue(RenderContext ctx)
    {
        Object qcValueObject = qcValueColumn.getValue(ctx);
        if (qcValueObject != null)
        {
            return qcValueObject.toString();
        }
        return null;
    }

    public Object getRawValue(RenderContext ctx)
    {
        return super.getValue(ctx);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String qcValue = getQcValue(ctx);
        if (qcValue != null)
        {
            out.write("<font class=\"labkey-qc\">");
            out.write(h(qcValue));
            out.write("</font>");

            String imgHtml = "<img align=\"top\" src=\"" +
                    HttpView.currentContext().getContextPath() +
                    "/_images/qc_indicator.gif\" class=\"labkey-qc-indicator\">";

            String popupText = PageFlowUtil.filter(QcUtil.getQcLabel(qcValue, ctx.getContainer()));

            // If we have a raw value, include it in the popup
            String value = super.getFormattedValue(ctx);
            if (value != null && !"".equals(value))
            {
                popupText += ("<p>The value as originally entered was: '" + value + "'.");
            }

            out.write(PageFlowUtil.helpPopup("QC Value: " + qcValue, popupText, true, imgHtml, 0));

            return;
        }
        // Call super, as we don't want to check twice for the qc value
        String value = super.getFormattedValue(ctx);

        if ("".equals(value.trim()))
        {
            out.write("&nbsp;");
        }
        else
        {
            out.write(value);
        }
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        columns.add(qcValueColumn);
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        // For non-qc-aware clients, we need to return null
        // if we have a qc value
        if (getQcValue(ctx) != null)
        {
            return null;
        }
        else
        {
            // No QC value, so return the underlying data
            return super.getValue(ctx);
        }
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        return getValue(ctx);
    }

    @Override
    public String getFormattedValue(RenderContext ctx)
    {
        if (getQcValue(ctx) != null)
            return "";
        return super.getFormattedValue(ctx);
    }

    @Override
    protected Object getInputValue(RenderContext ctx)
    {
        // bug 7479: QC fields don't preserve value on reshow
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
    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        renderGridCellContents(ctx, out);
    }

    @Override
    public void renderInputCell(RenderContext ctx, Writer out, int span) throws IOException
    {
        out.write("<td colspan=" + span + ">");
        renderInputHtml(ctx, out, getInputValue(ctx));
        renderQCPicker(ctx, out);
        out.write("</td>");
    }

    private void renderQCPicker(RenderContext ctx, Writer out) throws IOException
    {
        String formFieldName = ctx.getForm().getFormFieldName(qcValueColumn);
        String selectedQcValue = getQcValue(ctx);
        Set<String> qcValues = QcUtil.getQcValues(ctx.getContainer());
        out.write("<br>QC Value:");
        out.write("<select");
        outputName(ctx, out, formFieldName);
        if (isDisabledInput())
            out.write(" DISABLED");
        out.write(">\n");
        out.write("<option value=\"\"></option>");
        for (String qcValue : qcValues)
        {
            out.write("  <option value=\"");
            out.write(qcValue);
            out.write("\"");
            if (null != selectedQcValue && qcValue.equals(selectedQcValue))
                out.write(" selected ");
            out.write(" >");
            out.write(qcValue);
            out.write("</option>\n");
        }
        out.write("</select>");
        // disabled inputs are not posted with the form, so we output a hidden form element:
        //if (isDisabledInput())
        //    renderHiddenFormInput(ctx, out, formFieldName, value);
    }
}
