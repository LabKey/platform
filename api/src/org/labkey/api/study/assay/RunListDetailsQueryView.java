package org.labkey.api.study.assay;

import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.RenderContext;
import org.springframework.web.servlet.mvc.Controller;

import java.io.Writer;
import java.io.IOException;

/**
 * Copyright (c) 2007 LabKey Software Foundation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
* User: brittp
* Created: Feb 27, 2008 3:45:13 PM
*/
public class RunListDetailsQueryView extends RunListQueryView
{
    private Class<? extends Controller> _detailsActionClass;
    private String _detailsIdColumn;
    private String _dataIdColumn;

    public RunListDetailsQueryView(ExpProtocol protocol, ViewContext context, Class<? extends Controller> detailsActionClass,
                                   String detailsIdColumn, String dataIdColumn)
    {
        super(protocol, context);
        _detailsActionClass = detailsActionClass;
        _detailsIdColumn = detailsIdColumn;
        _dataIdColumn = dataIdColumn;
    }

    protected DataView createDataView()
    {
        DataView view = super.createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.addDisplayColumn(0, new SimpleDisplayColumn()
        {
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Object runId = ctx.getRow().get(_dataIdColumn);
                if (runId != null)
                {
                    ActionURL url = new ActionURL(_detailsActionClass, ctx.getContainer()).addParameter(_detailsIdColumn, "" + runId);
                    out.write("[<a href=\"" + url.getLocalURIString() + "\" title=\"View run details\">run&nbsp;details</a>]");
                }
            }
        });
        return view;
    }
}
