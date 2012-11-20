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

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.data.*;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.study.controllers.assay.AssayController;
import org.springframework.validation.BindException;

import java.util.List;
import java.util.ArrayList;

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

    protected DataRegion createDataRegion()
    {
        DataRegion rgn = super.createDataRegion();
        rgn.setShowRecordSelectors(false);
        List<DisplayColumn> displayCols = new ArrayList<DisplayColumn>();
        String[] displayColNames = { "name", "description", "type", "created", "modified"};

        for (DisplayColumn col : rgn.getDisplayColumns())
        {
            String colName = col.getName();
            for (String displayColName : displayColNames)
            {
                if (displayColName.equalsIgnoreCase(colName) || !(col instanceof DataColumn))
                {
                    displayCols.add(col);
                    break;
                }
            }
        }
        rgn.setDisplayColumns(displayCols);
        return rgn;
    }

    @Override
    public DataView createDataView()
    {
        DataView result = super.createDataView();
        result.getDataRegion().setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        return result;
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        bar.add(createViewButton(getItemFilter()));
        if (getContainer().hasPermission(getUser(), DesignAssayPermission.class))
        {
            ActionURL insertURL = new ActionURL(AssayController.ChooseAssayTypeAction.class, view.getViewContext().getContainer());
            insertURL.addParameter(ActionURL.Param.returnUrl, getViewContext().getActionURL().getLocalURIString());
            bar.add(new ActionButton("New Assay Design", insertURL));
        }

        bar.add(new ActionButton("Manage Assays", new ActionURL(AssayController.BeginAction.class, getContainer())));
    }
}
