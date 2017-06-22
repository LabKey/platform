/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;

import java.sql.SQLException;
import java.util.Map;

/**
 * Created by klum on 5/27/2016.
 */
public class SummaryWebPart extends JspView<IssuesController.SummaryBean>
{
    public SummaryWebPart(String issueDefName, Map<String, String> propertyMap)
    {
        super("/org/labkey/issue/view/summaryWebpart.jsp", new IssuesController.SummaryBean());

        IssuesController.SummaryBean bean = getModelBean();

        ViewContext context = getViewContext();
        Container c = context.getContainer();

        //set specified web part title
        Object title = propertyMap.get("title");
        if (title == null)
            title = IssueManager.getEntryTypeNames(c, issueDefName).pluralName + " Summary : " + issueDefName;
        setTitle(title.toString());

        User u = context.getUser();
        bean.hasPermission = c.hasPermission(u, ReadPermission.class);
        if (!bean.hasPermission)
            return;

        ActionURL listUrl = new ActionURL(IssuesController.ListAction.class, getViewContext().getContainer()).
                addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDefName).
                addParameter(DataRegion.LAST_FILTER_PARAM, true);

        setTitleHref(listUrl);

        bean.issueDefName = issueDefName;
        bean.insertURL = new ActionURL(IssuesController.InsertAction.class, c).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDefName);

        try
        {
            IssueListDef issueListDef = IssueManager.getIssueListDef(getViewContext().getContainer(), issueDefName);
            bean.bugs = IssueManager.getSummary(c, u, issueListDef);
        }
        catch (SQLException x)
        {
            setVisible(false);
        }
    }
}
