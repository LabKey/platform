/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

        User user = getViewContext().getUser();
        PipelineService service = PipelineService.get();

        URI uriRoot = null;
        boolean canModify = service.canModifyPipelineRoot(user, c);
        PipeRoot pr = service.findPipelineRoot(c);
        if (pr != null)
        {
            uriRoot = pr.getUri(c);
        }

        if (uriRoot == null && !canModify)
        {
            showMessage("Setup required.  Please contact your project administrator.");
            return;
        }

        DataRegion rgn = new StatusDataRegion();
        rgn.setColumns(PipelineStatusManager.getTableInfo().getColumns("Status, Created, FilePath, Description"));
        DisplayColumn col = rgn.getDisplayColumn("FilePath");
        col.setVisible(false);
        col = rgn.getDisplayColumn("Description");
        col.setVisible(false);
        col = new DescriptionDisplayColumn(uriRoot);
        col.setWidth("500");
        rgn.addColumn(col);

        String referer = PipelineController.RefererValues.protal.toString();
        ButtonBar bb = new ButtonBar();

        if (c.hasPermission(user, ACL.PERM_INSERT) && uriRoot != null)
        {
            ActionURL url = PipelineController.urlBrowse(c, referer);
            ActionButton button = new ActionButton(url, "Process and Import Data");
            button.setActionType(ActionButton.Action.GET);
            bb.add(button);
        }

        if (canModify)
        {
            ActionURL url = PipelineController.urlSetup(c, referer);
            ActionButton button = new ActionButton(url, "Setup");
            button.setActionType(ActionButton.Action.GET);
            bb.add(button);
        }

        rgn.setButtonBar(bb, DataRegion.MODE_GRID);

        rgn.getDisplayColumn(0).setURL(StatusController.urlDetailsData(c));

        GridView gridView = new GridView(rgn);
        gridView.setCustomizeLinks(getCustomizeLinks());
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Status", PipelineJob.COMPLETE_STATUS, CompareType.NEQ);
        gridView.setFilter(filter);
        gridView.setSort(new Sort("-Created"));
        include(gridView);
    }
}
