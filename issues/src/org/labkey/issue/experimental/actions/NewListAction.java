package org.labkey.issue.experimental.actions;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.DataRegion;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.IssuesController;
import org.labkey.issue.experimental.IssuesListView;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.query.IssuesQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by klum on 5/3/2016.
 */
@RequiresPermission(ReadPermission.class)
public class NewListAction extends SimpleViewAction<IssuesController.ListForm>
{
    public NewListAction(){}
    public NewListAction(ViewContext ctx)
    {
        setViewContext(ctx);
    }

    @Override
    public ModelAndView getView(IssuesController.ListForm form, BindException errors) throws Exception
    {
        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
        String issueDefName = getViewContext().getActionURL().getParameter(IssuesListView.ISSUE_LIST_DEF_NAME);

        // convert AssignedTo/Email to AssignedTo/DisplayName: old bookmarks
        // reference Email, which is no longer displayed.
        ActionURL url = getViewContext().cloneActionURL();
        String[] emailFilters = url.getKeysByPrefix(IssuesQuerySchema.TableType.Issues.name() + ".AssignedTo/Email");
        if (emailFilters != null && emailFilters.length > 0)
        {
            for (String emailFilter : emailFilters)
                url.deleteParameter(emailFilter);
            return HttpView.redirect(url);
        }

        //getPageConfig().setRssProperties(new IssuesController.RssAction().getUrl(), names.pluralName.toString());

        return new IssuesListView(issueDefName);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
        return root.addChild(names.pluralName + " List", getURL());
    }

    public ActionURL getURL()
    {
        return new ActionURL(NewListAction.class, getContainer()).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }
}
