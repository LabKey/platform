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

package org.labkey.api.study.query;

import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jeckels
 * Date: Nov 6, 2007
 */
public class ValidParticipantVisitDisplayColumn extends SimpleDisplayColumn
{
    private final PublishResultsQueryView.ResolverHelper _resolverHelper;

    public ValidParticipantVisitDisplayColumn(PublishResultsQueryView.ResolverHelper resolverHelper)
    {
        _resolverHelper = resolverHelper;
        setCaption("Specimen Match");
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Pair<Boolean,String> matchStatus = _resolverHelper.getMatchStatus(ctx);
        boolean match = matchStatus.first;
        String message = matchStatus.second;
        if (match)
        {
            out.write("<i class=\"fa fa-check\"></i>");
            out.write(PageFlowUtil.helpPopup("Match", message, true));
        }
        else
        {
            out.write("<i class=\"fa fa-times\"></i>");
            out.write(PageFlowUtil.helpPopup("No match", message, true));
        }
    }
}
