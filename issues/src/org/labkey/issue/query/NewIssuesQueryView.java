package org.labkey.issue.query;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueListDef;
import org.springframework.validation.BindException;

/**
 * Created by klum on 4/11/2016.
 */
public class NewIssuesQueryView extends QueryView
{
    private IssueListDef _issueDef;

    public NewIssuesQueryView(IssueListDef issueDef, ViewContext context, UserSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);

        _issueDef = issueDef;
        setShowDetailsColumn(false);
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (view.getDataRegion().getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
        {
            ViewContext context = getViewContext();

            ActionURL viewDetailsURL = context.cloneActionURL().setAction(IssuesController.DetailsListAction.class);
            ActionButton listDetailsButton = new ActionButton(viewDetailsURL, "View Details");
            listDetailsButton.setActionType(ActionButton.Action.POST);
            listDetailsButton.setRequiresSelection(true);
            listDetailsButton.setDisplayPermission(ReadPermission.class);
            listDetailsButton.setNoFollow(true);
            bar.add(listDetailsButton);

/*
            ActionButton moveButton = new ActionButton("Move");
            moveButton.setRequiresSelection(true);
            moveButton.setDisplayPermission(AdminPermission.class);
            moveButton.setScript("Issues.window.MoveIssue.create(LABKEY.DataRegions['" + view.getDataRegion().getName() + "'].getChecked());");
            bar.add(moveButton);
*/
            Domain domain = _issueDef.getDomain(getUser());
            if (domain != null)
            {
                // for now just attach the domain editor to the admin button, eventually build this out to the final admin UI
                ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(context.getContainer(), domain.getTypeURI(), true, false, false);
                ActionButton adminButton = new ActionButton(url, "Admin", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                adminButton.setDisplayPermission(AdminPermission.class);

                bar.add(adminButton);
            }

            if (!getUser().isGuest())
            {
                ActionButton prefsButton = new ActionButton(IssuesController.EmailPrefsAction.class, "Email Preferences", DataRegion.MODE_GRID, ActionButton.Action.LINK);
                bar.add(prefsButton);
            }
        }
    }
}
