/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.issue.view;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueManager;

import java.util.Map;

/**
 * Created by klum on 5/2/2016.
 */
public class IssuesWebPartFactory extends BaseWebPartFactory
{
    private boolean _showCustomizeOnInsert;

    public IssuesWebPartFactory()
    {
        super("Issues List", true, true);
    }

    public WebPartView getWebPartView(@NotNull ViewContext context, @NotNull Portal.WebPart webPart)
    {
        Map<String, String> properties = webPart.getPropertyMap();
        String issueDefName = properties.get(IssuesListView.ISSUE_LIST_DEF_NAME);
        _showCustomizeOnInsert = IssueManager.getDefaultIssueListDefName(context.getContainer()) != null;

        if (issueDefName == null)
            issueDefName = IssueManager.getDefaultIssueListDefName(context.getContainer());

        WebPartView view;

        if (issueDefName != null)
            view = new IssuesListView(issueDefName);
        else
            view = new HtmlView(IssuesController.getUndefinedIssueListMessage(context, issueDefName));

        view.setTitle("Issues List : " + StringUtils.trimToEmpty(issueDefName));
        view.setFrame(WebPartView.FrameType.PORTAL);

        return view;
    }

    @Override
    public boolean showCustomizeOnInsert()
    {
        return _showCustomizeOnInsert;
    }

    @Nullable
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new IssuesListView.IssuesListConfig(webPart);
    }
}
