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

package org.labkey.api.exp;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;

/**
 * User: jeckels
 * Date: Oct 12, 2006
 */
public class ExperimentRunListView extends QueryView
{
    public static final String STATUS_ELEMENT_ID = "experimentRunGroupMembershipStatus";

    private boolean _showDeleteButton = false;
    private boolean _showAddToExperimentButton = false;
    private boolean _showRemoveFromExperimentButton = false;
    private boolean _showExportXARButton = false;
    private boolean _showMoveRunsButton = false;
    private boolean _includeSubfolders = false;

    private final ExperimentRunFilter _selectedFilter;

    public ExperimentRunListView(UserSchema schema, QuerySettings settings, ExperimentRunFilter selectedFilter)
    {
        super(schema, settings);
        _buttonBarPosition = DataRegion.ButtonBarPosition.BOTTOM;
        _selectedFilter = selectedFilter;
        setShowDetailsColumn(false);
        setShowExportButtons(false);
        setShowRecordSelectors(true);
    }

    public static QuerySettings getRunListQuerySettings(UserSchema schema, ViewContext model, String tableName, boolean allowCustomizations)
    {
        QuerySettings settings = new QuerySettings(model, tableName);
        settings.setSchemaName(schema.getSchemaName());
        settings.getQueryDef(schema);
        settings.setAllowChooseQuery(false);
        settings.setAllowChooseView(allowCustomizations);
        settings.setQueryName(tableName);
        return settings;
    }

    public static ExperimentRunListView createView(ViewContext model, ExperimentRunFilter selectedFilter, boolean allowCustomizations)
    {
        UserSchema schema = QueryService.get().getUserSchema(model.getUser(), model.getContainer(), selectedFilter.getSchemaName());
        return new ExperimentRunListView(schema, getRunListQuerySettings(schema, model, selectedFilter.getTableName(), allowCustomizations), selectedFilter);
    }


    public DataView createDataView()
    {
        DataView result = super.createDataView();
        result.getRenderContext().setBaseSort(new Sort("-RowId"));
        return result;
    }

    public void setShowDeleteButton(boolean showDelete)
    {
        _showDeleteButton = showDelete;
    }
    
    public void setShowAddToRunGroupButton(boolean showAddToExperimentButton)
    {
        _showAddToExperimentButton = showAddToExperimentButton;
    }
    
    public void setShowMoveRunsButton(boolean showMoveRunsButton)
    {
        _showMoveRunsButton = showMoveRunsButton;
    }

    public void setShowRemoveFromExperimentButton(boolean showRemoveFromExperimentButton)
    {
        _showRemoveFromExperimentButton = showRemoveFromExperimentButton;
    }

    public boolean isIncludeSubfolders()
    {
        return _includeSubfolders;
    }

    public void setIncludeSubfolders(boolean includeSubfolders)
    {
        _includeSubfolders = includeSubfolders;
    }

    private ExpExperiment getExperiment()
    {
        return getRunTable().getExperiment();
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        super.populateButtonBar(view, bar);

        // Can't use view.getViewContext(), since it's not rendering
        // should probably pass in ViewContext
        ViewContext context = HttpView.currentContext();
        Container c = context.getContainer();

        if (_showRemoveFromExperimentButton)
        {
            getExperiment();
            ActionURL removeRunUrl = PageFlowUtil.urlProvider(ExperimentUrls.class).getRemoveSelectedExpRunsURL(getContainer(), context.getActionURL(), getExperiment());
            ActionButton removeRunAction = new ActionButton("","Remove");
            String script = "return verifySelected(this.form, \"" + removeRunUrl.getLocalURIString() + "\", \"post\", \"run\")";
            removeRunAction.setScript(script);
            removeRunAction.setActionType(ActionButton.Action.POST);

            removeRunAction.setDisplayPermission(ACL.PERM_DELETE);
            bar.add(removeRunAction);
        }

        if (_showDeleteButton)
        {
            ActionButton deleteButton = new ActionButton("button", "Delete");
            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getDeleteSelectedExpRunsURL(context.getContainer(), context.getActionURL());
            deleteButton.setScript("return verifySelected(this.form, \"" + url.getLocalURIString() + "\", \"post\", \"runs\")");
            deleteButton.setActionType(ActionButton.Action.POST);
            deleteButton.setDisplayPermission(ACL.PERM_DELETE);
            bar.add(deleteButton);
        }

        if (_showAddToExperimentButton && c.hasPermission(context.getUser(), ACL.PERM_INSERT))
        {
            MenuButton addToExperimentButton = new MenuButton("Add to run group");

            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getCreateRunGroupURL(getViewContext().getContainer(), getViewContext().getActionURL(), true);
            String javascript = "javascript: " + view.getDataRegion().getJavascriptFormReference(false) + ".method = \"POST\";\n " +
                    view.getDataRegion().getJavascriptFormReference(false) + ".action = " + PageFlowUtil.jsString(url + "&noPost=true") + ";\n " +
                    view.getDataRegion().getJavascriptFormReference(false) + ".submit();";
            addToExperimentButton.addMenuItem("Create new run group...", javascript);

            ExpExperiment[] experiments = ExperimentService.get().getExperiments(c, getViewContext().getUser(), true);
            if (experiments.length > 0)
            {
                addToExperimentButton.addSeparator();
            }

            for (ExpExperiment exp : experiments)
            {
                ActionURL addRunUrl = PageFlowUtil.urlProvider(ExperimentUrls.class).getAddRunsToExperimentURL(getContainer(), exp);
                addToExperimentButton.addMenuItem(exp.getName(), null, "if (verifySelected(" + view.getDataRegion().getJavascriptFormReference(false) + ", \"" + addRunUrl.getLocalURIString() + "\", \"post\", \"run\")) { " + view.getDataRegion().getJavascriptFormReference(false) + ".submit(); }");
            }
            bar.add(addToExperimentButton);
        }

        if (_showMoveRunsButton)
        {
            ActionButton deleteButton = new ActionButton("button", "Move");
            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getMoveRunsLocationURL(getContainer());
            deleteButton.setScript("return verifySelected(this.form, \"" + url.getLocalURIString() + "\", \"post\", \"runs\")");
            deleteButton.setActionType(ActionButton.Action.POST);
            deleteButton.setDisplayPermission(ACL.PERM_DELETE);
            bar.add(deleteButton);
        }

        if (_showExportXARButton)
        {
            ActionURL exportUrl = PageFlowUtil.urlProvider(ExperimentUrls.class).getExportRunsOptionsURL(context.getContainer(), getExperiment());

            ActionButton exportXAR = new ActionButton("", "Export XAR");
            exportXAR.setScript("return verifySelected(this.form, \"" + exportUrl.getLocalURIString() + "\", \"post\", \"experiment run\")");
            exportXAR.setActionType(ActionButton.Action.POST);
            bar.add(exportXAR);
        }

        _selectedFilter.populateButtonBar(context, bar, view);

        if (getViewContext().hasPermission(ACL.PERM_UPDATE))
        {
            bar.add(new SimpleTextDisplayElement("<span id=\"" + STATUS_ELEMENT_ID + "\" />", true));
        }
    }

    protected DataRegion createDataRegion()
    {
        DataRegion result = super.createDataRegion();
        result.setShadeAlternatingRows(true);
        result.setShowBorders(true);
        result.setName(_selectedFilter.getTableName());
        for (DisplayColumn column : result.getDisplayColumns())
        {
            if (column.getCaption().startsWith("Experiment Run "))
            {
                column.setCaption(column.getCaption().substring("Experiment Run ".length()));
            }
        }
        result.setRecordSelectorValueColumns("RowId");
        if (getRunTable().getExperiment() != null)
        {
            result.addHiddenFormField("expLSID", getRunTable().getExperiment().getLSID());
        }
        return result;
    }

    protected ActionURL urlFor(QueryAction action)
    {
        switch (action)
        {
            case deleteQueryRows:
                return null;
        }
        return super.urlFor(action);
    }

    public ExpRunTable getRunTable()
    {
        return (ExpRunTable)getTable();
    }

    @Override
    protected TableInfo createTable()
    {
        ExpRunTable table = (ExpRunTable)super.createTable();
        if (_includeSubfolders)
            table.setContainerFilter(ContainerFilter.CURRENT_AND_SUBFOLDERS);
        return table;
    }

    public void setShowExportXARButton(boolean showExportXARButton)
    {
        _showExportXARButton = showExportXARButton;
    }
}
