/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.assay;

import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.assay.plate.PlateUrls;
import org.springframework.validation.BindException;

/**
 * User: brittp
 * Date: Jun 28, 2007
 * Time: 5:07:02 PM
 */
public class AssayListQueryView extends QueryView
{
    public AssayListQueryView(ViewContext context, QuerySettings settings, BindException errors)
    {
        super(AssayManager.get().createSchema(context.getUser(), context.getContainer(), null), settings, errors);
        setShowExportButtons(false);
        setShowDetailsColumn(false);
        setShowRecordSelectors(false);
        setShadeAlternatingRows(true);
        setShowBorders(true);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (getContainer().hasPermission(getUser(), DesignAssayPermission.class))
        {
            ActionURL insertURL = PageFlowUtil.urlProvider(AssayUrls.class).getChooseAssayTypeURL(view.getViewContext().getContainer());
            insertURL.addReturnURL(getViewContext().getActionURL());
            ActionButton insert = new ActionButton("New Assay Design", insertURL);
            insert.setActionType(ActionButton.Action.LINK);
            insert.setDisplayPermission(DesignAssayPermission.class);
            bar.add(insert);
        }

        // getProject() returns null in the root container
        Container project = getContainer().getProject() == null ? getContainer() : getContainer().getProject();
        if (project != null && !project.equals(getContainer()) && project.hasPermission(getUser(), DesignAssayPermission.class))
        {
            ActionURL manageProjectAssays = PageFlowUtil.urlProvider(AssayUrls.class).getBeginURL(project);
            ActionButton sharedButton = new ActionButton("Manage Project Assays", manageProjectAssays);
            sharedButton.setActionType(ActionButton.Action.LINK);
            bar.add(sharedButton);
        }

        Container sharedProject = ContainerManager.getSharedContainer();
        if (!sharedProject.equals(getContainer()) && !sharedProject.equals(project) &&
                sharedProject.hasPermission(getUser(), DesignAssayPermission.class))
        {
            ActionURL manageProjectAssays = PageFlowUtil.urlProvider(AssayUrls.class).getBeginURL(sharedProject);
            ActionButton sharedButton = new ActionButton("Manage Shared Project Assays", manageProjectAssays);
            sharedButton.setActionType(ActionButton.Action.LINK);
            bar.add(sharedButton);
        }

        if (getContainer().hasPermission(getUser(), DesignAssayPermission.class))
        {
            ActionURL plateURL = PageFlowUtil.urlProvider(PlateUrls.class).getPlateTemplateListURL(getContainer());
            plateURL.addReturnURL(getViewContext().getActionURL());
            ActionButton insert = new ActionButton("Configure Plate Templates", plateURL);
            insert.setActionType(ActionButton.Action.LINK);
            insert.setDisplayPermission(DesignAssayPermission.class);
            bar.add(insert);
        }
    }

    @Override
    public DataView createDataView()
    {
        DataView result = super.createDataView();
        result.getRenderContext().setBaseSort(new Sort("Name"));
        return result;
    }
}
