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

import org.labkey.api.data.DataRegion;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.issue.IssuesController;
import org.labkey.issue.query.IssuesQuerySchema;

import java.io.PrintWriter;

/**
 * Created by klum on 5/1/2016.
 */
public class IssuesListView extends VBox
{
    public static final String ISSUE_LIST_DEF_NAME = "issueDefName";
    public static final String ISSUE_LIST_DEF_ID = "issueDefId";

    public IssuesListView(String issueDefName)
    {
        String dataRegionName = "issues-" + issueDefName;
        UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), IssuesQuerySchema.SCHEMA_NAME);
        QuerySettings settings = schema.getSettings(getViewContext(), dataRegionName, issueDefName);

        QueryView queryView = schema.createView(getViewContext(), settings, null);

        // check for any legacy custom view parameters, and if so display a warning
        if (settings.getViewName() == null)
        {
            QuerySettings legacySettings = schema.getSettings(getViewContext(), "Issues", issueDefName);
            if (legacySettings.getViewName() != null)
            {
                ActionURL url = getViewContext().cloneActionURL().
                        deleteParameter(legacySettings.param(QueryParam.viewName)).
                        addParameter(settings.param(QueryParam.viewName), legacySettings.getViewName());

                HtmlView warning = new HtmlView("<span class='labkey-error'>The specified URL contains an obsolete viewName parameter and is being ignored. " +
                        "Please update your bookmark to this new URL : <a target='blank' href='" + PageFlowUtil.filter(url.getLocalURIString()) + "'>" + PageFlowUtil.filter(url.getURIString()) + "</a></span>");
                addView(warning);
            }
        }
        // add the header for buttons and views
        addView(new JspView<>("/org/labkey/issue/view/list.jsp", issueDefName));
        addView(queryView);

        setTitleHref(new ActionURL(IssuesController.ListAction.class, getViewContext().getContainer()).
                addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDefName).
                addParameter(DataRegion.LAST_FILTER_PARAM, true));
    }


    public static class IssuesListConfig extends HttpView
    {
        private Portal.WebPart _webPart;

        public IssuesListConfig(Portal.WebPart webPart)
        {
            _webPart = webPart;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            JspView view = new JspView<>("/org/labkey/issue/view/issueListWebPartConfig.jsp", _webPart);
            include(view);
        }
    }
}
