package org.labkey.issue.experimental.actions;

import org.apache.poi.hssf.record.chart.DatRecord;
import org.labkey.api.data.DataRegion;
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
public class NewCloseAction extends AbstractIssueAction
{
    @Override
    public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
    {
        int issueId = form.getIssueId();
        _issue = getIssue(issueId, true);
        if (null == _issue)
        {
            throw new NotFoundException();
        }

        Issue prevIssue = (Issue)_issue.clone();
        User user = getUser();
        requiresUpdatePermission(user, _issue);

        _issue.close(user);

        IssuePage page = new IssuePage(getContainer(), user);
        JspView v = new JspView<>("/org/labkey/issue/experimental/view/updateView.jsp", page);

        page.setAction(NewCloseAction.class);
        page.setMode(DataRegion.MODE_UPDATE);
        page.setIssue(_issue);
        page.setPrevIssue(prevIssue);
        page.setCustomColumnConfiguration(getColumnConfiguration());
        page.setBody(form.getComment());
        page.setEditable(getEditableFields(page.getAction(), getColumnConfiguration()));
        page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
        page.setErrors(errors);
        page.setIssueListDef(getIssueListDef());

        return v;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
        return (new NewDetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Close " + names.singularName);
    }
}
