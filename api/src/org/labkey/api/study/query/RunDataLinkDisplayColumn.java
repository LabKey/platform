/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
import org.labkey.api.study.actions.AssayDetailRedirectAction;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Serves the dual purpose of adding a hidden <input> element for the object id and showing a link to the originating run
 * User: jgarms
 * Date: Dec 19, 2008
 */
public class RunDataLinkDisplayColumn extends DataInputColumn
{
    private final ColumnInfo _runIdCol;
    private final ColumnInfo _objectIdCol;

    public RunDataLinkDisplayColumn(String completionBase, PublishResultsQueryView.ResolverHelper resolverHelper, ColumnInfo runIdCol, ColumnInfo objectIdCol)
    {
        super("Originating Run", "objectId", false, completionBase, resolverHelper, objectIdCol);
        _runIdCol = runIdCol;
        _objectIdCol = objectIdCol;
    }

    protected Object calculateValue(RenderContext ctx)
    {
        if (_requiredColumn == null)
            return null;
        return ctx.getRow().get(_requiredColumn.getAlias());
    }
    
    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (null != _objectIdCol)
            super.renderGridCellContents(ctx, out);
        int runId = (Integer)_runIdCol.getValue(ctx);
        ActionURL runURL = new ActionURL(AssayDetailRedirectAction.class, ctx.getContainer());
        runURL.addParameter("runId", runId);
        out.write(PageFlowUtil.textLink("View Run", runURL));
    }

    @Override
    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        out.write("Originating Run");
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        columns.add(_runIdCol);
        if (null != _objectIdCol)
            columns.add(_objectIdCol);
    }
}
