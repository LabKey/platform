/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.issue.query;

import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.NavTree;
import org.labkey.common.util.Pair;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IssuesQueryView extends QueryView
{
    private ViewContext _context;

    public IssuesQueryView(ViewContext context, UserSchema schema, QuerySettings settings)
    {
        super(schema, settings);
        _context = context;
        setShowDetailsColumn(false);
        getSettings().setAllowChooseQuery(false);
    }

    // MAB: I just want a resultset....
    public ResultSet getResultSet() throws SQLException, IOException
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        ResultSet rs = rgn.getResultSet(view.getRenderContext());
        return rs;
    }
    
    public DataView createDataView()
    {
        DataView view = super.createDataView();

        if (view.getDataRegion().getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
            view.getDataRegion().setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
        view.getDataRegion().setShowRecordSelectors(true);
        view.getDataRegion().setRecordSelectorValueColumns("IssueId");
        view.getDataRegion().setShadeAlternatingRows(true);
        view.getDataRegion().setShowBorders(true);

        DisplayColumn issueid = view.getDataRegion().getDisplayColumn("IssueId");
        if (null != issueid)
            issueid.setURL(new ActionURL("issues", "details", getContainer()).toString() + "issueId=${IssueId}");

        //ensureDefaultCustomViews();
        return view;
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);
        
        if (view.getDataRegion().getButtonBarPosition() != DataRegion.ButtonBarPosition.NONE)
        {
            String viewDetailsURL = _context.cloneActionURL().setAction("detailsList.view").getEncodedLocalURIString();
            ActionButton listDetailsButton = new ActionButton("button", "View Details");
            listDetailsButton.setScript("return verifySelected(this.form, \"" + viewDetailsURL + "\", \"post\", \"rows\")");
            listDetailsButton.setActionType(ActionButton.Action.GET);
            listDetailsButton.setDisplayPermission(ACL.PERM_READ);
            bar.add(listDetailsButton);

            ActionButton adminButton = new ActionButton(_context.cloneActionURL().setAction("admin.view").getEncodedLocalURIString(), "Admin", DataRegion.MODE_GRID, ActionButton.Action.LINK);
            adminButton.setDisplayPermission(ACL.PERM_ADMIN);
            bar.add(adminButton);

            ActionButton prefsButton = new ActionButton(_context.cloneActionURL().setAction("emailPrefs.view").getEncodedLocalURIString(), "Email Preferences", DataRegion.MODE_GRID, ActionButton.Action.LINK);
            bar.add(prefsButton);
        }
    }

    protected void addGridViews(MenuButton menu, ActionURL target, String currentView)
    {
        ActionURL url = target.clone().deleteParameters();
        NavTree item = new NavTree("all", url);
        if (currentView == "")
            item.setSelected(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        url = target.clone().deleteParameters();
        url.addFilter("Issues", FieldKey.fromString("Status"), CompareType.EQUAL, "open");
        Sort sort = new Sort("AssignedTo/DisplayName");
        sort.insertSortColumn("Milestone", true);
        sort.applyURLSort(url, getDataRegionName());
        url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());        
        item = new NavTree("open", url);
        if (currentView == "")
            item.setSelected(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        url = target.clone().deleteParameters();
        url.addFilter("Issues", FieldKey.fromString("Status"), CompareType.EQUAL, "resolved");
        sort = new Sort("AssignedTo/DisplayName");
        sort.insertSortColumn("Milestone", true);
        sort.applyURLSort(url, getDataRegionName());
        url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());
        item = new NavTree("resolved", url);
        if (currentView == "")
            item.setSelected(target.toString().equals(url.toString()));
        menu.addMenuItem(item);

        if (!getUser().isGuest())
        {
            url = target.clone().deleteParameters();
            url.addFilter("Issues", FieldKey.fromString("AssignedTo/DisplayName"), CompareType.EQUAL, getUser().getDisplayName(getViewContext()));
            url.addFilter("Issues", FieldKey.fromString("Status"), CompareType.NEQ_OR_NULL, "closed");
            sort = new Sort("-Milestone");
            sort.applyURLSort(url, getDataRegionName());
            url.addParameter(getDataRegionName() + ".sort", sort.getSortParamValue());
            item = new NavTree("mine", url);
            if (currentView == "")
                item.setSelected(target.toString().equals(url.toString()));
            menu.addMenuItem(item);
        }
        menu.addSeparator();
        Map<String, CustomView> customViews = getQueryDef().getCustomViews(getViewContext().getUser(), getViewContext().getRequest());
        for (CustomView view : customViews.values())
        {
            if (view.isHidden())
                continue;
            String label = view.getName();
            if (label == null)
                continue;

            item = new NavTree(label, target.clone().replaceParameter(param(QueryParam.viewName), label).getLocalURIString());
            item.setId("Views:" + label);
            if (label.equals(currentView))
                item.setSelected(true);
            else
                item.setImageSrc(getViewContext().getContextPath() + "/reports/grid.gif");
            menu.addMenuItem(item);
        }
    }

    protected void populateReportButtonBar(ButtonBar bar)
    {
        super.populateReportButtonBar(bar);

        ActionButton adminButton = new ActionButton(_context.cloneActionURL().setAction("admin.view").getEncodedLocalURIString(), "Admin", DataRegion.MODE_GRID, ActionButton.Action.LINK);
        adminButton.setDisplayPermission(ACL.PERM_ADMIN);
        bar.add(adminButton);

        ActionButton prefsButton = new ActionButton(_context.cloneActionURL().setAction("emailPrefs.view").getEncodedLocalURIString(), "Email Preferences", DataRegion.MODE_GRID, ActionButton.Action.LINK);
        bar.add(prefsButton);
    }

    public Map<String, String> getReports()
    {
        Map<String, String> reports = new HashMap<String, String>();
        addReportsToChangeViewPicker(reports);

        return reports;
    }
    
    protected void setupDataView(DataView view)
    {
        // We need to set the base sort _before_ calling super.setupDataView.  If the user
        // has set a sort on their custom view, we want their sort to take precedence.
        view.getRenderContext().setBaseSort(new Sort("-IssueId"));
        super.setupDataView(view);
    }

    private static final String CUSTOM_VIEW_ALL = "all";
    private static final String CUSTOM_VIEW_OPEN = "open";
    private static final String CUSTOM_VIEW_RESOLVED = "resolved";

    private void ensureDefaultCustomViews()
    {
        UserSchema schema = QueryService.get().getUserSchema(null, _context.getContainer(), IssuesQuerySchema.SCHEMA_NAME);
        QueryDefinition qd = schema.getQueryDefForTable("Issues");
        Map<String, CustomView> views = qd.getCustomViews(null, _context.getRequest());

        if (!views.containsKey(CUSTOM_VIEW_ALL))
        {
            CustomView view = qd.createCustomView(null, CUSTOM_VIEW_ALL);
            List<FieldKey> columns = new ArrayList<FieldKey>();

            view.setIsHidden(true);
            columns.add(FieldKey.fromParts("IssueId"));
            columns.add(FieldKey.fromParts("Type"));
            columns.add(FieldKey.fromParts("Area"));
            columns.add(FieldKey.fromParts("Title"));
            columns.add(FieldKey.fromParts("AssignedTo"));
            columns.add(FieldKey.fromParts("Priority"));
            columns.add(FieldKey.fromParts("Status"));
            columns.add(FieldKey.fromParts("Milestone"));
            view.setColumns(columns);

            view.setFilterAndSortFromURL(new ActionURL().addParameter("Issues.sort", "-Milestone,AssignedTo/DisplayName"), "Issues");
            view.save(null, null);
        }

        if (!views.containsKey(CUSTOM_VIEW_OPEN))
        {
            CustomView view = qd.createCustomView(null, CUSTOM_VIEW_OPEN);
            List<FieldKey> columns = new ArrayList<FieldKey>();

            view.setIsHidden(true);
            columns.add(FieldKey.fromParts("IssueId"));
            columns.add(FieldKey.fromParts("Type"));
            columns.add(FieldKey.fromParts("Area"));
            columns.add(FieldKey.fromParts("Title"));
            columns.add(FieldKey.fromParts("AssignedTo"));
            columns.add(FieldKey.fromParts("Priority"));
            columns.add(FieldKey.fromParts("Status"));
            columns.add(FieldKey.fromParts("Milestone"));
            view.setColumns(columns);

            view.setFilterAndSortFromURL(new ActionURL().addParameter("Issues.sort", "-Milestone,AssignedTo/DisplayName").
                    addParameter("Issues.Status~eq", "open"), "Issues");
            view.save(null, null);
        }

        if (!views.containsKey(CUSTOM_VIEW_RESOLVED))
        {
            CustomView view = qd.createCustomView(null, CUSTOM_VIEW_RESOLVED);
            List<FieldKey> columns = new ArrayList<FieldKey>();

            view.setIsHidden(true);
            columns.add(FieldKey.fromParts("IssueId"));
            columns.add(FieldKey.fromParts("Type"));
            columns.add(FieldKey.fromParts("Area"));
            columns.add(FieldKey.fromParts("Title"));
            columns.add(FieldKey.fromParts("AssignedTo"));
            columns.add(FieldKey.fromParts("Priority"));
            columns.add(FieldKey.fromParts("Status"));
            columns.add(FieldKey.fromParts("Milestone"));
            view.setColumns(columns);

            view.setFilterAndSortFromURL(new ActionURL().addParameter("Issues.sort", "-Milestone,AssignedTo/DisplayName").
                    addParameter("Issues.Status~eq", "resolved"), "Issues");
            view.save(null, null);
        }
    }

    public ActionURL getCustomizeURL()
    {
        return urlFor(QueryAction.chooseColumns);
    }

    protected ActionURL urlFor(QueryAction action)
    {
        switch (action)
        {
            case exportRowsTsv:
                final ActionURL url =  _context.cloneActionURL().setAction("exportTsv.view");
                for (Pair<String, String> param : super.urlFor(action).getParameters())
                {
                    url.addParameter(param.getKey(), param.getValue());
                }
                return url;
        }
        return super.urlFor(action);
    }
}
