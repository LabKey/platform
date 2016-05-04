package org.labkey.issue.experimental;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

import java.util.Map;

/**
 * Created by klum on 5/2/2016.
 */
public class IssuesWebPartFactory extends AlwaysAvailableWebPartFactory
{

    public IssuesWebPartFactory()
    {
        super("New Issues List", true, true);
    }

    public WebPartView getWebPartView(@NotNull ViewContext context, @NotNull Portal.WebPart webPart)
    {
        Map<String, String> properties = webPart.getPropertyMap();
        String issueDefName = properties.get(IssuesListView.ISSUE_LIST_DEF_NAME);

        IssuesListView result = new IssuesListView(issueDefName);
        result.setTitle("Issues List : " + issueDefName);
        result.setFrame(WebPartView.FrameType.PORTAL);

        return result;
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new IssuesListView.IssuesListConfig(webPart);
    }
}
