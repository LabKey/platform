package org.labkey.issue.actions;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.ActionNames;
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

import java.util.Collections;
import java.util.Map;

/**
 * Created by klum on 4/13/2016.
 */

@RequiresPermission(ReadPermission.class)
public class NewUpdateAction extends IssueUpdateAction
{
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

        _issue.beforeUpdate(getContainer());

        IssuePage page = new IssuePage(getContainer(), user);
        JspView v = new JspView<>("/org/labkey/issue/view/newUpdateView.jsp", page);

        page.setAction(NewUpdateAction.class);
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
        return root.addChild("update new issue");
/*
        return new IssuesController.DetailsAction(_issue, getViewContext()).appendNavTrail(root)
                .addChild("Update " + getSingularEntityName() + ": " + _issue.getTitle());
*/
    }
}
