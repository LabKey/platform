package org.labkey.issue.view;

import org.labkey.api.data.DataRegion;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
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

    public IssuesListView(String issueDefName)
    {
        String dataRegionName = "issues-" + issueDefName;
        UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), IssuesQuerySchema.SCHEMA_NAME);
        QuerySettings settings = schema.getSettings(getViewContext(), dataRegionName, issueDefName);
        settings.getBaseSort().insertSortColumn(FieldKey.fromParts("IssueId"), Sort.SortDirection.DESC);

        QueryView queryView = schema.createView(getViewContext(), settings, null);

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
