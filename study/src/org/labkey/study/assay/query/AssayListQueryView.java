/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.study.assay.query;

import org.labkey.api.data.*;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.study.assay.PlateUrls;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.assay.AssayController;
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
        super(new AssaySchemaImpl(context.getUser(), context.getContainer(), null), settings, errors);
        setShowExportButtons(false);
        setShowDetailsColumn(false);
        setShowRecordSelectors(false);
        setShadeAlternatingRows(true);
        setShowBorders(true);
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (getContainer().hasPermission(getUser(), DesignAssayPermission.class))
        {
            ActionURL insertURL = new ActionURL(AssayController.ChooseAssayTypeAction.class, view.getViewContext().getContainer());
            insertURL.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().getLocalURIString());
            ActionButton insert = new ActionButton("New Assay Design", insertURL);
            insert.setActionType(ActionButton.Action.LINK);
            insert.setDisplayPermission(DesignAssayPermission.class);
            bar.add(insert);
        }

        // getProject() returns null in the root container
        Container project = getContainer().getProject() == null ? getContainer() : getContainer().getProject();
        if (project != null && !project.equals(getContainer()) && project.hasPermission(getUser(), DesignAssayPermission.class))
        {
            ActionURL manageProjectAssays = new ActionURL(AssayController.BeginAction.class, project);
            ActionButton sharedButton = new ActionButton("Manage Project Assays", manageProjectAssays);
            sharedButton.setActionType(ActionButton.Action.LINK);
            bar.add(sharedButton);
        }

        Container sharedProject = ContainerManager.getSharedContainer();
        if (!sharedProject.equals(getContainer()) && !sharedProject.equals(project) &&
                sharedProject.hasPermission(getUser(), DesignAssayPermission.class))
        {
            ActionURL manageProjectAssays = new ActionURL(AssayController.BeginAction.class, sharedProject);
            ActionButton sharedButton = new ActionButton("Manage Shared Project Assays", manageProjectAssays);
            sharedButton.setActionType(ActionButton.Action.LINK);
            bar.add(sharedButton);
        }

        if (getContainer().hasPermission(getUser(), DesignAssayPermission.class))
        {
            ActionURL plateURL = PageFlowUtil.urlProvider(PlateUrls.class).getPlateTemplateListURL(getContainer());
            plateURL.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().getLocalURIString());
            ActionButton insert = new ActionButton("Configure Plate Templates", plateURL);
            insert.setActionType(ActionButton.Action.LINK);
            insert.setDisplayPermission(DesignAssayPermission.class);
            bar.add(insert);
        }
    }

    public DataView createDataView()
    {
        DataView result = super.createDataView();
        result.getRenderContext().setBaseSort(new Sort("Name"));
        return result;
    }
}
