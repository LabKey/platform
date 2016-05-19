package org.labkey.issue.experimental.actions;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.DataRegion;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssuePage;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by klum on 5/5/2016.
 */
@RequiresPermission(InsertPermission.class)
public class NewInsertAction extends AbstractIssueAction
{
    @Override
    public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
    {
        _issue = reshow ? form.getBean() : new Issue();
        _issue.setIssueDefName(form.getIssueDefName());
        // if we have errors, then form.getBean() is likely to throw, but try anyway
/*
        if (errors.hasErrors())
        {
            try
            {
                _issue = reshow ? form.getBean() : new Issue();
            }
            catch (Exception e)
            {
                _issue = new Issue();
            }
        }
        else
        {
            _issue = reshow ? form.getBean() : new Issue();
        }
*/

        if (_issue.getAssignedTo() != null)
        {
            User user = UserManager.getUser(_issue.getAssignedTo());

            if (user != null)
            {
                _issue.setAssignedTo(user.getUserId());
            }
        }

        User defaultUser = IssueManager.getDefaultAssignedToUser(getContainer());
        if (defaultUser != null)
            _issue.setAssignedTo(defaultUser.getUserId());

        _issue.open(getContainer(), getUser());
        if (!reshow || form.getSkipPost())
        {
            // Set the defaults if we're not reshowing after an error, or if this is a request to open an issue
            // from a mothership which comes in as a POST and is therefore considered a reshow
            setNewIssueDefaults(_issue);
        }

        if (NumberUtils.isNumber(form.getPriority()))
            _issue.setPriority(Integer.parseInt(form.getPriority()));

        IssuePage page = new IssuePage(getContainer(), getUser());
        JspView v = new JspView<>("/org/labkey/issue/experimental/view/updateView.jsp", page);

        page.setAction(NewInsertAction.class);
        page.setMode(DataRegion.MODE_UPDATE);

        page.setIssue(_issue);
        page.setPrevIssue(_issue);
        page.setCustomColumnConfiguration(getColumnConfiguration());
        page.setBody(form.getComment() == null ? form.getBody() : form.getComment());
        page.setCallbackURL(form.getCallbackURL());
        page.setEditable(getEditableFields(page.getAction(), getColumnConfiguration()));
        page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
        page.setErrors(errors);
        page.setIssueListDef(getIssueListDef());

        return v;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
        return new NewListAction(getViewContext()).appendNavTrail(root).addChild("Insert New " + names.singularName);
    }

    @Override
    public ActionURL getSuccessURL(IssuesController.IssuesForm issuesForm)
    {
        if (!StringUtils.isEmpty(issuesForm.getCallbackURL()))
        {
            ActionURL url = new ActionURL(issuesForm.getCallbackURL());
            url.addParameter("issueId", _issue.getIssueId());
            url.addParameter("assignedTo", _issue.getAssignedTo());
            return url;
        }

        return new NewDetailsAction(_issue, getViewContext()).getURL();
    }
}
