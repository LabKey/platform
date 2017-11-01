/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.PanelButton;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

/**
 * A grid view of a subset of all experiment runs, typically of a given protocol or assay type.
 * User: jeckels
 * Date: Oct 12, 2006
 */
public class ExperimentRunListView extends QueryView
{
    private boolean _showAddToExperimentButton = false;
    private boolean _showRemoveFromExperimentButton = false;
    private boolean _showMoveRunsButton = false;
    private boolean _showUploadAssayRunsButton = false;

    @NotNull
    private final ExperimentRunType _selectedType;

    public ExperimentRunListView(UserSchema schema, QuerySettings settings, @NotNull ExperimentRunType selectedType)
    {
        super(schema, settings);
        _buttonBarPosition = DataRegion.ButtonBarPosition.TOP;
        _selectedType = selectedType;
        setShowDetailsColumn(false);
        setShowExportButtons(true);
        // The file export panel that we add later requires record selectors
        setShowRecordSelectors(true);
    }

    public static QuerySettings getRunListQuerySettings(UserSchema schema, ViewContext model, String tableName, boolean allowCustomizations)
    {
        QuerySettings settings = schema.getSettings(model, tableName, tableName);
        settings.getQueryDef(schema);
        settings.setBaseSort(new Sort("-RowId"));
        settings.setAllowChooseView(allowCustomizations);
        return settings;
    }


    @Override
    protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        renderHeaderView(request, response);
        super.renderView(model, request, response);
    }

    /** Optionally render a header in addition to the main grid */
    protected void renderHeaderView(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        _selectedType.renderHeader(request, response);
    }

    public static ExperimentRunListView createView(ViewContext model, ExperimentRunType selectedType, boolean allowCustomizations)
    {
        UserSchema schema = QueryService.get().getUserSchema(model.getUser(), model.getContainer(), selectedType.getSchemaName());
        if (schema == null)
        {
            // Fall back on a generic runs list instead of blowing up if we can't find the schema
            selectedType = ExperimentRunType.ALL_RUNS_TYPE;
            schema = QueryService.get().getUserSchema(model.getUser(), model.getContainer(), selectedType.getSchemaName());
        }
        return new ExperimentRunListView(schema, getRunListQuerySettings(schema, model, selectedType.getTableName(), allowCustomizations), selectedType);
    }

    public void setShowUploadAssayRunsButton(boolean showUploadAssayRunsButton)
    {
        _showUploadAssayRunsButton = showUploadAssayRunsButton;
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
            ActionURL removeRunUrl = PageFlowUtil.urlProvider(ExperimentUrls.class).getRemoveSelectedExpRunsURL(getContainer(), getReturnURL(), getExperiment());
            ActionButton removeRunAction = new ActionButton(removeRunUrl,"Remove");
            removeRunAction.setActionType(ActionButton.Action.POST);
            removeRunAction.setRequiresSelection(true);

            removeRunAction.setDisplayPermission(DeletePermission.class);
            bar.add(removeRunAction);
        }

        if (showDeleteButton())
        {
            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getDeleteSelectedExpRunsURL(context.getContainer(), getReturnURL());
            ActionButton deleteButton = new ActionButton(url, "Delete");
            deleteButton.setIconCls("trash");
            deleteButton.setActionType(ActionButton.Action.POST);
            deleteButton.setRequiresSelection(true);
            deleteButton.setDisplayPermission(DeletePermission.class);
            bar.add(deleteButton);
        }

        if (_showAddToExperimentButton && c.hasPermission(context.getUser(), InsertPermission.class))
        {
            MenuButton addToExperimentButton = new MenuButton("Add to run group");
            addToExperimentButton.setRequiresSelection(true);

            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getCreateRunGroupURL(getContainer(), getReturnURL(), true);
            String javascript = view.getDataRegion().getJavascriptFormReference() + ".method = \"POST\";\n " +
                    view.getDataRegion().getJavascriptFormReference() + ".action = " + PageFlowUtil.jsString(url + "&noPost=true") + ";\n " +
                    view.getDataRegion().getJavascriptFormReference() + ".submit();";
            addToExperimentButton.addMenuItem("Create new run group...", null, javascript);

            List<? extends ExpExperiment> experiments = ExperimentService.get().getExperiments(c, getViewContext().getUser(), true, false);
            if (experiments.size() > 0)
            {
                addToExperimentButton.addSeparator();
            }

            for (ExpExperiment exp : experiments)
            {
                ActionURL addRunUrl = PageFlowUtil.urlProvider(ExperimentUrls.class).getAddRunsToExperimentURL(getContainer(), exp);
                addToExperimentButton.addMenuItem(exp.getName(), null, "if (verifySelected(" + view.getDataRegion().getJavascriptFormReference() + ", \"" + addRunUrl.getLocalURIString() + "\", \"post\", \"run\")) { " + view.getDataRegion().getJavascriptFormReference() + ".submit(); }");
            }
            bar.add(addToExperimentButton);
        }

        if (_showMoveRunsButton)
        {
            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getMoveRunsLocationURL(getContainer());
            ActionButton deleteButton = new ActionButton(url, "Move");
            deleteButton.setActionType(ActionButton.Action.POST);
            deleteButton.setRequiresSelection(true);
            deleteButton.setDisplayPermission(DeletePermission.class);
            bar.add(deleteButton);
        }

        _selectedType.populateButtonBar(context, bar, view, getTable().getContainerFilter());

        if (_showUploadAssayRunsButton && c.hasPermission(getUser(), InsertPermission.class))
        {
            List<ExpProtocol> protocols = Collections.emptyList();
            if (AssayService.get() != null)
                protocols = AssayService.get().getAssayProtocols(getContainer());

            if (!protocols.isEmpty())
            {
                MenuButton addRunsButton;
                if(c.getFolderType().getForceAssayUploadIntoWorkbooks() && !c.isWorkbook())
                {
                    addRunsButton = new MenuButton("Upload Assay Runs"){
                        public void render(RenderContext ctx, Writer out) throws IOException
                        {
                            out.write("<script type=\"text/javascript\">\n");
                            out.write("LABKEY.requiresExt4ClientAPI()\n");
                            out.write("LABKEY.requiresScript('extWidgets/ImportWizard.js')\n");
                            out.write("</script>\n");
                            super.render(ctx, out);
                        }
                    };
                }
                else
                {
                    addRunsButton = new MenuButton("Upload Assay Runs");
                }

                for (ExpProtocol protocol : protocols)
                {
                    AssayProvider provider = AssayService.get().getProvider(protocol);
                    if (provider != null)
                    {
                        NavTree btn;
                        if(c.getFolderType().getForceAssayUploadIntoWorkbooks() && !c.isWorkbook())
                        {
                            btn = new NavTree(protocol.getName() + " (" + provider.getName() + ")");
                            btn.setScript("Ext4.create('LABKEY.ext.ImportWizardWin', {" +
                                "controller: '" + provider.getImportURL(c, protocol).getController() + "'," +
                                "action: '" + provider.getImportURL(c, protocol).getAction() + "'," +
                                "urlParams: {rowId: " + protocol.getRowId() + "}" +
                                "}).show();");
                        }
                        else
                        {
                            btn = new NavTree(protocol.getName() + " (" + provider.getName() + ")", provider.getImportURL(getContainer(), protocol));
                        }

                        addRunsButton.addMenuItem(btn);
                    }
                }
                bar.add(addRunsButton);
            }
        }
    }

    @Override
    @NotNull
    public PanelButton createExportButton(@Nullable List<String> recordSelectorColumns)
    {
        PanelButton result = super.createExportButton(recordSelectorColumns);
        String defaultFilenamePrefix = "Exported " + (getTitle() == null ? "Runs" : getTitle());

        HttpView filesView = ExperimentService.get().createFileExportView(getContainer(), defaultFilenamePrefix);
        result.addSubPanel("Files", filesView);

        HttpView xarView = ExperimentService.get().createRunExportView(getContainer(), defaultFilenamePrefix);
        result.addSubPanel("XAR", xarView);

        return result;
    }

    protected DataRegion createDataRegion()
    {
        DataRegion result = super.createDataRegion();
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
}
