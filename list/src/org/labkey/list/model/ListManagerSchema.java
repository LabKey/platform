/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.list.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.PanelButton;
import org.labkey.api.data.Sort;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.list.controllers.ListController;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by joec on 8/18/2014.
 */
public class ListManagerSchema extends UserSchema
{
    private static final Set<String> TABLE_NAMES;
    public static final String LIST_MANAGER = "ListManager";
    public static final String SCHEMA_NAME = "ListManager";

    static
    {
        Set<String> names = new TreeSet<>();
        names.add(LIST_MANAGER);
        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    public ListManagerSchema(User user, Container container)
    {
        super(SCHEMA_NAME, "Contains list of lists", user, container, ExperimentService.get().getSchema());
        _hidden = true;
    }

    public static void register(Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return true;
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new ListManagerSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    @Nullable
    @Override
    public TableInfo createTable(String name)
    {
        if (LIST_MANAGER.equalsIgnoreCase(name))
        {
            TableInfo dbTable = getDbSchema().getTable("list");
            ListManagerTable table = new ListManagerTable(this, dbTable);
            table.setName("Available Lists");
            return table;
        }
        else
        {
            return null;
        }
    }

    @Override
    protected QuerySettings createQuerySettings(String dataRegionName, String queryName, String viewName)
    {
        QuerySettings settings = super.createQuerySettings(dataRegionName, queryName, viewName);
        if (LIST_MANAGER.equalsIgnoreCase(queryName))
        {
            settings.setBaseSort(new Sort("Name"));
        }
        return settings;
    }

    @Override
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        if (null != settings.getQueryName() && settings.getQueryName().equalsIgnoreCase(LIST_MANAGER))
        {
            return new QueryView(this, settings, errors)
            {
                QuerySettings s = getSettings();

                @Override
                protected void populateButtonBar(DataView view, ButtonBar bar)
                {
                    bar.add(super.createViewButton(getViewItemFilter()));
                    bar.add(createReportButton());
                    bar.add(createCreateNewListButton());
                    bar.add(createDeleteButton());
                    List<String> recordSelectorColumns = view.getDataRegion().getRecordSelectorValueColumns();
                    bar.add(super.createExportButton(recordSelectorColumns));
                    bar.add(createImportListArchiveButton());
                    bar.add(createExportArchiveButton());
                }

                private ActionButton createCreateNewListButton()
                {
                    ActionURL urlCreate = new ActionURL(ListController.EditListDefinitionAction.class, getContainer());
                    urlCreate.addReturnURL(getReturnURL());
                    ActionButton btnCreate = new ActionButton(urlCreate, "Create New List");
                    btnCreate.setActionType(ActionButton.Action.GET);
                    btnCreate.setDisplayPermission(DesignListPermission.class);
                    return btnCreate;
                }

                private ActionButton createImportListArchiveButton()
                {
                    ActionURL urlImport = new ActionURL(ListController.ImportListArchiveAction.class, getContainer());
                    urlImport.addReturnURL(getReturnURL());
                    ActionButton btnImport = new ActionButton(urlImport, "Import List Archive");
                    btnImport.setActionType(ActionButton.Action.GET);
                    btnImport.setDisplayPermission(DesignListPermission.class);
                    return btnImport;
                }

                @Override
                public ActionButton createDeleteButton()
                {
                    ActionURL urlDelete = new ActionURL(ListController.DeleteListDefinitionAction.class, getContainer());
                    urlDelete.addReturnURL(getReturnURL());
                    ActionButton btnDelete = new ActionButton(urlDelete, "Delete");
                    btnDelete.setIconCls("trash");
                    btnDelete.setActionType(ActionButton.Action.POST);
                    btnDelete.isLocked();
                    btnDelete.setDisplayPermission(DeletePermission.class);
                    btnDelete.setRequiresSelection(true, "Are you sure you want to delete the selected row?", "Are you sure you want to delete the selected rows?");
                    return btnDelete;
                }

                private ActionButton createExportArchiveButton()
                {
                    ActionURL urlExport;
                    ActionButton btnExport;

                    if (s.getContainerFilterName() != null && s.getContainerFilterName().equals("CurrentAndSubfolders"))
                    {
                        urlExport = new ActionURL(getReturnURL().toString());
                        btnExport = new ActionButton(urlExport, "Export List Archive");
                        btnExport.setRequiresSelection(true, 1, 0, "You cannot export while viewing subFolders", "You cannot export while viewing subFolders", null);
                    }
                    else
                    {
                        urlExport = new ActionURL(ListController.ExportListArchiveAction.class, getContainer());
                        btnExport = new ActionButton(urlExport, "Export List Archive");
                        btnExport.setRequiresSelection(true);
                    }

                    btnExport.setActionType(ActionButton.Action.POST);
                    btnExport.setDisplayPermission(DesignListPermission.class);
                    return btnExport;
                }

                @Override
                protected void addDetailsAndUpdateColumns(List<DisplayColumn> ret, TableInfo table)
                {
                    if (getContainer().hasPermission(getUser(), DesignListPermission.class))
                    {
                        SimpleDisplayColumn designColumn = new SimpleDisplayColumn()
                        {
                            @Override
                            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                            {
                                Container c = ContainerManager.getForId(ctx.get(FieldKey.fromParts("container")).toString());
                                ActionURL designUrl = new ActionURL(ListController.EditListDefinitionAction.class, c);
                                designUrl.addParameter("listId", ctx.get(FieldKey.fromParts("listId")).toString());
                                out.write(PageFlowUtil.textLink("View Design", designUrl));
                            }
                        };
                        ret.add(designColumn);
                    }

                    if (AuditLogService.get().isViewable())
                    {
                        SimpleDisplayColumn historyColumn = new SimpleDisplayColumn()
                        {
                            @Override
                            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                            {
                                Container c = ContainerManager.getForId(ctx.get(FieldKey.fromParts("container")).toString());
                                ActionURL historyUrl = new ActionURL(ListController.HistoryAction.class, c);
                                historyUrl.addParameter("listId", ctx.get(FieldKey.fromParts("listId")).toString());
                                out.write(PageFlowUtil.textLink("View History", historyUrl));
                            }
                        };
                        ret.add(historyColumn);
                    }
                }
            };
        }
        return super.createView(context, settings, errors);
    }
    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }
}
