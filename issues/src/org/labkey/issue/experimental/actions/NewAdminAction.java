package org.labkey.issue.experimental.actions;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.NavTree;
import org.labkey.issue.IssueUpdateEmailTemplate;
import org.labkey.issue.IssuesController;
import org.labkey.issue.experimental.IssuesListView;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by klum on 5/11/2016.
 */
@RequiresPermission(AdminPermission.class)
public class NewAdminAction extends SimpleViewAction<IssuesController.AdminForm>
{
    @Override
    public ModelAndView getView(IssuesController.AdminForm adminForm, BindException errors) throws Exception
    {
        String issueDefName = getViewContext().getActionURL().getParameter(IssuesListView.ISSUE_LIST_DEF_NAME);
        IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), issueDefName);
        Domain domain = issueListDef.getDomain(getUser());

        Map<String, String> props = new HashMap<>();
        props.put("typeURI", domain.getTypeURI());

        props.put("issueListUrl", new ActionURL(NewListAction.class, getContainer()).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDefName).getLocalURIString());
        props.put("customizeEmailUrl", PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeEmailURL(getContainer(), IssueUpdateEmailTemplate.class, getViewContext().getActionURL()).getLocalURIString());
        props.put("instructions", domain.getDomainKind().getDomainEditorInstructions());

        return new GWTView("org.labkey.issues.Designer", props);
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
        return (new NewListAction(getViewContext())).appendNavTrail(root).addChild(names.pluralName + " Admin Page", new ActionURL(NewAdminAction.class, getContainer()));
    }
}
