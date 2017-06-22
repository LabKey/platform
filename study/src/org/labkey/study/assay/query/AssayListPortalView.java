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

package org.labkey.study.assay.query;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.assay.AssayController;
import org.springframework.validation.BindException;

/**
 * User: ulberge
 * Date: Aug 14, 2007
 */
public class AssayListPortalView extends AssayListQueryView
{
    public AssayListPortalView(ViewContext context, QuerySettings settings, BindException errors)
    {
        super(context, settings, errors);
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        bar.add(createViewButton(getItemFilter()));
        bar.add(createReportButton());
        if (getContainer().hasPermission(getUser(), DesignAssayPermission.class))
        {
            ActionURL insertURL = new ActionURL(AssayController.ChooseAssayTypeAction.class, view.getViewContext().getContainer());
            insertURL.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().getLocalURIString());
            bar.add(new ActionButton("New Assay Design", insertURL));
        }

        bar.add(new ActionButton("Manage Assays", new ActionURL(AssayController.BeginAction.class, getContainer())));
    }
}
