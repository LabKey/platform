/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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
package org.labkey.core;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.ClientAPIAuditViewFactory;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.FastaDataLoader;
import org.labkey.api.reader.HTMLDataLoader;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.core.query.UsersDomainKind;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.etl.CachingDataIterator;
import org.labkey.api.etl.RemoveDuplicatesDataIterator;
import org.labkey.api.etl.ResultSetDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.FirstRequestHandler;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeResourceLoader;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleDependencySorter;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.module.ModuleResourceLoader;
import org.labkey.api.module.ResourceFinder;
import org.labkey.api.module.SpringModule;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.script.RhinoService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.NestedGroupsTest;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.ReplacedRunFilter;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.ContactWebPart;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.menu.ContainerMenu;
import org.labkey.api.view.menu.ProjectsMenu;
import org.labkey.api.webdav.FileSystemAuditViewFactory;
import org.labkey.api.webdav.ModuleStaticResolverImpl;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.core.admin.ActionsTsvWriter;
import org.labkey.core.admin.AdminController;
import org.labkey.core.admin.CustomizeMenuForm;
import org.labkey.core.admin.importer.FolderTypeImporterFactory;
import org.labkey.core.admin.importer.PageImporterFactory;
import org.labkey.core.admin.importer.SearchSettingsImporterFactory;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.admin.writer.FolderSerializationRegistryImpl;
import org.labkey.core.admin.writer.FolderTypeWriterFactory;
import org.labkey.core.admin.writer.PageWriterFactory;
import org.labkey.core.admin.writer.SearchSettingsWriterFactory;
import org.labkey.core.analytics.AnalyticsController;
import org.labkey.core.analytics.AnalyticsServiceImpl;
import org.labkey.core.attachment.AttachmentServiceImpl;
import org.labkey.core.dialect.PostgreSqlDialectFactory;
import org.labkey.core.junit.JunitController;
import org.labkey.core.login.DbLoginAuthenticationProvider;
import org.labkey.core.login.LoginController;
import org.labkey.core.query.AttachmentAuditViewFactory;
import org.labkey.core.query.ContainerAuditViewFactory;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.query.UserAuditViewFactory;
import org.labkey.core.reader.DataLoaderServiceImpl;
import org.labkey.core.security.SecurityController;
import org.labkey.core.test.TestController;
import org.labkey.core.thumbnail.ThumbnailServiceImpl;
import org.labkey.core.user.UserController;
import org.labkey.core.webdav.DavController;
import org.labkey.core.workbook.WorkbookFolderType;
import org.labkey.core.workbook.WorkbookQueryView;
import org.labkey.core.workbook.WorkbookSearchView;

import javax.servlet.ServletContextEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 25, 2005
 * Time: 2:54:30 PM
 */
public class CoreModule extends SpringModule implements SearchService.DocumentProvider
{
    public static final String EXPERIMENTAL_JSDOC = "experimental-jsdoc";

    @Override
    public String getName()
    {
        return CORE_MODULE_NAME;
    }

    @Override
    public double getVersion()
    {
        return 12.23;
    }

    @Override
    public int compareTo(Module m)
    {
        //core module always sorts first
        return (m instanceof CoreModule) ? 0 : -1;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void init()
    {
        ServiceRegistry.get().registerService(ContainerService.class, ContainerManager.getContainerService());
        ServiceRegistry.get().registerService(FolderSerializationRegistry.class, FolderSerializationRegistryImpl.get());
        SqlDialectManager.register(new PostgreSqlDialectFactory());

        addController("admin", AdminController.class);
        addController("admin-sql", SqlScriptController.class);
        addController("security", SecurityController.class);
        addController("user", UserController.class);
        addController("login", LoginController.class);
        addController("junit", JunitController.class);
        addController("core", CoreController.class);
        addController("test", TestController.class);
        addController("analytics", AnalyticsController.class);

        AuthenticationManager.registerProvider(new DbLoginAuthenticationProvider(), Priority.Low);
        AttachmentService.register(new AttachmentServiceImpl());
        AnalyticsServiceImpl.register();
        FirstRequestHandler.addFirstRequestListener(new CoreFirstRequestHandler());
        RhinoService.register();
        ServiceRegistry.get().registerService(ThumbnailService.class, new ThumbnailServiceImpl());
        ServiceRegistry.get().registerService(DataLoaderService.I.class, new DataLoaderServiceImpl());

        ModuleStaticResolverImpl.get();

        DefaultSchema.registerProvider("core", new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new CoreQuerySchema(schema.getUser(), schema.getContainer());
            }
        });

        String projectRoot = AppProps.getInstance().getProjectRoot();
        if (projectRoot != null)
        {
            File root = new File(projectRoot);
            if (root.isDirectory())
            {
                ResourceFinder api = new ResourceFinder("API", root + "/server/api", root + "/build/modules/api");
                ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", api);
                ResourceFinder internal = new ResourceFinder("Internal", root + "/server/internal", root + "/build/modules/internal");
                ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", internal);
            }
        }

        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_JSDOC, "Javascript Documentation", "Displays LabKey javascript API's from the Developer Links menu.", false);
    }

    @Override
    public Set<ModuleResourceLoader> getResourceLoaders()
    {
        return Collections.<ModuleResourceLoader>singleton(new FolderTypeResourceLoader());
    }

    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(
                new AlwaysAvailableWebPartFactory("Contacts")
                {
                    public WebPartView getWebPartView(ViewContext ctx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                    {
                        return new ContactWebPart();
                    }
                },
                new AlwaysAvailableWebPartFactory("Folders", WebPartFactory.LOCATION_MENUBAR, false, false) {
                    public WebPartView getWebPartView(final ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        final ProjectsMenu projectsMenu = new ProjectsMenu(portalCtx);
                        projectsMenu.setCollapsed(false);
                        WebPartView v = new WebPartView("Folders") {
                            @Override
                            protected void renderView(Object model, PrintWriter out) throws Exception
                            {
                                out.write("<table style='width:50'><tr><td style='vertical-align:top;padding:4px'>");
                                include(new ContainerMenu(portalCtx));
                                out.write("</td><td style='vertical-align:top;padding:4px'>");
                                include(projectsMenu);
                                out.write("</td></tr></table>");
                            }
                        };
                        v.setFrame(WebPartView.FrameType.PORTAL);
                        return v;
                    }
                },
                new BaseWebPartFactory("Workbooks")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        WorkbookQueryView wbqview = new WorkbookQueryView(portalCtx, new CoreQuerySchema(portalCtx.getUser(), portalCtx.getContainer()));
                        VBox box = new VBox(new WorkbookSearchView(wbqview), wbqview);
                        box.setFrame(WebPartView.FrameType.PORTAL);
                        box.setTitle("Workbooks");
                        return box;
                    }

                    @Override
                    public boolean isAvailable(Container c, String location)
                    {
                        return !c.isWorkbook() && location.equalsIgnoreCase(HttpView.BODY);
                    }
                },
                new BaseWebPartFactory("Workbook Description")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        JspView view = new JspView("/org/labkey/core/workbook/workbookDescription.jsp");
                        view.setTitle("Workbook Description");
                        view.setFrame(WebPartView.FrameType.NONE);
                        return view;
                    }

                    @Override
                    public boolean isAvailable(Container c, String location)
                    {
                        return false;
                    }
                },
                new AlwaysAvailableWebPartFactory("Projects")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        JspView<Portal.WebPart> view = new JspView<Portal.WebPart>("/org/labkey/core/project/projects.jsp", webPart);

                        String title = webPart.getPropertyMap().containsKey("title") ? webPart.getPropertyMap().get("title") : "Projects";
                        view.setTitle(title);

                        if (portalCtx.hasPermission(AdminPermission.class))
                        {
                            NavTree customize = new NavTree("");
                            customize.setScript("customizeProjectWebpart(" + webPart.getRowId() + ", \'" + webPart.getPageId() + "\', " + webPart.getIndex() + ");");
                            customize.setDisplay("Large Icons");
                            view.setCustomize(customize);
                        }
                        return view;
                    }
                },
                new AlwaysAvailableWebPartFactory("Custom Menu", WebPartFactory.LOCATION_MENUBAR, true, false) {
                    public WebPartView getWebPartView(final ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        final CustomizeMenuForm form = AdminController.getCustomizeMenuForm(webPart);
                        String title = "My Menu";
                        if (form.getTitle() != null && !form.getTitle().equals(""))
                            title = form.getTitle();

                        WebPartView view = null;
                        if (form.isChoiceListQuery())
                        {
                            view = createMenuQueryView(portalCtx, title, form);
                        }
                        else
                        {
                            view = createMenuFolderView(portalCtx, title, form);
                        }
                        view.setFrame(WebPartView.FrameType.PORTAL);
                        return view;
                    }

                    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
                    {
                        CustomizeMenuForm form = AdminController.getCustomizeMenuForm(webPart);
                        JspView<CustomizeMenuForm> view = new JspView<CustomizeMenuForm>("/org/labkey/core/admin/customizeMenu.jsp", form);
                        view.setTitle(form.getTitle());
                        view.setFrame(WebPartView.FrameType.PORTAL);
                        return view;
                    }
                }
        ));
    }

    private static final int MAX_PER_COLUMN = 15;

    private WebPartView createMenuQueryView(ViewContext context, String title, final CustomizeMenuForm form)
    {
        if (null != StringUtils.trimToNull(form.getFolderName()))
        {
            Container container = ContainerManager.getForPath(form.getFolderName());
            context = new ViewContext(context);
            context.setContainer(container);        // Need ViewComntext with proper container
        }

        final ViewContext actualContext = context;
        String schemaName = StringUtils.trimToNull(form.getSchemaName());
        if (null != schemaName)
        {
            UserSchema schema = QueryService.get().getUserSchema(actualContext.getUser(), actualContext.getContainer(), schemaName);
            if (null == schema)
                throw new IllegalArgumentException("Schema '" + schemaName + "' could not be found.");

            QuerySettings settings = new QuerySettings(actualContext, null, form.getQueryName());

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);

            settings.setShowRows(ShowRows.PAGINATED);
            settings.setMaxRows(100);
            settings.setViewName(form.getViewName());

            QueryView view = new QueryView(schema, settings, null)
            {
                @Override
                protected void renderDataRegion(PrintWriter out) throws Exception
                {
                    boolean seenAtLeastOne = false;
                    out.write("<table style='width:50'>");
                    TableInfo tableInfo = getTable();
                    if (null != tableInfo)
                    {
                        ColumnInfo columnInfo = tableInfo.getColumn(form.getColumnName());
                        String urlBase = form.getUrl();
                        if (urlBase != null && !urlBase.contentEquals(""))
                            columnInfo.setURL(StringExpressionFactory.createURL(form.getUrl()));
                        DataColumn dataColumn = new DataColumn(columnInfo, false)
                        {
                            @Override           // so we can use DetailsURL if no other URL can be used
                            protected String renderURLorValueURL(RenderContext renderContext)
                            {
                                String url = super.renderURLorValueURL(renderContext);
                                if (null == url)
                                {
                                    StringExpression expr = getColumnInfo().getParentTable().getDetailsURL(null, renderContext.getContainer());
                                    if (null != expr)
                                        url = expr.eval(renderContext);
                                }
                                return url;
                            }
                        };

                        RenderContext renderContext = new RenderContext(actualContext);
                        Results results = getResults(ShowRows.PAGINATED);
                        try
                        {
                            renderContext.setResults(results);
                            ResultSet rs = results.getResultSet();
                            if (null != rs)
                            {
                                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

                                // To do columns, we'll write each cell into a StringBuilder, then we have the count and can go from there
                                ArrayList<StringBuilder> cellStrings = new ArrayList<StringBuilder>();
                                while (rs.next())
                                {
                                    StringBuilder stringBuilder = new StringBuilder();
                                    cellStrings.add(stringBuilder);
                                    StringBuilderWriter writer = new StringBuilderWriter(stringBuilder);
                                    renderContext.setRow(factory.getRowMap(rs));
                                    dataColumn.renderGridCellContents(renderContext, writer);
                                    seenAtLeastOne = true;
                                }

                                writeCells(cellStrings, out);
                            }

                        }
                        finally
                        {
                            ResultSetUtil.close(results);
                        }
                    }
                    if (!seenAtLeastOne)
                        out.write("<tr><td>No query results.</td></tr>");
                    out.write("</table>");
                }
            };
            view.setTitle(title);

            view.setShowBorders(false);
            view.setShowConfiguredButtons(false);
            view.setShowDeleteButton(false);
            view.setShowDetailsColumn(false);
            view.setShowExportButtons(false);
            view.setShowFilterDescription(false);
            view.setShowImportDataButton(false);
            view.setShowInsertNewButton(false);
            view.setShowPaginationCount(false);
            view.setAllowExportExternalQuery(false);
            view.setShowSurroundingBorder(false);
            view.setShowPaginationCount(false);
            view.setShowPagination(false);
            return view;
        }
        else
        {
            WebPartView view = new WebPartView(title) {
                @Override
                protected void renderView(Object model, PrintWriter out) throws Exception
                {
                    out.write("<table style='width:50'><tr><td style='vertical-align:top;padding:4px'>");
                    out.write("No schema or query selected.");
                    out.write("</td></tr></table>");
                }
            };
            return view;
        }
    }

    private WebPartView createMenuFolderView(final ViewContext context, String title, final CustomizeMenuForm form)
    {
        // If rootPath is "", then use current context's container
        String rootPath = form.getRootFolder();
        Container rootFolder = (0 == rootPath.compareTo("")) ? context.getContainer() : ContainerManager.getForPath(rootPath);
        final User user = context.getUser();
        List<Container> containersTemp = null;
        if (form.isIncludeAllDescendants())
        {
            containersTemp = ContainerManager.getAllChildren(rootFolder, user, ReadPermission.class, false);    // no workbooks
            containersTemp.remove(rootFolder);      // getAllChildren adds root, which we don't want
        }
        else
        {
            containersTemp = ContainerManager.getChildren(rootFolder, user, ReadPermission.class, false);   // no workbooks
//            containersTemp.add(rootFolder);      // Don't add root folder; later we may add a checkbox to allow it to be added, if so, check root's permissions
        }

        if (!context.getContainer().getPolicy().hasPermission(user, AdminPermission.class))
        {
            // If user doesn't have Admin permission, don't show "_" containers
            List<Container> adjustedContainers = new ArrayList<Container>();
            for (Container container : containersTemp)
            {
                if (!container.getName().startsWith("_"))
                    adjustedContainers.add(container);
            }
            containersTemp = adjustedContainers;
        }

        Collections.sort(containersTemp, new Comparator<Container>()
        {
            @Override
            public int compare(Container container1, Container container2)
            {
                return container1.getName().compareToIgnoreCase(container2.getName());
            }
        });

        final Collection<Container> containers = containersTemp;

        WebPartView view = new WebPartView(title) {
            @Override
            protected void renderView(Object model, PrintWriter out) throws Exception
            {
                final String filterFolderName = form.getFolderTypes();
                StringExpression expr = null;
                String urlBase = form.getUrl();
                if (null != StringUtils.trimToNull(urlBase))
                {
                    expr = StringExpressionFactory.createURL(form.getUrl());
                }

                boolean seenAtLeastOne = false;
                out.write("<table style='width:50'>");
                ArrayList<StringBuilder> cells = new ArrayList<StringBuilder>();
                for (Container container : containers)
                {
                    if (null == StringUtils.trimToNull(filterFolderName) ||
                            "[all]".equals(filterFolderName) ||
                            container.getFolderType().getName().equals(filterFolderName))
                    {
                        ActionURL actionURL = null;
                        if (null != expr)
                        {
                            actionURL = new ActionURL(expr.getSource());
                            actionURL.setContainer(container);
                        }
                        else
                        {
                            actionURL = container.getStartURL(user);
                        }

                        String uri = actionURL.getLocalURIString();
                        if (null != StringUtils.trimToNull(uri))
                        {
                            String name = null != StringUtils.trimToNull(container.getName()) ? container.getName() : "[root]";
                            StringBuilder cell = new StringBuilder("<a href=\"" + uri + "\">" + name + "</a>");
                            cells.add(cell);
                            seenAtLeastOne = true;
                        }
                    }
                }

                writeCells(cells, out);

                if (!seenAtLeastOne)
                    out.write("<tr><td style='vertical-align:top;padding:4px'>No folders selected.</td></tr>");
                out.write("</table>");
            }
        };
        return view;
    }

    private void writeCells(ArrayList<StringBuilder> cells, PrintWriter out)
    {
        int countContainers = cells.size();
        int countColumns = (int)Math.ceil((double)countContainers/MAX_PER_COLUMN);
        int countRows =  (int)Math.ceil((double)countContainers/countColumns);

        for (int i = 0; i < countRows; i += 1)
        {
            out.write("<tr>");

            for (int k = 0; k < countColumns; k += 1)
            {
                int index = k * countRows + i;
                if (index < cells.size())
                {
                    StringBuilder cell = cells.get(index);
                    out.write("<td style='vertical-align:top;padding:0px 4px'>");
                    out.write(cell.toString());
                    out.write("</td>");
                }
            }
            out.write("</tr>");
        }
    }

    @Override
    public void beforeUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
        {
            CoreSchema core = CoreSchema.getInstance();

            core.getSqlDialect().prepareNewDatabase(core.getSchema());
        }

        super.beforeUpdate(moduleContext);
    }


    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        super.afterUpdate(moduleContext);

        if (moduleContext.isNewInstall())
            bootstrap();

        try
        {
            // Increment on every core module upgrade to defeat browser caching of static resources.
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    private void bootstrap()
    {
        // Create the initial groups
        GroupManager.bootstrapGroup(Group.groupAdministrators, "Administrators");
        GroupManager.bootstrapGroup(Group.groupUsers, "Users");
        GroupManager.bootstrapGroup(Group.groupGuests, "Guests");
        GroupManager.bootstrapGroup(Group.groupDevelopers, "Developers", PrincipalType.ROLE);

        // Other containers inherit permissions from root; admins get all permissions, users & guests none
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        Role siteAdminRole = RoleManager.getRole(SiteAdminRole.class);
        Role readerRole = RoleManager.getRole(ReaderRole.class);

        ContainerManager.bootstrapContainer("/", siteAdminRole, noPermsRole, noPermsRole);

        // Users & guests can read from /home
        ContainerManager.bootstrapContainer(ContainerManager.HOME_PROJECT_PATH, siteAdminRole, readerRole, readerRole);

        // Only users can read from /Shared
        ContainerManager.bootstrapContainer(ContainerManager.SHARED_CONTAINER_PATH, siteAdminRole, readerRole, noPermsRole);

        try
        {
            // Need to insert standard MV indicators for the root -- okay to call getRoot() since we just created it.
            Container rootContainer = ContainerManager.getRoot();
            String rootContainerId = rootContainer.getId();
            TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();

            for (Map.Entry<String,String> qcEntry : MvUtil.getDefaultMvIndicators().entrySet())
            {
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("Container", rootContainerId);
                params.put("MvIndicator", qcEntry.getKey());
                params.put("Label", qcEntry.getValue());

                Table.insert(null, mvTable, params);
            }
        }
        catch (Throwable t)
        {
            ExceptionUtil.logExceptionToMothership(null, t);
        }

    }



    @Override
    public CoreUpgradeCode getUpgradeCode()
    {
        return new CoreUpgradeCode();
    }


    @Override
    public void destroy()
    {
        super.destroy();
        UsageReportingLevel.cancelUpgradeCheck();
    }


    @Override
    public void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        // Any containers in the cache have bogus folder types since they aren't registered until startup().  See #10310
        ContainerManager.clearCache();

        // This listener deletes all properties; make sure it executes after most of the other listeners
        ContainerManager.addContainerListener(new CoreContainerListener(), ContainerManager.ContainerListener.Order.Last);
        SecurityManager.init();
        ModuleLoader.getInstance().registerFolderType(this, FolderType.NONE);
        AppProps.getInstance().getUsageReportingLevel().scheduleUpgradeCheck();
        SystemMaintenance.setTimer();

        AuditLogService.get().addAuditViewFactory(UserAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(GroupAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(AttachmentAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ContainerAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(FileSystemAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ClientAPIAuditViewFactory.getInstance());

        TempTableTracker.init();
        ContextListener.addShutdownListener(TempTableTracker.getShutdownListener());
        ContextListener.addShutdownListener(DavController.getShutdownListener());

        // Export action stats on graceful shutdown
        ContextListener.addShutdownListener(new ShutdownListener() {
            public void shutdownPre(ServletContextEvent servletContextEvent)
            {
            }

            public void shutdownStarted(ServletContextEvent servletContextEvent)
            {
                Logger logger = Logger.getLogger(ActionsTsvWriter.class);

                if (null != logger)
                {
                    Appender appender = logger.getAppender("ACTION_STATS");

                    if (null != appender && appender instanceof RollingFileAppender)
                        ((RollingFileAppender)appender).rollOver();
                    else
                        Logger.getLogger(CoreModule.class).warn("Could not rollover the action stats tsv file--there was no appender named ACTION_STATS, or it is not a RollingFileAppender.");

                    TSVWriter writer = new ActionsTsvWriter();
                    StringBuilder buf = new StringBuilder();

                    try
                    {
                        writer.write(buf);
                    }
                    catch (IOException e)
                    {
                        Logger.getLogger(CoreModule.class).error("Exception exporting action stats", e);
                    }

                    logger.info(buf.toString());
                }
            }
        });

        AdminController.registerAdminConsoleLinks();
        AnalyticsController.registerAdminConsoleLinks();
        UserController.registerAdminConsoleLinks();

        WebdavService.get().setResolver(WebdavResolverImpl.get());
        ModuleLoader.getInstance().registerFolderType(this, new WorkbookFolderType());

        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
        {
            ss.addDocumentProvider(this);
        }

        SecurityManager.addViewFactory(new SecurityController.GroupDiagramViewFactory());

        FolderSerializationRegistry fsr = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != fsr)
        {
            fsr.addFactories(new FolderTypeWriterFactory(), new FolderTypeImporterFactory());
            fsr.addFactories(new SearchSettingsWriterFactory(), new SearchSettingsImporterFactory());
            fsr.addFactories(new PageWriterFactory(), new PageImporterFactory());
        }

        // Register the default DataLoaders.
        // The DataLoaderFactories also register a SearchService.DocumentParser so this should be done after SearchService is available.
        DataLoaderService.I dls = DataLoaderService.get();
        if (dls != null)
        {
            dls.registerFactory(new ExcelLoader.Factory());
            dls.registerFactory(new TabLoader.TsvFactory());
            dls.registerFactory(new TabLoader.CsvFactory());
            dls.registerFactory(new HTMLDataLoader.Factory());
            dls.registerFactory(new JSONDataLoader.Factory());
            dls.registerFactory(new FastaDataLoader.Factory());
        }

        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_CONTAINER_RELATIVE_URL,
                "Container Relative URL",
                "Use container relative URLs of the form /labkey/container/controller-action instead of the current /labkey/controller/container/action URLs.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP,
                "Client-side Exception Logging",
                "Report unhandled JavaScript exceptions to mothership.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_RSERVE_REPORTING,
                "Rserve Reports",
                "Use an R Server for R script evaluation instead of running R from a command shell.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_EMAIL_PERMISSION,
                "Require permission to view email addresses",
                "Require explicit permission for non-admins to view users' email addresses.",
                false);

        PropertyService.get().registerDomainKind(new UsersDomainKind());
    }

    @Override
    public String getTabName(ViewContext context)
    {
        return "Admin";
    }


    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        if (user == null)
        {
            return AppProps.getInstance().getHomePageActionURL();
        }
        else if (c != null && "/".equals(c.getPath()) && user.isAdministrator())
        {
            return PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
        }
        else if (c != null && c.hasPermission(user, AdminPermission.class))
        {
            return PageFlowUtil.urlProvider(SecurityUrls.class).getProjectURL(c);
        }
        else
        {
            return PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(c, user.getUserId(), AppProps.getInstance().getHomePageActionURL());
        }
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_NEVER;
    }


    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        @SuppressWarnings({"unchecked"})
        Set<Class> testClasses = new HashSet<Class>(Arrays.asList(
                Table.TestCase.class,
                Table.DataIteratorTestCase.class,
                DbSchema.TestCase.class,
                TableViewFormTestCase.class,
                ActionURL.TestCase.class,
                SecurityManager.TestCase.class,
                PropertyManager.TestCase.class,
                ContainerManager.TestCase.class,
                TabLoader.TabLoaderTestCase.class,
                MapLoader.MapLoaderTestCase.class,
                GroupManager.TestCase.class,
                SecurityController.TestCase.class,
                AttachmentServiceImpl.TestCase.class,
                WebdavResolverImpl.TestCase.class,
                MimeMap.TestCase.class,
                ModuleStaticResolverImpl.TestCase.class,
                StorageProvisioner.TestCase.class,
                RhinoService.TestCase.class,
                SimpleTranslator.TranslateTestCase.class,
                ResultSetDataIterator.TestCase.class,
                ExceptionUtil.TestCase.class,
                ViewCategoryManager.TestCase.class,
                TableSelectorTestCase.class,
                NestedGroupsTest.class,
                CoreModule.TestCase.class,
                ContainerDisplayColumn.TestCase.class,
                SimpleFilter.InClauseTestCase.class
                //,RateLimiter.TestCase.class
        ));

        testClasses.addAll(SqlDialectManager.getAllJUnitTests());

        return testClasses;
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return new HashSet<Class>(Arrays.asList(
                DateUtil.TestCase.class,
                TSVWriter.TestCase.class,
                ExcelLoader.ExcelLoaderTestCase.class,
                ExcelFactory.ExcelFactoryTestCase.class,
                ModuleDependencySorter.TestCase.class,
                DateUtil.TestCase.class,
                DatabaseCache.TestCase.class,
                PasswordExpiration.TestCase.class,
                BooleanFormat.TestCase.class,
                XMLWriterTest.TestCase.class,
                FileUtil.TestCase.class,
                FileType.TestCase.class,
                MemTracker.TestCase.class,
                StringExpressionFactory.TestCase.class,
                Path.TestCase.class,
                Lsid.TestCase.class,
                HString.TestCase.class,
                PageFlowUtil.TestCase.class,
                ResultSetUtil.TestCase.class,
                ArrayListMap.TestCase.class,
                DbScope.DialectTestCase.class,
                ValidEmail.TestCase.class,
                RemoveDuplicatesDataIterator.DeDuplicateTestCase.class,
                CachingDataIterator.ScrollTestCase.class,
                StringUtilsLabKey.TestCase.class,
                Compress.TestCase.class,
                ExtUtil.TestCase.class,
                JsonTest.class,
                ExtUtil.TestCase.class,
                ReplacedRunFilter.TestCase.class
        ));
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set
        (
            CoreSchema.getInstance().getSchema(),       // core
            Portal.getSchema(),                         // portal
            PropertyManager.getSchema(),                // prop
            TestSchema.getInstance().getSchema()        // test
        );
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set
            (
                CoreSchema.getInstance().getSchemaName(),       // core
                Portal.getSchemaName(),                         // portal
                PropertyManager.getSchemaName(),                // prop
                TestSchema.getInstance().getSchemaName()        // test
            );
    }

    @Override
    public List<String> getAttributions()
    {
        return Arrays.asList(
            "<a href=\"http://www.apache.org\" target=\"top\"><img src=\"http://www.apache.org/images/asf_logo.gif\" alt=\"Apache\" width=\"185\" height=\"50\"></a>",
            "<a href=\"http://www.springframework.org\" target=\"top\"><img src=\"http://static.springframework.org/images/spring21.png\" alt=\"Spring\" width=\"100\" height=\"48\"></a>"
        );
    }

    @NotNull
    @Override
    public List<File> getStaticFileDirectories()
    {
        List<File> dirs = super.getStaticFileDirectories();
        if (AppProps.getInstance().isDevMode())
        {
            if (null != getSourcePath())
            {
                dirs.add(0, new File(getSourcePath(),"../../internal/webapp"));
                dirs.add(0, new File(getSourcePath(),"../../api/webapp"));
            }
        }
        return dirs;
    }

    @NotNull
    @Override
    protected List<File> getResourceDirectories()
    {
        List<File> resources = super.getResourceDirectories();

        String root = AppProps.getInstance().getProjectRoot();
        if (root != null)
        {
            resources.add(new File(root + "/server/api"));
            resources.add(new File(root + "/server/internal"));
            if (AppProps.getInstance().isDevMode())
            {
                resources.add(new File(root + "/server/api/src"));
                resources.add(new File(root + "/server/internal/src"));

                resources.add(new File(root + "/build/modules/api"));
                resources.add(new File(root + "/build/modules/internal"));
            }
        }

        return resources;
    }

    @Override
    public void enumerateDocuments(SearchService.IndexTask task, @NotNull Container c, Date since)
    {
        if (null == task)
            task = ServiceRegistry.get(SearchService.class).defaultTask();

        if (c.isRoot())
            return;

        Container p = c.getProject();
        assert null != p;
        String displayTitle;
        String searchTitle;
        String body;

        // UNDONE: generalize to other folder types
        Study study = StudyService.get().getStudy(c);

        if (null != study)
        {
            displayTitle = study.getSearchDisplayTitle();
            searchTitle = study.getSearchKeywords();
            body = study.getSearchBody();
        }
        else
        {
            String type;

            if (c.isProject())
                type = "Project";
            else if (c.isWorkbook())
                type = "Workbook";
            else
                type = "Folder";

            String name = c.isWorkbook() ? c.getTitle() : c.getName();

            String description = StringUtils.trimToEmpty(c.getDescription());
            displayTitle = type + " -- " + name;
            searchTitle = name + " " + description + " " + type;
            body = type + " " + name + (c.isProject() ? "" : " in Project " + p.getName());
            body += "\n" + description;
        }

        Map<String, Object> properties = new HashMap<String, Object>();

        assert (null != searchTitle);
        properties.put(SearchService.PROPERTY.searchTitle.toString(), searchTitle);
        properties.put(SearchService.PROPERTY.displayTitle.toString(), displayTitle);
        properties.put(SearchService.PROPERTY.categories.toString(), SearchService.navigationCategory.getName());
        ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
        startURL.setExtraPath(c.getId());
        WebdavResource doc = new SimpleDocumentResource(c.getParsedPath(),
                "link:" + c.getId(),
                c.getId(),
                "text/plain",
                body.getBytes(),
                startURL,
                properties);
        task.addResource(doc, SearchService.PRIORITY.item);
    }

    
    @Override
    public void indexDeleted() throws SQLException
    {
        Table.execute(CoreSchema.getInstance().getSchema(), new SQLFragment(
            "UPDATE core.Documents SET lastIndexed=NULL"
        ));
    }

    public static class TestCase extends Assert
    {
        private TestContext _ctx;
        private User _user;
        Module _module;
        Container _project;
        Container _subFolder;
        String PROP1 = "TestProp";
        String PROP2 = "TestPropContainer";
        String PROJECT_NAME = "__ModulePropsTestProject";
        String FOLDER_NAME = "subfolder";

        @Before
        public void setUp()
        {
            _ctx = TestContext.get();
            User loggedIn = _ctx.getUser();
            assertTrue("login before running this test", null != loggedIn);
            assertFalse("login before running this test", loggedIn.isGuest());
            _user = _ctx.getUser().cloneUser();

            _module = new TestModule();
            ((TestModule)_module).init();
        }

        /**
         * Make sure module properties can be set, and that the correct coalesced values is returned (ie. if value not set on
         * a container, coalesce backwards to the first parent container where the value is set).
         */
        @Test
        public void testModuleProperties() throws Exception
        {
            if (ContainerManager.getForPath(PROJECT_NAME) != null)
            {
                ContainerManager.deleteAll(ContainerManager.getForPath(PROJECT_NAME), _user);
            }

            _project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME, null, null, false, _user);
            _subFolder = ContainerManager.createContainer(_project, FOLDER_NAME);

            Map<String, ModuleProperty> props = _module.getModuleProperties();
            ModuleProperty prop2 = props.get(PROP2);

            String rootVal = "RootValue";
            String projectVal = "ProjectValue";
            String folderVal = "FolderValue";

            prop2.saveValue(_user, ContainerManager.getRoot(), 0, rootVal);
            prop2.saveValue(_user, _project, 0, projectVal);
            prop2.saveValue(_user, _subFolder, 0, folderVal);

            assertEquals(rootVal, prop2.getEffectiveValue(_user, ContainerManager.getRoot()));
            assertEquals(projectVal, prop2.getEffectiveValue(_user, _project));
            assertEquals(folderVal, prop2.getEffectiveValue(_user, _subFolder));

            prop2.saveValue(_user, _subFolder, 0, null);
            assertEquals(projectVal, prop2.getEffectiveValue(_user, _subFolder));

            prop2.saveValue(_user, _project, 0, null);
            assertEquals(rootVal, prop2.getEffectiveValue(_user, _subFolder));

            prop2.saveValue(_user, ContainerManager.getRoot(), 0, null);
            assertEquals(prop2.getDefaultValue(), prop2.getEffectiveValue(_user, _subFolder));

            String newVal = "NewValue";
            prop2.saveValue(_user, _project, 0, newVal);
            assertEquals(prop2.getDefaultValue(), prop2.getEffectiveValue(_user, ContainerManager.getRoot()));
            assertEquals(newVal, prop2.getEffectiveValue(_user, _project));
            assertEquals(newVal, prop2.getEffectiveValue(_user, _subFolder));

            ContainerManager.deleteAll(_project, _user);
        }

        private class TestModule extends DefaultModule {
            @Override
            public void doStartup(ModuleContext c)
            {
            }

            @Override
            public void init()
            {
                setName("__JunitTestModule");
                ModuleProperty mp = new ModuleProperty(this, PROP1);
                mp.setCanSetPerContainer(false);
                addModuleProperty(mp);

                ModuleProperty mp2 = new ModuleProperty(this, PROP2);
                mp2.setCanSetPerContainer(true);
                mp2.setDefaultValue("Default");
                addModuleProperty(mp2);
            }

            @Override
            protected Collection<WebPartFactory> createWebPartFactories()
            {
                return new HashSet<WebPartFactory>();
            }

            @Override
            public boolean hasScripts()
            {
                return false;
            }
        }
    }
}
