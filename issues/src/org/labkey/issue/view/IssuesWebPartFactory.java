package org.labkey.issue.view;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
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

        WebPartView view;

        if (issueDefName != null)
            view = new IssuesListView(issueDefName);
        else
            view = new HtmlView(IssuesController.getUndefinedIssueListMessage(context, issueDefName));

        view.setTitle("Issues List : " + StringUtils.trimToEmpty(issueDefName));
        view.setFrame(WebPartView.FrameType.PORTAL);

        return view;
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new IssuesListView.IssuesListConfig(webPart);
    }
}
