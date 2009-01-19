/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.*;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;

/**
 * User: jeckels
 * Date: Jan 28, 2008
 */
public class ExperimentRunGroupsView extends VBox
{
    public ExperimentRunGroupsView(User user, final Container c, final ExpRun run, final ActionURL currentURL)
    {
        QuerySettings settings = new QuerySettings(getViewContext(), "Experiments", ExpSchema.EXPERIMENTS_MEMBERSHIP_FOR_RUN_TABLE_NAME);
        settings.setAllowChooseQuery(false);
        QueryView experimentsView = new QueryView(new ExpSchema(user, c), settings)
        {
            protected TableInfo createTable()
            {
                return ((ExpSchema)getSchema()).createExperimentsTableWithRunMemberships(null, run);
            }

            protected void populateButtonBar(DataView view, ButtonBar bar)
            {
                ActionButton createButton = new ActionButton(ExperimentController.ExperimentUrlsImpl.get().getCreateRunGroupURL(c, currentURL, false), "Create new group");
                createButton.setDisplayPermission(ACL.PERM_UPDATE);
                createButton.setActionType(ActionButton.Action.LINK);
                bar.add(createButton);

                bar.add(new SimpleTextDisplayElement("<span id=\"experimentRunGroupMembershipStatus\" />", true));
            }
        };
        experimentsView.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTTOM);
        experimentsView.setShowDetailsColumn(false);

        addView(experimentsView);


        Collapsible collapsible = new WebPartCollapsible("experimentRunGroup");
        NavTreeManager.applyExpandState(collapsible, getViewContext());
        enableExpandCollapse("experimentRunGroup", !collapsible.isCollapsed());
        setTitle("Run Groups");

        setTitlePopupHelp("Run Groups", "Run groups let you define sets of runs that are related. For example, you might create separate groups\n" +
                "for your case and controls, so that you can easily select them.");

        setFrame(FrameType.PORTAL);
    }
}
