package org.labkey.issue.view;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.issue.model.IssueManager;

import java.util.Map;

/**
 * Created by klum on 5/2/2016.
 */
public class IssuesWebPartFactory extends BaseWebPartFactory
{
    public IssuesWebPartFactory()
    {
        super("Issues List", true, true);
    }

    public WebPartView getWebPartView(@NotNull ViewContext context, @NotNull Portal.WebPart webPart)
    {
        Map<String, String> properties = webPart.getPropertyMap();
        String issueDefName = properties.get(IssuesListView.ISSUE_LIST_DEF_NAME);
        if (issueDefName == null)
            issueDefName = IssueManager.getDefaultIssueListDefName(context.getContainer());

        WebPartView result;
        if (issueDefName != null)
            result = new IssuesListView(issueDefName);
        else
            result = new HtmlView("<span class='labkey-error'>There are no issues lists defined for this folder.</span>");

        result.setTitle("Issues List : " + StringUtils.trimToEmpty(issueDefName));
        result.setFrame(WebPartView.FrameType.PORTAL);

        return result;
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new IssuesListView.IssuesListConfig(webPart);
    }
}
