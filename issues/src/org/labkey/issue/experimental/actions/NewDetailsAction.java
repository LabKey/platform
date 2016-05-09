package org.labkey.issue.experimental.actions;

import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.DataRegion;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssuePage;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;

/**
 * Created by klum on 5/3/2016.
 */
@RequiresPermission(ReadPermission.class)
public class NewDetailsAction extends AbstractIssueAction
{
    public NewDetailsAction(){}
    public NewDetailsAction(Issue issue, ViewContext context)
    {
        _issue = issue;
        setViewContext(context);
    }

    @Override
    public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
    {
        int issueId = form.getIssueId();
        _issue = getIssue(issueId, true);

        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
        if (null == _issue)
        {
            throw new NotFoundException("Unable to find " + names.singularName + " " + form.getIssueId());
        }

        IssuePage page = new IssuePage(getContainer(), getUser());
        page.setMode(DataRegion.MODE_DETAILS);
        page.setPrint(isPrint());
        page.setIssue(_issue);
        page.setCustomColumnConfiguration(getColumnConfiguration());
        //pass user's update perms to jsp page to determine whether to show notify list
        page.setUserHasUpdatePermissions(hasUpdatePermission(getUser(), _issue));
        page.setUserHasAdminPermissions(hasAdminPermission(getUser(), _issue));
        page.setMoveDestinations(IssueManager.getMoveDestinationContainers(getContainer()).size() != 0 ? true : false);
        page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
        page.setIssueListDef(getIssueListDef());

        NotificationService.get().markAsRead(getContainer(), getUser(), "issue:" + _issue.getIssueId(), Arrays.asList(Issue.class.getName()), getUser().getUserId());
        return new JspView<>("/org/labkey/issue/experimental/view/detailView.jsp", page);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return new NewListAction(getViewContext()).appendNavTrail(root).
                addChild(getSingularEntityName() + " " + _issue.getIssueId() + ": " + _issue.getTitle(), getURL());
    }

    public ActionURL getURL()
    {
        return new ActionURL(NewDetailsAction.class, getContainer()).addParameter("issueId", _issue.getIssueId());
    }
}
