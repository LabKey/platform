/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
import org.labkey.api.query.CrosstabView;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SafeToRender;
import org.labkey.api.writer.HtmlWriter;

import java.sql.SQLException;
import java.util.List;

import static org.labkey.api.util.DOM.Attribute.colspan;
import static org.labkey.api.util.DOM.TH;
import static org.labkey.api.util.DOM.THEAD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;

/**
 * Used in conjunction with the CrosstabView class to override rendering of the column headers.
 */
public class CrosstabDataRegion extends DataRegion
{
    private final CrosstabSettings _settings;
    private final int _numRowAxisCols;
    private final int _numMeasures;
    private final int _numMemberMeasures;

    public CrosstabDataRegion(CrosstabSettings settings, int numRowAxisCols, int numMeasures, int numMemberMeasures)
    {
        _settings = settings;
        _numMeasures = numMeasures;
        _numMemberMeasures = numMemberMeasures;
        _numRowAxisCols = numRowAxisCols;
        setAllowHeaderLock(false);
    }

    @Override
    protected void renderGridHeaderColumns(RenderContext ctx, HtmlWriter out, boolean showRecordSelectors, List<DisplayColumn> renderers) throws SQLException
    {
        if (_numMemberMeasures > 0)
        {
            //add a row for the column axis label if there is one
            THEAD(
                TR(
                    (Renderable) ret -> {
                        renderColumnGroupHeader(_numRowAxisCols + (showRecordSelectors ? 1 : 0), HtmlString.of(_settings.getRowAxis().getCaption()), out, false);
                        renderColumnGroupHeader(renderers.size() - _numRowAxisCols, HtmlString.of(_settings.getColumnAxis().getCaption()), out, false);

                        return ret;
                    }
                )
            ).appendTo(out);

            //add an extra row for the column dimension members
            THEAD(
                TR(
                    (Renderable) ret -> {
                        renderColumnGroupHeader(_numRowAxisCols + (showRecordSelectors ? 1 : 0), HtmlString.of(_settings.getRowAxis().getCaption()), out, false);

                        List<Pair<CrosstabMember, List<DisplayColumn>>> groupedByMember = CrosstabView.columnsByMember(renderers);

                        // Output a group header for each column's crosstab member.
                        CrosstabDimension colDim = _settings.getColumnAxis().getDimensions().getFirst();
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
                                    if (StringUtils.startsWith(innerCaption, memberCaption))
                                        renderer.setCaption(StringUtils.trim(innerCaption.substring(memberCaption.length())));
                                }
                            }
                        }

                        return ret;
                    }
                )
            ).appendTo(out);
        }

        //call the base class to finish rendering the headers
        super.renderGridHeaderColumns(ctx, out, showRecordSelectors, renderers);
    }

    protected SafeToRender getMemberCaptionWithUrl(CrosstabDimension dimension, CrosstabMember member)
    {
        String url = null;
        if (null != dimension.getUrl())
            url = dimension.getMemberUrl(member);

        return getMemberCaptionWithUrl(member.getCaption(), url);
    }

    protected SafeToRender getMemberCaptionWithUrl(String caption, String url)
    {
        if (url != null)
        {
            return LinkBuilder.simpleLink(caption, url);
        }

        return HtmlString.of(caption);
    }

    protected void renderColumnGroupHeader(int groupWidth, SafeToRender caption, HtmlWriter out, boolean alternate)
    {
        if (groupWidth <= 0)
            return;

        TH(
            at(colspan, groupWidth).
            cl("labkey-data-region labkey-pivot labkey-group-column-header").
            cl(alternate,"labkey-alternate-col").
            cl(isShowBorders(), "labkey-show-borders"),
            caption
        ).appendTo(out);
    }
}
