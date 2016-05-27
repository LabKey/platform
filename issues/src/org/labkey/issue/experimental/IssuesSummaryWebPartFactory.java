package org.labkey.issue.experimental;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.issue.IssuesController;
import org.labkey.issue.experimental.view.SummaryWebPart;

import java.util.Map;

/**
 * Created by klum on 5/27/2016.
 */
public class IssuesSummaryWebPartFactory extends BaseWebPartFactory
{
    public static final String NAME = "New Issues Summary";

    public IssuesSummaryWebPartFactory()
    {
        super(NAME, true, true);
    }

    public WebPartView getWebPartView(@NotNull ViewContext context, @NotNull Portal.WebPart webPart)
    {
        WebPartView view = new SummaryWebPart(webPart.getPropertyMap());

        return view;
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new IssuesListView.IssuesListConfig(webPart);
    }
}
