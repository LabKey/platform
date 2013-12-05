/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.study.designer.view;

import org.labkey.api.data.*;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GridView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.designer.DesignerController;
import org.labkey.study.designer.StudyDesignManager;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * User: Mark Igra
 * Date: Feb 15, 2007
 * Time: 4:45:19 PM
 */
public class StudyDesignsWebPart extends GridView
{
    public StudyDesignsWebPart(ViewContext ctx, boolean inPortal)
    {
        super(new DataRegion(), (BindException)null);

        TableInfo table = StudyDesignManager.get().getStudyDesignTable();
        QuerySettings settings = new QuerySettings(ctx, table.getName());
        settings.setSelectionKey(DataRegionSelection.getSelectionKey(table.getSchema().getName(), table.getName(), null, table.getName()));

        DataRegion dr = getDataRegion();
        dr.setSettings(settings);
        dr.addColumns(table.getColumns("StudyId", "Label", "Modified", "PublicRevision","Container", "Active"));
        dr.getDisplayColumn("StudyId").setCaption("Id");

        ActionURL helper = new ActionURL(DesignerController.DesignerAction.class, ctx.getContainer());
        dr.getDisplayColumn("Label").setURL(helper.toString() + "&studyId=${studyId}");
        dr.getDisplayColumn("PublicRevision").setCaption("Revision");
        dr.getDisplayColumn("Container").setVisible(false);
        dr.getDisplayColumn("Active").setVisible(false);
        DisplayColumn dc = new DataColumn(table.getColumn("Active")) {

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                boolean value = ((Boolean)super.getValue(ctx)).booleanValue();
                if (value)
                {
                    Map<String, String> style = new HashMap<>();
                    style.put("style", "white-space:nowrap");
                    out.write(PageFlowUtil.textLink("Go To Study Folder", renderURL(ctx), "", "", style));
                }
                else
                {
                    out.write("&nbsp;");
                }
            }

            @Override
            public String getCaption()
            {
                return "";
            }
        };
        final ActionURL studyFolderUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(ctx.getContainer());
        DetailsURL url = new DetailsURL(studyFolderUrl);
        url.setContainerContext(new ContainerContext.FieldKeyContext(FieldKey.fromParts("Container")));
        dc.setURLExpression(url);
        dr.addDisplayColumn(dc);
        ButtonBar bb = new ButtonBar();
        if (ctx.getContainer().hasPermission(ctx.getUser(), InsertPermission.class))
        {
            helper.addParameter("edit", "true");
            bb.add(new ActionButton("New Protocol", helper));
        }
        if (inPortal)
        {
            if (ctx.getContainer().hasPermission(ctx.getUser(), UpdatePermission.class))
            {
                ActionURL adminURL = new ActionURL(DesignerController.BeginAction.class, ctx.getContainer());
                bb.add(new ActionButton("Manage Protocols", adminURL));
            }
        }
        else
        {
            if (ctx.getContainer().hasPermission(ctx.getUser(), DeletePermission.class))
            {
                dr.setShowRecordSelectors(true);
                bb.add(ActionButton.BUTTON_DELETE);
            }
            else
                dr.setShowRecordSelectors(false);

            if (ctx.getContainer().getProject().hasPermission(ctx.getUser(), AdminPermission.class))
            {
                ActionURL templateHelper = helper.clone();
                templateHelper.setAction(DesignerController.EditTemplateAction.class);
                bb.add(new ActionButton("Edit Template for Project", templateHelper));
            }
        }

        dr.setButtonBar(bb);
        setTitle("Vaccine Study Protocols");
        setSort(new Sort("Label"));
        getRenderContext().setUseContainerFilter(false);
        setFilter(new SimpleFilter(FieldKey.fromParts("SourceContainer"), ctx.getContainer()));
    }
}
