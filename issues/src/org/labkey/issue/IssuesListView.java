package org.labkey.issue;

import org.labkey.api.query.*;
import org.labkey.api.view.JspView;
import org.labkey.api.view.VBox;
import org.labkey.issue.query.IssuesQuerySchema;
import org.labkey.issue.query.IssuesQueryView;

import java.util.Map;

/**
 * User: jeckels
 * Date: May 28, 2010
 */
public class IssuesListView extends VBox
{
    public IssuesListView()
    {
        UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), IssuesQuerySchema.SCHEMA_NAME);
        QuerySettings settings = schema.getSettings(getViewContext(), IssuesQuerySchema.TableType.Issues.name(), IssuesQuerySchema.TableType.Issues.name());
        IssuesQueryView queryView = new IssuesQueryView(getViewContext(), schema, settings);

        // add the header for buttons and views
        QueryDefinition qd = schema.getQueryDefForTable(IssuesQuerySchema.TableType.Issues.name());
        Map<String, CustomView> views = qd.getCustomViews(getViewContext().getUser(), getViewContext().getRequest());
        // don't include a customized default view in the list
        if (views.containsKey(null))
            views.remove(null);

        addView(new JspView("/org/labkey/issue/list.jsp"));
        addView(queryView);

        setTitleHref(IssuesController.getListURL(getViewContext().getContainer()));
    }
}
