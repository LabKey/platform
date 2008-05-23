/*
 * Copyright (c) 2008 LabKey Corporation
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

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.List;

/**
 * Used in conjunction with the CrosstabView class to override rendering of
 * the column headers.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Jan 25, 2008
 * Time: 10:09:00 AM
 */
public class CrosstabDataRegion extends DataRegion
{
    private CrosstabSettings _settings = null;
    private CrosstabTableInfo _table = null;
    private int _numRowAxisCols = 0;
    private int _numMeasures = 0;

    public CrosstabDataRegion(CrosstabTableInfo table, int numRowAxisCols, int numMeasures)
    {
        _settings = table.getSettings();
        _table = table;
        _numMeasures = numMeasures;
        _numRowAxisCols = numRowAxisCols;
    }

    @Override
    protected void renderGridHeaderColumns(RenderContext ctx, Writer out, List<DisplayColumn> renderers) throws SQLException, IOException
    {
        //add a row for the column axis label if there is one
        if(_settings.getColumnAxis().getCaption() != null)
        {
            out.write("<tr>\n");
            renderColumnGroupHeader(_numRowAxisCols + (getShowRecordSelectors() ? 1 : 0), _settings.getRowAxis().getCaption(), null, out, 2);
            renderColumnGroupHeader(renderers.size() - _numRowAxisCols - (getShowRecordSelectors() ? 1 : 0),
                    _settings.getColumnAxis().getCaption(), out);
            out.write("</tr>\n");
        }

        //add an extra row for the column dimension members
        out.write("<tr>\n");

        //if record selectors are enabled, add a blank column
        if(getShowRecordSelectors())
            out.write("<td></td>\n");

        //for each col dimesion member, add a group header
        CrosstabDimension colDim = _settings.getColumnAxis().getDimensions().get(0);
        List<CrosstabMember> colMembers = _table.getColMembers();
        for(int idxColMember = 0; idxColMember < colMembers.size(); ++idxColMember)
        {
            renderColumnGroupHeader(_numMeasures,
                    getMemberCaptionWithUrl(colDim, colMembers.get(idxColMember)),
                    idxColMember % 2 == 0 ? ALTERNATING_ROW_COLOR : null, out, 1);

            if(idxColMember % 2 == 0)
            {
                //set background color on renderers for measure columns
                int idxStart = _numRowAxisCols + (idxColMember * _numMeasures);
                for (int idxMeasureCol = idxStart;
                    idxMeasureCol < idxStart + _numMeasures; ++idxMeasureCol)
                {
                    renderers.get(idxMeasureCol).setBackgroundColor(ALTERNATING_ROW_COLOR);
                }
            } //every other col dimension member
        } //for each col dimension member

        //end the col dimension member header row
        out.write("</tr>\n");

        //call the base class to finish rendering the headers
        super.renderGridHeaderColumns(ctx, out, renderers);
    } //renderGridHeaders()

    protected String getMemberCaptionWithUrl(CrosstabDimension dimension, CrosstabMember member)
    {
        if(null != dimension.getUrl())
        {
            StringBuilder ret = new StringBuilder();
            ret.append("<a href=\"");
            ret.append(dimension.getMemberUrl(member));
            ret.append("\">");
            ret.append(PageFlowUtil.filter(member.getCaption()));
            ret.append("</a>");
            return ret.toString();
        }
        else
            return PageFlowUtil.filter(member.getCaption());
    }

    protected void renderColumnGroupHeader(int groupWidth, String caption, Writer out) throws IOException
    {
        renderColumnGroupHeader(groupWidth, caption, null, out, 1);
    }

    protected void renderColumnGroupHeader(int groupWidth, String caption, String backgroundColor, Writer out, int groupHeight) throws IOException
    {
        if(groupWidth <= 0)
            return;

        out.write("<td colspan=\"");
        out.write(String.valueOf(groupWidth));
        out.write("\" rowspan=\"");
        out.write(String.valueOf(groupHeight));
        out.write("\" class=\"grid\"");
        if (isShowColumnSeparators() || isShowHeaderSeparator())
        {
            out.write(" style=\"border: solid 1px " + COLUMN_SEPARATOR_COLOR + "\"");
        }
        out.write(">\n");
        out.write(caption);
        out.write("</td>\n");
    }

    protected void renderBorderStyle(String side, Writer out) throws IOException
    {
        out.write(side.startsWith("border") ? side : "border-" + side);
        out.write(":1px solid ");
        out.write(COLUMN_SEPARATOR_COLOR);
        out.write(";");
    }

} //CrosstabDatRegion
