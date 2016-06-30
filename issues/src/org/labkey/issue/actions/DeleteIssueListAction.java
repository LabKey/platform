package org.labkey.issue.actions;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.query.IssuesQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by klum on 6/30/2016.
 */
@RequiresPermission(AdminPermission.class)
public class DeleteIssueListAction extends FormViewAction<DeleteIssueListAction.DeleteIssueListForm>
{
    @Override
    public void validateCommand(DeleteIssueListForm target, Errors errors)
    {
    }

    @Override
    public ModelAndView getView(DeleteIssueListForm form, boolean reshow, BindException errors) throws Exception
    {
        if (!getContainer().getProject().hasPermission(getUser(), AdminPermission.class))
            throw new UnauthorizedException("You must be at least a project admin to be able to delete an issue list.");

        Set<String> ids = DataRegionSelection.getSelected(getViewContext(), null, true, true);

        for (String id : ids)
        {
            IssueListDef issueListDef = IssueManager.getIssueListDef(NumberUtils.toInt(id));
            if (issueListDef != null)
            {
                TableInfo table = issueListDef.createTable(getUser());

                form.getRowCounts().add(new TableSelector(table, null, null).getRowCount());
                form.getIssueDefNames().add(issueListDef.getName());
                form.getIssueDefId().add(issueListDef.getRowId());
            }
        }
        if (!form.getIssueDefId().isEmpty())
            return new JspView<>("/org/labkey/issue/view/deleteIssueList.jsp", form);
        else
            return new HtmlView("<span class='labkey-error'>Unable to find an Issue List with that ID.</span>");
    }

    @Override
    public boolean handlePost(DeleteIssueListForm form, BindException errors) throws Exception
    {
        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getTableInfoIssues().getSchema().getScope().ensureTransaction())
        {
            for (int id : form.getIssueDefId())
            {
                IssueManager.deleteIssueListDef(id, getContainer(), getUser());
            }
            transaction.commit();
        }
        return true;
    }

    @Override
    public URLHelper getSuccessURL(DeleteIssueListForm deleteIssueListForm)
    {
        return QueryService.get().urlFor(getUser(), getContainer(), QueryAction.executeQuery, "issues", IssuesQuerySchema.TableType.IssueListDef.name());
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }

    public static class DeleteIssueListForm
    {
        private List<Integer> _issueDefId = new ArrayList<>();
        private List<Long> _rowCounts = new ArrayList<>();
        private List<String> _issueDefNames = new ArrayList<>();

        public List<Integer> getIssueDefId()
        {
            return _issueDefId;
        }

        public void setIssueDefId(List<Integer> issueDefIds)
        {
            _issueDefId = issueDefIds;
        }

        public List<Long> getRowCounts()
        {
            return _rowCounts;
        }

        public List<String> getIssueDefNames()
        {
            return _issueDefNames;
        }
    }
}
