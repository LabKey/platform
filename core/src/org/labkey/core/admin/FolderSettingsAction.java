/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.admin;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.PanelButton;
import org.labkey.api.data.RenderContext;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.Group;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.WebPartView;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.ZipFile;
import org.labkey.core.admin.writer.FolderExportContext;
import org.labkey.core.admin.writer.FolderWriter;
import org.labkey.core.query.CoreQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: klum
 * Date: Jan 17, 2011
 * Time: 10:36:02 AM
 */
@RequiresPermissionClass(AdminPermission.class)
@ActionNames("folderSettings, customize")
public class FolderSettingsAction extends FormViewAction<FolderSettingsAction.FolderSettingsForm>
{
    private ActionURL _successURL;

    public void validateCommand(FolderSettingsForm form, Errors errors)
    {
        if (form.isFolderTypeTab())
        {
            boolean fEmpty = true;
            for (String module : form.activeModules)
            {
                if (module != null)
                {
                    fEmpty = false;
                    break;
                }
            }
            if (fEmpty && "None".equals(form.getFolderType()))
            {
                errors.reject(SpringActionController.ERROR_MSG, "Error: Please select at least one module to display.");
            }
        }
        else if (form.isMessagesTab())
        {
            MessageConfigService.ConfigTypeProvider provider = MessageConfigService.getInstance().getConfigType(form.getProvider());

            if (provider != null)
                provider.validateCommand(getViewContext(), errors);
        }
    }

    public ModelAndView getView(FolderSettingsForm form, boolean reshow, BindException errors) throws Exception
    {
        // In export-to-browser case, base action will attempt to reshow the view since we returned null as the success
        // URL; returning null here causes the base action to stop pestering the action.
        if (reshow)
            return null;

        return new FolderSettingsTabStrip(getContainer(), form, errors);
    }

    public boolean handlePost(FolderSettingsForm form, BindException errors) throws Exception
    {
        if (form.isMvIndicatorsTab())
            return handleMvIndicatorsPost(form, errors);
        else if (form.isFolderTypeTab())
            return handleFolderTypePost(form, errors);
        else if (form.isFullTextSearchTab())
            return handleFullTextSearchPost(form, errors);
        else if (form.isExportTab())
            return handleExportPost(form, errors);
        else
            return handleMessagesPost(form, errors);
    }

    private boolean handleMvIndicatorsPost(FolderSettingsForm form, BindException errors) throws SQLException
    {
        if (form.isInheritMvIndicators())
        {
            MvUtil.inheritMvIndicators(getContainer());
            return true;
        }
        else
        {
            // Javascript should have enforced any constraints
            MvUtil.assignMvIndicators(getContainer(), form.getMvIndicators(), form.getMvLabels());
            return true;
        }
    }

    private boolean handleFolderTypePost(FolderSettingsForm form, BindException errors) throws SQLException
    {
        Container c = getContainer();
        if (c.isRoot())
        {
            throw new NotFoundException();
        }

        String[] modules = form.getActiveModules();

        if (modules.length == 0)
        {
            errors.reject(null, "At least one module must be selected");
            return false;
        }

        Set<Module> activeModules = new HashSet<Module>();
        for (String moduleName : modules)
        {
            Module module = ModuleLoader.getInstance().getModule(moduleName);
            if (module != null)
                activeModules.add(module);
        }

        if (null == StringUtils.trimToNull(form.getFolderType()) || FolderType.NONE.getName().equals(form.getFolderType()))
        {
            c.setFolderType(FolderType.NONE, activeModules);
            Module defaultModule = ModuleLoader.getInstance().getModule(form.getDefaultModule());
            c.setDefaultModule(defaultModule);
        }
        else
        {
            FolderType folderType = ModuleLoader.getInstance().getFolderType(form.getFolderType());
            c.setFolderType(folderType, activeModules);
        }

        if (form.isWizard())
        {
            _successURL = PageFlowUtil.urlProvider(SecurityUrls.class).getContainerURL(c);
            _successURL.addParameter("wizard", Boolean.TRUE.toString());
        }
        else
            _successURL = c.getFolderType().getStartURL(c, getUser());

        return true;
    }

    private boolean handleFullTextSearchPost(FolderSettingsForm form, BindException errors) throws SQLException
    {
        Container c = getContainer();
        if (c.isRoot())
        {
            throw new NotFoundException();
        }

        ContainerManager.updateSearchable(c, form.getSearchable(), getUser());
        _successURL = getViewContext().getActionURL();  // Redirect to ourselves -- this forces a reload of the Container object to get the property update

        return true;
    }

    private boolean handleExportPost(FolderSettingsForm form, BindException errors) throws Exception
    {
        Container c = getContainer();
        if (c.isRoot())
        {
            throw new NotFoundException();
        }

        FolderWriter writer = new FolderWriter();
        FolderExportContext ctx = new FolderExportContext(getUser(), getContainer(), PageFlowUtil.set(form.getTypes()), Logger.getLogger(FolderWriter.class));

        switch(form.getLocation())
        {
            case 0:
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                if (root == null || !root.isValid())
                {
                    throw new NotFoundException("No valid pipeline root found");
                }
                File exportDir = root.resolvePath("export");
                writer.write(c, ctx, new FileSystemFile(exportDir));
                _successURL = getViewContext().getActionURL(); // TODO: where should this redirect to?
                break;
            }
            case 1:
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                if (root == null || !root.isValid())
                {
                    throw new NotFoundException("No valid pipeline root found");
                }
                File exportDir = root.resolvePath("export");
                exportDir.mkdir();
                ZipFile zip = new ZipFile(exportDir, FileUtil.makeFileNameWithTimestamp(c.getName(), "folder.zip"));
                writer.write(c, ctx, zip);
                zip.close();
                _successURL = getViewContext().getActionURL(); // TODO: where should this redirect to?
                break;
            }
            case 2:
            {
                ZipFile zip = new ZipFile(getViewContext().getResponse(), FileUtil.makeFileNameWithTimestamp(c.getName(), "folder.zip"));
                writer.write(c, ctx, zip);
                zip.close();
                break;
            }
        }

        return true;
    }

    private boolean handleMessagesPost(FolderSettingsForm form, BindException errors) throws Exception
    {
        MessageConfigService.ConfigTypeProvider provider = MessageConfigService.getInstance().getConfigType(form.getProvider());

        if (provider != null)
        {
            _successURL = getViewContext().getActionURL();
            return provider.handlePost(getViewContext(), errors);
        }
        errors.reject(SpringActionController.ERROR_MSG, "Unable to find the selected config provider");
        return false;
    }

    public ActionURL getSuccessURL(FolderSettingsForm form)
    {
        return _successURL;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        Container c = getViewContext().getContainer();

        if (c.isRoot())
            return AdminController.appendAdminNavTrail(root, "Admin Console", AdminController.ShowAdminAction.class, getContainer());

        root.addChild("Folder Settings: " + getContainer().getPath());
        return root;
    }

    private Container getContainer()
    {
        return getViewContext().getContainer();
    }

    private User getUser()
    {
        return getViewContext().getUser();
    }

    public static class FolderSettingsForm
    {
        // folder type settings
        private String[] activeModules = new String[ModuleLoader.getInstance().getModules().size()];
        private String defaultModule;
        private String folderType;
        private boolean wizard;
        private String tabId;

        // missing value settings
        private boolean inheritMvIndicators;
        private String[] mvIndicators;
        private String[] mvLabels;

        // full-text search settings
        private boolean searchable;
        private String _provider;

        // export folder settings
        private String[] _types;
        private int _location;

        public String[] getActiveModules()
        {
            return activeModules;
        }

        public void setActiveModules(String[] activeModules)
        {
            this.activeModules = activeModules;
        }

        public String getDefaultModule()
        {
            return defaultModule;
        }

        public void setDefaultModule(String defaultModule)
        {
            this.defaultModule = defaultModule;
        }

        public String getFolderType()
        {
            return folderType;
        }

        public void setFolderType(String folderType)
        {
            this.folderType = folderType;
        }

        public boolean isWizard()
        {
            return wizard;
        }

        public void setWizard(boolean wizard)
        {
            this.wizard = wizard;
        }

        public void setTabId(String tabId)
        {
            this.tabId = tabId;
        }

        public String getTabId()
        {
            return tabId;
        }

        public boolean isFolderTypeTab()
        {
            return "folderType".equals(getTabId());
        }

        public boolean isMvIndicatorsTab()
        {
            return "mvIndicators".equals(getTabId());
        }

        public boolean isFullTextSearchTab()
        {
            return "fullTextSearch".equals(getTabId());
        }

        public boolean isMessagesTab()
        {
            return "messages".equals(getTabId());
        }

        public boolean isExportTab()
        {
            return "export".equals(getTabId());
        }

        public boolean isInheritMvIndicators()
        {
            return inheritMvIndicators;
        }

        public void setInheritMvIndicators(boolean inheritMvIndicators)
        {
            this.inheritMvIndicators = inheritMvIndicators;
        }

        public String[] getMvIndicators()
        {
            return mvIndicators;
        }

        public void setMvIndicators(String[] mvIndicators)
        {
            this.mvIndicators = mvIndicators;
        }

        public String[] getMvLabels()
        {
            return mvLabels;
        }

        public void setMvLabels(String[] mvLabels)
        {
            this.mvLabels = mvLabels;
        }

        public boolean getSearchable()
        {
            return searchable;
        }

        public void setSearchable(boolean searchable)
        {
            this.searchable = searchable;
        }

        public String getProvider()
        {
            return _provider;
        }

        public void setProvider(String provider)
        {
            _provider = provider;
        }

        public String[] getTypes()
        {
            return _types;
        }

        public void setTypes(String[] types)
        {
            _types = types;
        }

        public int getLocation()
        {
            return _location;
        }

        public void setLocation(int location)
        {
            _location = location;
        }
    }


    private static class FolderSettingsTabStrip extends TabStripView
    {
        private final Container _container;
        private FolderSettingsForm _form;
        private BindException _errors;

        private FolderSettingsTabStrip(Container c, FolderSettingsForm form, BindException errors)
        {
            _container = c;
            _form = form;
            _errors = errors;
        }

        public List<NavTree> getTabList()
        {
            ActionURL url = new AdminController.AdminUrlsImpl().getFolderSettingsURL(getViewContext().getContainer());
            List<NavTree> tabs = new ArrayList<NavTree>(2);

            if (!_container.isRoot())
                tabs.add(new TabInfo("Folder Type", "folderType", url));
            tabs.add(new TabInfo("Missing Value Indicators", "mvIndicators", url));
            if (!_container.isRoot())
            {
                tabs.add(new TabInfo("Full-Text Search", "fullTextSearch", url));
                tabs.add(new TabInfo("Email Notifications", "messages", url));
                tabs.add(new TabInfo("Export Folder", "export", url));
            }
            return tabs;
        }

        public HttpView getTabView(String tabId) throws Exception
        {
            if ("folderType".equals(tabId))
            {
                assert !_container.isRoot() : "No folder type settings for the root folder";
                return new JspView<FolderSettingsForm>("/org/labkey/core/admin/folderType.jsp", _form, _errors);
            }
            else if ("mvIndicators".equals(tabId))
            {
                return new JspView<FolderSettingsForm>("/org/labkey/core/admin/mvIndicators.jsp", _form, _errors);
            }
            else if ("fullTextSearch".equals(tabId))
            {
                return new JspView<FolderSettingsForm>("/org/labkey/core/admin/fullTextSearch.jsp", _form, _errors);
            }
            else if ("messages".equals(tabId))
            {
                return getMessageTabView();
            }
            else if ("export".equals(tabId))
            {
                return new JspView<FolderSettingsForm>("/org/labkey/core/admin/exportFolder.jsp", _form, _errors);
            }
            else
            {
                throw new NotFoundException("Unknown tab id");
            }
        }

        private static final String DATA_REGION_NAME = "Users";
        private int realRowIndex = 0;

        private HttpView getMessageTabView() throws Exception
        {
            final String key = DataRegionSelection.getSelectionKey("core", CoreQuerySchema.USERS_MSG_SETTINGS_TABLE_NAME, null, DATA_REGION_NAME);
            DataRegionSelection.clearAll(getViewContext(), key);

            QuerySettings settings = new QuerySettings(getViewContext(), DATA_REGION_NAME, CoreQuerySchema.USERS_MSG_SETTINGS_TABLE_NAME);
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(true);
            settings.getBaseSort().insertSortColumn("DisplayName");

            QueryView queryView = new QueryView(new CoreQuerySchema(getViewContext().getUser(), getViewContext().getContainer()), settings, _errors)
            {
                @Override
                public List<DisplayColumn> getDisplayColumns()
                {
                    List<DisplayColumn> columns = new ArrayList<DisplayColumn>();
                    SecurityPolicy policy = getContainer().getPolicy();
                    Set<String> assignmentSet = new HashSet<String>();

                    assignmentSet.add(SecurityManager.getGroup(Group.groupAdministrators).getName());
                    assignmentSet.add(SecurityManager.getGroup(Group.groupDevelopers).getName());
                            
                    for (RoleAssignment assignment : policy.getAssignments())
                    {
                        Group g = SecurityManager.getGroup(assignment.getUserId());
                        if (g != null)
                            assignmentSet.add(g.getName());
                    }

                    for (DisplayColumn col : super.getDisplayColumns())
                    {
                        if (col.getName().equalsIgnoreCase("Groups"))
                            columns.add(new FolderGroupColumn(assignmentSet, col.getColumnInfo()));
                        else
                            columns.add(col);
                    }
                    return columns;
                }

                @Override
                protected void populateButtonBar(DataView dataView, ButtonBar bar)
                {
                    try {
                        // add the provider configuration views to the admin panel button
                        PanelButton adminButton = new PanelButton("Update Settings", getDataRegionName());
                        PanelConfig config = new PanelConfig(getViewContext().getActionURL().clone(), key);
                        for (MessageConfigService.ConfigTypeProvider provider : MessageConfigService.getInstance().getConfigTypes())
                        {
                            VBox view = new VBox();

                            view.addView(new HtmlView("<input type=\"hidden\" name=\"provider\" value=\"" + provider.getType() + "\">"));
                            view.addView(provider.createConfigPanel(getViewContext(), config));

                            adminButton.addSubPanel(provider.getName(), view);
                        }
                        bar.add(adminButton);

                        super.populateButtonBar(dataView, bar);
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            };
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            queryView.setShowDetailsColumn(false);
            queryView.setShowRecordSelectors(true);
            queryView.setFrame(WebPartView.FrameType.NONE);
            queryView.disableContainerFilterSelection();
            queryView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);

            VBox view = new VBox();

            view.addView(new JspView<Object>("/org/labkey/core/admin/view/folderSettingsHeader.jsp", Object.class, _errors));
            view.addView(queryView);

            return view;
        }
    }

    private static class FolderGroupColumn extends DataColumn
    {
        Set<String> _assignmentSet;
        public FolderGroupColumn(Set<String> assignmentSet, ColumnInfo col)
        {
            super(col);
            _assignmentSet = assignmentSet;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String value = (String)ctx.get(getBoundColumn().getDisplayField().getFieldKey());

            if (value != null)
            {
                StringBuilder sb = new StringBuilder();
                String delim = "";

                for (String name : value.split(","))
                {
                    if (_assignmentSet.contains(name))
                    {
                        sb.append(delim);
                        sb.append(name);
                        delim = ",<br>";
                    }
                }
                out.write(sb.toString());
            }
            //super.renderGridCellContents(ctx, out);  
        }
    }

    private static class PanelConfig implements MessageConfigService.PanelInfo
    {
        private ActionURL _returnUrl;
        private String _dataRegionSelectionKey;

        public PanelConfig(ActionURL returnUrl, String selectionKey)
        {
            _returnUrl = returnUrl;
            _dataRegionSelectionKey = selectionKey;
        }

        @Override
        public ActionURL getReturnUrl()
        {
            return _returnUrl;
        }

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }
    }
}
