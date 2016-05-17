package org.labkey.issue.experimental.query;

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.IssuesController;
import org.labkey.issue.experimental.IssuesListView;
import org.labkey.issue.experimental.actions.NewAdminAction;
import org.labkey.issue.experimental.actions.NewDetailsListAction;
import org.labkey.issue.model.IssueListDef;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by klum on 4/11/2016.
 */
public class IssuesQueryView extends QueryView
{
    private IssueListDef _issueDef;

    public IssuesQueryView(IssueListDef issueDef, ViewContext context, UserSchema schema, QuerySettings settings, BindException errors)
    {
        super(schema, settings, errors);

        _issueDef = issueDef;
        setShowDetailsColumn(false);
        setShowInsertNewButton(false);
        setShowImportDataButton(false);
        setShowUpdateColumn(false);
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        if (view.getDataRegion().getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
        {
            ViewContext context = getViewContext();

            ActionURL viewDetailsURL = context.cloneActionURL().setAction(NewDetailsListAction.class);
            viewDetailsURL.replaceParameter(IssuesListView.ISSUE_LIST_DEF_NAME, _issueDef.getName());
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
                ActionURL url = new ActionURL(NewAdminAction.class, getContainer()).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, _issueDef.getName());
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

    @Override
    protected void addGridViews(MenuButton menu, URLHelper target, String currentView)
    {
        URLHelper url = target.clone().deleteParameters();
        NavTree item = new NavTree("all", url);
        if ("".equals(currentView))
            item.setStrong(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        url = target.clone().deleteParameters();
        url.addFilter(getDataRegionName(), FieldKey.fromString("Status"), CompareType.EQUAL, "open");
        Sort sort = new Sort("AssignedTo/DisplayName");
        sort.insertSortColumn("Milestone", true);
        sort.addURLSort(url, getDataRegionName());
        url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());
        item = new NavTree("open", url);
        if ("".equals(currentView))
            item.setStrong(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        url = target.clone().deleteParameters();
        url.addFilter(getDataRegionName(), FieldKey.fromString("Status"), CompareType.EQUAL, "resolved");
        sort = new Sort("AssignedTo/DisplayName");
        sort.insertSortColumn("Milestone", true);
        sort.addURLSort(url, getDataRegionName());
        url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());
        item = new NavTree("resolved", url);
        if ("".equals(currentView))
            item.setStrong(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        if (!getUser().isGuest())
        {
            url = target.clone().deleteParameters();
            url.addFilter(getDataRegionName(), FieldKey.fromString("AssignedTo/DisplayName"), CompareType.EQUAL, getUser().getDisplayName(getViewContext().getUser()));
            url.addFilter(getDataRegionName(), FieldKey.fromString("Status"), CompareType.NEQ_OR_NULL, "closed");
            sort = new Sort("-Milestone");
            sort.addURLSort(url, getDataRegionName());
            url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());
            item = new NavTree("mine", url);
            if ("".equals(currentView))
                item.setStrong(target.toString().equals(url.toString()));
            menu.addMenuItem(item);
        }

        // sort the grid view alphabetically, with private views over public ones
        List<CustomView> views = new ArrayList<>(getQueryDef().getCustomViews(getViewContext().getUser(), getViewContext().getRequest(), false, false).values());
        Collections.sort(views, new Comparator<CustomView>() {
            public int compare(CustomView o1, CustomView o2)
            {
                if (!o1.isShared() && o2.isShared()) return -1;
                if (o1.isShared() && !o2.isShared()) return 1;
                if (o1.getName() == null) return -1;
                if (o2.getName() == null) return 1;

                return o1.getName().compareTo(o2.getName());
            }
        });

        boolean addSep = true;

        // issues doesn't preserve any URL sorts or filters because they may have been introduced by
        // the built in filter views.
        // TODO: replace these views with programatically filtered ones so we can leave URL filters on
        target.deleteParameters();

        for (CustomView view : views)
        {
            String label = view.getName();
            if (label == null)
                continue;

            if (addSep)
            {
                menu.addSeparator();
                addSep = false;
            }
            item = new NavTree(label, target.clone().replaceParameter(param(QueryParam.viewName), label).getLocalURIString());
            item.setId("GridViews:" + PageFlowUtil.filter(label));
            if (label.equals(currentView))
                item.setStrong(true);

            StringBuilder description = new StringBuilder();
            if (view.isSession())
            {
                item.setEmphasis(true);
                description.append("Unsaved ");
            }
            if (view.isShared())
                description.append("Shared ");
            if (description.length() > 0)
                item.setDescription(description.toString());

            if (view.isShared())
                item.setImageSrc(getViewContext().getContextPath() + "/reports/grid_shared.gif");
            else
            {
                item.setImageSrc(getViewContext().getContextPath() + "/reports/grid.gif");
                item.setImageCls("fa fa-table");
            }
            menu.addMenuItem(item);
        }
    }
}
