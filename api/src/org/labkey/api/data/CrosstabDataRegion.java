/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
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
 * User: Dave
 * Date: Jan 25, 2008
 * Time: 10:09:00 AM
 */
public class CrosstabDataRegion extends DataRegion
{
    private static final Logger _log = Logger.getLogger(CrosstabDataRegion.class);
    private CrosstabSettings _settings;
    private int _numRowAxisCols;
    private int _numMeasures;
    private int _numMemberMeasures;

    public CrosstabDataRegion(CrosstabSettings settings, int numRowAxisCols, int numMeasures, int numMemberMeasures)
    {
        _settings = settings;
        _numMeasures = numMeasures;
        _numMemberMeasures = numMemberMeasures;
        _numRowAxisCols = numRowAxisCols;
        setAllowHeaderLock(false);
    }

    @Override
    protected void renderGridHeaderColumns(RenderContext ctx, Writer out, boolean showRecordSelectors, List<DisplayColumn> renderers)
            throws IOException, SQLException
    {
        if (_numMemberMeasures > 0)
        {
            //add a row for the column axis label if there is one
            out.write("<thead><tr>");
            renderColumnGroupHeader(_numRowAxisCols + (showRecordSelectors ? 1 : 0), _settings.getRowAxis().getCaption(), out, false);
            renderColumnGroupHeader(renderers.size() - _numRowAxisCols, _settings.getColumnAxis().getCaption(), out, false);
            out.write("</tr></thead>");

            //add an extra row for the column dimension members
            out.write("<thead><tr>");
            renderColumnGroupHeader(_numRowAxisCols + (showRecordSelectors ? 1 : 0), _settings.getRowAxis().getCaption(), out, false);

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
                    if (_numMeasures != _numMemberMeasures || colDim.getMemberUrl(currentMember) != null)
                    {
                        renderColumnGroupHeader(memberColumns.size(), getMemberCaptionWithUrl(colDim, currentMember), out, alternate);
                    }
                }

                for (DisplayColumn renderer : memberColumns)
                {
                    if (alternate)
                        renderer.addDisplayClass("labkey-alternate-col");
                    if (currentMember != null && _numMeasures != _numMemberMeasures)
                    {
                        String memberCaption = currentMember.getCaption();
                        String innerCaption = renderer.getCaption(ctx);
                        if (StringUtils.startsWith(innerCaption,memberCaption))
                            renderer.setCaption(StringUtils.trim(innerCaption.substring(memberCaption.length())));
                    }
                }
            }

            //end the col dimension member header row
            out.write("</tr></thead>");
        }

        //call the base class to finish rendering the headers
        super.renderGridHeaderColumns(ctx, out, showRecordSelectors, renderers);
    }

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
            ret.append(PageFlowUtil.filter(caption));
            ret.append("</a>");
            return ret.toString();
        }

        return PageFlowUtil.filter(caption);
    }

    protected void renderColumnGroupHeader(int groupWidth, String caption, Writer out, boolean alternate) throws IOException
    {
        if (groupWidth <= 0)
            return;

        out.write("<th colspan=\"");
        out.write(String.valueOf(groupWidth));
        out.write("\" class=\"labkey-data-region");
        if (alternate)
            out.write(" labkey-alternate-col");
        if (isShowBorders())
            out.write(" labkey-show-borders");
        out.write(" labkey-group-column-header");
        out.write("\">\n");
        out.write(caption == null ? "" : caption);
        out.write("</th>\n");
    }
}
