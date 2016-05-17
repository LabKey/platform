package org.labkey.issue.experimental.actions;

import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssuePage;
import org.labkey.issue.query.IssuesQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by klum on 5/16/2016.
 */
@RequiresPermission(ReadPermission.class)
public class NewDetailsListAction extends AbstractIssueAction
{
    @Override
    public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
    {
        _issue = new Issue();
        _issue.setIssueDefName(form.getIssueDefName());

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

        Set<String> issueIds = DataRegionSelection.getSelected(getViewContext(), false);
        if (issueIds.isEmpty())
        {
            issueIds = new LinkedHashSet<>();

            UserSchema userSchema = QueryService.get().getUserSchema(getUser(), getContainer(), IssuesQuerySchema.SCHEMA_NAME);
            TableInfo table = userSchema.getTable(form.getIssueDefName());
            List<String> ids = new TableSelector(table, Collections.singleton(table.getColumn(FieldKey.fromParts("issueId"))),
                    null, null).getArrayList(String.class);

            issueIds.addAll(ids);
        }

        IssuePage page = new IssuePage(getContainer(), getUser());
        page.setPrint(isPrint());
        JspView v = new JspView<>("/org/labkey/issue/experimental/view/detailList.jsp", page);

        page.setMode(DataRegion.MODE_DETAILS);
        page.setIssueIds(issueIds);
        page.setCustomColumnConfiguration(getColumnConfiguration());
        page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
        page.setDataRegionSelectionKey(DataRegionSelection.getSelectionKeyFromRequest(getViewContext()));
        page.setIssueListDef(getIssueListDef());

        getPageConfig().setNoIndex(); // We want crawlers to index the single issue detail page, no the multiple page
        getPageConfig().setNoFollow();

        return v;
    }

    @Override
    public boolean handlePost(IssuesController.IssuesForm form, BindException errors) throws Exception
    {
        return true;
    }

    @Override
    public ActionURL getSuccessURL(IssuesController.IssuesForm form)
    {
        return null;
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
        return new NewListAction(getViewContext()).appendNavTrail(root).addChild(names.singularName + " Details");
    }
}
