package org.labkey.issue.experimental.view;

import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.IssuesController;
import org.labkey.issue.experimental.IssuesListView;
import org.labkey.issue.experimental.actions.NewInsertAction;
import org.labkey.issue.experimental.actions.NewListAction;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;

import java.sql.SQLException;
import java.util.Map;

/**
 * Created by klum on 5/27/2016.
 */
public class SummaryWebPart extends JspView<IssuesController.SummaryBean>
{
    public SummaryWebPart(Map<String, String> propertyMap)
    {
        super("/org/labkey/issue/view/summaryWebpart.jsp", new IssuesController.SummaryBean());

        IssuesController.SummaryBean bean = getModelBean();

        ViewContext context = getViewContext();
        Container c = context.getContainer();

        String issueDefName = propertyMap.get(IssuesListView.ISSUE_LIST_DEF_NAME);

        //set specified web part title
        Object title = propertyMap.get("title");
        if (title == null)
            title = "New " + IssueManager.getEntryTypeNames(c).pluralName + " Summary : " + issueDefName;
        setTitle(title.toString());

        User u = context.getUser();
        bean.hasPermission = c.hasPermission(u, ReadPermission.class);
        if (!bean.hasPermission)
            return;

        ActionURL listUrl = new ActionURL(NewListAction.class, getViewContext().getContainer()).
                addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDefName).
                addParameter(DataRegion.LAST_FILTER_PARAM, true);

        setTitleHref(listUrl);

        bean.listURL = listUrl;
        bean.insertURL = new ActionURL(NewInsertAction.class, c).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDefName);

        try
        {
            IssueListDef issueListDef = IssueManager.getIssueListDef(getViewContext().getContainer(), issueDefName);
            bean.bugs = IssueManager.getSummary(c, issueListDef);
        }
        catch (SQLException x)
        {
            setVisible(false);
        }
    }
}
