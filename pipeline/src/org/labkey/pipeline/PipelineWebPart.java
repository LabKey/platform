/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.pipeline;

import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.view.*;
import org.labkey.pipeline.api.PipelineStatusManager;
import org.labkey.pipeline.status.DescriptionDisplayColumn;
import org.labkey.pipeline.status.StatusController;
import org.labkey.pipeline.status.StatusDataRegion;

import java.io.PrintWriter;
import java.net.URI;

/**
 */
public class PipelineWebPart extends WebPartView
{
    private static final String partName = "Data Pipeline";

    public static String getPartName()
    {
        return partName;
    }

    public PipelineWebPart(ViewContext viewContext)
    {
        setTitle(getPartName());
        setTitleHref(StatusController.urlShowList(viewContext.getContainer(), false));
    }

    protected void showMessage(String message) throws Exception
    {
        HtmlView view = new HtmlView("<table class=\"DataRegion\"><tr><td>" + message + "</td></tr></table>");
        view.setTitle(getPartName());
        include(view);
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        Container c;

        try
        {
            c = getViewContext().getContainer(ACL.PERM_READ);
        }
        catch (UnauthorizedException e)
        {
            if (getViewContext().getUser().isGuest())
                showMessage("Please log in to see this data.");
            else
                showMessage("You do not have permission to see this data.");
            return;
        }

        GridView gridView = StatusController.getPartView(c, getViewContext().getUser(), null,
                StatusController.ShowPartRegionAction.class);
        if (gridView == null)
        {
            showMessage("Setup required.  Please contact your project administrator.");
            return;
        }

        gridView.setCustomizeLinks(getCustomizeLinks());
        include(gridView);
    }
}
