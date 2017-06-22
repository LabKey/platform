/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.issue.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.view.IssuesListView;
import org.springframework.validation.BindException;

import java.util.LinkedHashSet;

public class IssuesQueryView extends QueryView
{
    private IssueListDef _issueDef;

    public IssuesQueryView(IssueListDef issueDef, ViewContext context, UserSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);

        _issueDef = issueDef;
        setShowDetailsColumn(false);
        setShowInsertNewButton(false);
        setShowImportDataButton(false);
        setShowUpdateColumn(false);
    }

    @NotNull
    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = super.getClientDependencies();
        resources.add(ClientDependency.fromPath("Ext4"));
        resources.add(ClientDependency.fromPath("issues/move.js"));
        return resources;
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (view.getDataRegion().getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
        {
            ViewContext context = getViewContext();

            ActionURL viewDetailsURL = context.cloneActionURL().setAction(IssuesController.DetailsListAction.class);
            viewDetailsURL.replaceParameter(IssuesListView.ISSUE_LIST_DEF_NAME, _issueDef.getName());
            ActionButton listDetailsButton = new ActionButton(viewDetailsURL, "View Details");
            listDetailsButton.setActionType(ActionButton.Action.POST);
            listDetailsButton.setRequiresSelection(true);
            listDetailsButton.setDisplayPermission(ReadPermission.class);
            listDetailsButton.setNoFollow(true);
            bar.add(listDetailsButton);

            ActionButton moveButton = new ActionButton("Move");
            moveButton.setRequiresSelection(true);
            moveButton.setDisplayPermission(AdminPermission.class);
            moveButton.setScript("Issues.window.MoveIssue.create(LABKEY.DataRegions['" + view.getDataRegion().getName() + "'].getChecked(), " + PageFlowUtil.jsString(_issueDef.getName()) + ");");
            bar.add(moveButton);

            Domain domain = _issueDef.getDomain(getUser());
            if (domain != null)
            {
                ActionURL url = new ActionURL(IssuesController.AdminAction.class, getContainer()).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, _issueDef.getName());
                ActionButton adminButton = new ActionButton(url, "Admin", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                adminButton.setDisplayPermission(AdminPermission.class);

                bar.add(adminButton);
            }

            if (!getUser().isGuest())
            {
                ActionURL url = new ActionURL(IssuesController.EmailPrefsAction.class, getContainer()).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, _issueDef.getName());
                ActionButton prefsButton = new ActionButton(url, "Email Preferences", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                bar.add(prefsButton);
            }
        }
    }
}
