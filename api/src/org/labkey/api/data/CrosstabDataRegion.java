/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.labkey.api.query.CrosstabView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

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
    private int _numMemberMeasures = 0;

    public CrosstabDataRegion(CrosstabTableInfo table, int numRowAxisCols, int numMeasures, int numMemberMeasures)
    {
        _settings = table.getSettings();
        _table = table;
        _numMeasures = numMeasures;
        _numMemberMeasures = numMemberMeasures;
        _numRowAxisCols = numRowAxisCols;
    }

    @Override
    protected void renderGridHeaderColumns(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers)
            throws IOException, SQLException
    {
        if (_numMemberMeasures > 0)
        {
            //add a row for the column axis label if there is one
            out.write("<tr>\n");
            renderColumnGroupHeader(_numRowAxisCols + (showRecordSelectors ? 1 : 0), _settings.getRowAxis().getCaption(), out, 2, false);
            renderColumnGroupHeader(renderers.size() - _numRowAxisCols - (showRecordSelectors ? 1 : 0),
                    _settings.getColumnAxis().getCaption(), out);
            out.write("</tr>\n");

            //add an extra row for the column dimension members
            out.write("<tr>\n");

            List<Pair<CrosstabMember, List<DisplayColumn>>> groupedByMember = CrosstabView.columnsByMember(renderers);

            // Output a group header for each column's crosstab member.
            CrosstabDimension colDim = _settings.getColumnAxis().getDimensions().get(0);
            boolean alternate = true;
            for (Pair<CrosstabMember, List<DisplayColumn>> group : groupedByMember)
            {
                CrosstabMember currentMember = group.first;
                List<DisplayColumn> memberColumns = group.second;
                if (memberColumns.isEmpty())
                    continue;

                alternate = !alternate;

                if (currentMember != null)
                {
                    renderColumnGroupHeader(memberColumns.size(),
                            getMemberCaptionWithUrl(colDim, currentMember), out, 1, alternate);
                }

                if (alternate)
                {
                    for (DisplayColumn renderer : memberColumns)
                        renderer.addDisplayClass("labkey-alternate-col");
                }
            }

            //end the col dimension member header row
            out.write("</tr>\n");
        }

        //call the base class to finish rendering the headers
        super.renderGridHeaderColumns(ctx, out, showRecordSelectors, renderers);
    } //renderGridHeaders()

    protected String getMemberCaptionWithUrl(CrosstabDimension dimension, CrosstabMember member)
    {
        String url = null;
        if (null != dimension.getUrl())
            url = dimension.getMemberUrl(member);

        return getMemberCaptionWithUrl(member.getCaption(), url);
    }

    protected String getMemberCaptionWithUrl(String caption, String url)
    {
        if (url != null)
        {
            StringBuilder ret = new StringBuilder();
            ret.append("<a href=\"");
            ret.append(url);
            ret.append("\">");
            ret.append(caption);
            ret.append("</a>");
            return ret.toString();
        }
        else
        {
            return PageFlowUtil.filter(caption);
        }
    }

    protected void renderColumnGroupHeader(int groupWidth, String caption, Writer out) throws IOException
    {
        renderColumnGroupHeader(groupWidth, caption, out, 1, false);
    }

    protected void renderColumnGroupHeader(int groupWidth, String caption, Writer out, int groupHeight, boolean alternate) throws IOException
    {
        if(groupWidth <= 0)
            return;

        out.write("<td align=\"center\" colspan=\"");
        out.write(String.valueOf(groupWidth));
        out.write("\" rowspan=\"");
        out.write(String.valueOf(groupHeight));
        out.write("\" class=\"labkey-data-region");
        if (alternate)
            out.write(" labkey-alternate-col");
        if (isShowBorders())
            out.write(" labkey-show-borders");
        out.write("\">\n");
        out.write(caption == null ? "" : caption);
        out.write("</td>\n");
    }

} //CrosstabDatRegion
