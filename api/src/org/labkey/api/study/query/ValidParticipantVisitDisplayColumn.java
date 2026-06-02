/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

package org.labkey.api.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.HtmlWriter;

import static org.labkey.api.util.DOM.I;
import static org.labkey.api.util.DOM.cl;

public class ValidParticipantVisitDisplayColumn extends SimpleDisplayColumn
{
    private final PublishResultsQueryView.ResolverHelper _resolverHelper;
    private final boolean _matchSpecimen;

    public ValidParticipantVisitDisplayColumn(PublishResultsQueryView.ResolverHelper resolverHelper,
                                              ColumnInfo specimenIdCol,
                                              ColumnInfo sampleIdCol)
    {
        _resolverHelper = resolverHelper;
        _matchSpecimen = (specimenIdCol != null || sampleIdCol == null);

        if (_matchSpecimen)
            setCaption("Specimen Match");
        else
            setCaption("Sample Match");
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        Pair<Boolean, HtmlString> matchStatus;

        if (_matchSpecimen)
            matchStatus = _resolverHelper.getMatchStatus(ctx);
        else
            matchStatus = _resolverHelper.getSampleMatchStatus(ctx);

        boolean match = matchStatus.first;
        HtmlString message = matchStatus.second;
        if (match)
        {
            I(cl("fa fa-check")).appendTo(out);
            PageFlowUtil.popupHelp(message, "Match").appendTo(out);
        }
        else
        {
            I(cl("fa fa-times")).appendTo(out);
            PageFlowUtil.popupHelp(message, "No match").appendTo(out);
        }
    }
}
