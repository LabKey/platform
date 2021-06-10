/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.actions.AssayDetailRedirectAction;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.study.Dataset;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Serves the dual purpose of adding a hidden <input> element for the object id and showing a link to the originating source
 * User: jgarms
 * Date: Dec 19, 2008
 */
public class SourceDataLinkDisplayColumn extends DataInputColumn
{
    private final Dataset.PublishSource _publishSource;
    private final ColumnInfo _sourceIdCol;
    private final ColumnInfo _objectIdCol;

    public SourceDataLinkDisplayColumn(@Nullable ActionURL completionBase, PublishResultsQueryView.ResolverHelper resolverHelper,
                                       Dataset.PublishSource publishSource, ColumnInfo sourceIdCol, ColumnInfo objectIdCol)
    {
        super("Originating Source", "objectId", false, completionBase, resolverHelper, objectIdCol);
        _publishSource = publishSource;
        _sourceIdCol = sourceIdCol;
        _objectIdCol = objectIdCol;
    }

    @Override
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
        Integer sourceId = (Integer)_sourceIdCol.getValue(ctx);
        if (sourceId != null)
        {
            switch (_publishSource)
            {
                case Assay -> {
                    ActionURL runURL = new ActionURL(AssayDetailRedirectAction.class, ctx.getContainer());
                    runURL.addParameter("runId", sourceId);
                    out.write(PageFlowUtil.link("View Run").href(runURL).toString());
                }
                case SampleType -> {
                    ExpObject expObject = _publishSource.resolvePublishSource(sourceId);
                    if (expObject instanceof ExpSampleType)
                    {
                        ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getShowSampleTypeURL((ExpSampleType)expObject);
                        // by default the container is where the sample definition lives, use the current container instead so
                        // the user is returned to the original folder that the link was attempted from.
                        url.setContainer(ctx.getContainer());
                        out.write(PageFlowUtil.link("View Sample Type").href(url).toString());
                    }
                }
            }
        }
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        columns.add(_sourceIdCol);
        if (null != _objectIdCol)
            columns.add(_objectIdCol);
    }
}
