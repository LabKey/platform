/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.issue;

import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 */
public class IssuesActivityWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public IssuesActivityWebPartFactory()
    {
        super("Issues Activity", WebPartFactory.LOCATION_RIGHT, false, false);
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        return new IssuesActivityWebPart();
    }

    public static class IssuesActivityBean
    {
        public boolean hasPermission = false;
        public ActionURL listURL;
        public ActionURL insertURL;

        public List<Issue.Comment> comments;
    }

    private static class IssuesActivityWebPart extends JspView<IssuesActivityBean>
    {
        public IssuesActivityWebPart()
        {
            super("/org/labkey/issue/activityWebpart.jsp", new IssuesActivityBean());

            IssuesActivityBean bean = getModelBean();

            ViewContext context = getViewContext();
            Container c = context.getContainer();

            //set specified web part title
            Object title = context.get("title");
            if (title == null)
                title = IssueManager.getEntryTypeNames(c).pluralName + " Activity";
            setTitle(title.toString());

            User u = context.getUser();
            bean.hasPermission = c.hasPermission(u, ReadPermission.class);
            if (!bean.hasPermission)
                return;

            setTitleHref(IssuesController.getListURL(c));

            bean.listURL = IssuesController.getListURL(c).deleteParameters();

            bean.insertURL = IssuesController.issueURL(context.getContainer(), IssuesController.InsertAction.class);

            //bean.rssURL = IssuesController.

            bean.comments = IssuesController.getRecentComments(c, u, 20);
        }
    }
}
