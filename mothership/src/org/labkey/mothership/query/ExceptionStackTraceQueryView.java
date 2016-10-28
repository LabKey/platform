/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.mothership.query;

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.MenuButton;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.mothership.MothershipController;
import org.labkey.mothership.MothershipManager;
import org.springframework.validation.BindException;

/**
 * User: jeckels
 * Date: Feb 23, 2012
 */
public class ExceptionStackTraceQueryView extends QueryView
{
    public ExceptionStackTraceQueryView(MothershipSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);
    }

    @Override
    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (getContainer().hasPermission(getUser(), UpdatePermission.class))
        {
            MenuButton assignToButton = new MenuButton("Assign To");
            assignToButton.setRequiresSelection(true);
            assignToButton.setDisplayPermission(UpdatePermission.class);
            String form = "LABKEY.DataRegions." + getDataRegionName() + ".form";
            for (User user : MothershipManager.get().getAssignedToList(getSchema().getContainer()))
            {
                ActionURL url = new ActionURL(MothershipController.BulkUpdateAction.class, getContainer());
                url.addParameter("userId", user.getUserId());
                String script = "if (verifySelected("+form+", '" + url +
                        "', 'post', 'rows')) "+form+".submit();";

                assignToButton.addMenuItem(user.getDisplayName(getSchema().getUser()), null, script);
            }
            assignToButton.addSeparator();
            ActionURL ignoreURL = new ActionURL(MothershipController.BulkUpdateAction.class, getContainer());
            ignoreURL.addParameter("ignore", true);
            String ignoreScript = "if (verifySelected("+form+", '" + ignoreURL +
                    "', 'post', 'rows')) "+form+".submit();";
            assignToButton.addMenuItem("Ignore", null, ignoreScript);
            bar.add(assignToButton);
        }
    }
}
