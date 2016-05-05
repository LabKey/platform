package org.labkey.issue.experimental.actions;

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssuePage;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by klum on 5/5/2016.
 */
@RequiresPermission(ReadPermission.class)
public class NewReopenAction extends IssueUpdateAction
{
    @Override
    public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
    {
        int issueId = form.getIssueId();
        _issue = getIssue(issueId, true);
        if (_issue == null)
        {
            throw new NotFoundException();
        }

        Issue prevIssue = (Issue)_issue.clone();

        User user = getUser();
        requiresUpdatePermission(user, _issue);

        _issue.beforeReOpen(getContainer(), true);
        _issue.open(getContainer(), user);

        IssuePage page = new IssuePage(getContainer(), user);
        JspView v = new JspView<>("/org/labkey/issue/experimental/view/updateView.jsp", page);

        page.setAction(NewReopenAction.class);
        page.setIssue(_issue);
        page.setPrevIssue(prevIssue);
        page.setCustomColumnConfiguration(getColumnConfiguration());
        page.setBody(form.getComment());
        page.setEditable(getEditableFields(page.getAction(), getColumnConfiguration()));
        page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
        page.setErrors(errors);

        return v;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
        return (new NewDetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Reopen " + names.singularName);
    }
}
