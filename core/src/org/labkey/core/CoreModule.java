/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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

import junit.framework.TestCase;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.module.*;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.MapTabLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.script.RhinoService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.*;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.*;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.menu.ContainerMenu;
import org.labkey.api.view.menu.ProjectsMenu;
import org.labkey.api.webdav.*;
import org.labkey.core.admin.ActionsTsvWriter;
import org.labkey.core.admin.AdminController;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.analytics.AnalyticsController;
import org.labkey.core.analytics.AnalyticsServiceImpl;
import org.labkey.core.attachment.AttachmentServiceImpl;
import org.labkey.core.ftp.FtpController;
import org.labkey.core.junit.JunitController;
import org.labkey.core.login.DbLoginAuthenticationProvider;
import org.labkey.core.login.LoginController;
import org.labkey.core.query.*;
import org.labkey.core.security.SecurityController;
import org.labkey.core.test.TestController;
import org.labkey.core.user.UserController;
import org.labkey.core.webdav.DavController;
import org.labkey.api.webdav.FileSystemAuditViewFactory;
import org.labkey.core.workbook.WorkbookFolderType;
import org.labkey.core.workbook.WorkbookQueryView;
import org.labkey.core.workbook.WorkbookSearchView;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: migra
 * Date: Jul 25, 2005
 * Time: 2:54:30 PM
 */
public class CoreModule extends SpringModule implements SearchService.DocumentProvider
{
    public String getName()
    {
        return CORE_MODULE_NAME;
    }

    public double getVersion()
    {
        return 10.13;
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

    protected void init()
    {
        SqlDialect.register(new SqlDialectPostgreSQL());

        addController("admin", AdminController.class);
        addController("admin-sql", SqlScriptController.class);
        addController("security", SecurityController.class);
        addController("user", UserController.class);
        addController("login", LoginController.class);
        addController("junit", JunitController.class);
        addController("core", CoreController.class);
        addController("test", TestController.class);
        addController("ftp", FtpController.class);
        addController("analytics", AnalyticsController.class);

        AuthenticationManager.registerProvider(new DbLoginAuthenticationProvider(), Priority.Low);
        AttachmentService.register(new AttachmentServiceImpl());
        AnalyticsServiceImpl.register();
        FirstRequestHandler.addFirstRequestListener(new CoreFirstRequestHandler());
        RhinoService.register();

        DefaultSchema.registerProvider("core", new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new CoreQuerySchema(schema.getUser(), schema.getContainer());
            }
        });

        List<String> possibleRoots = new ArrayList<String>();
        if (null != getSourcePath())
            possibleRoots.add(getSourcePath() + "/../../..");
        if (null != System.getProperty("project.root"))
            possibleRoots.add(System.getProperty("project.root"));

        for (String root : possibleRoots)
        {
            File projectRoot = new File(root);
            if (projectRoot.exists())
            {
                try
                {
                    AppProps.getInstance().setProjectRoot(projectRoot.getCanonicalPath());

                    root = AppProps.getInstance().getProjectRoot();
                    ResourceFinder api = new ResourceFinder("API", root + "/server/api", root + "/build/modules/api");
                    ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", api);
                    ResourceFinder internal = new ResourceFinder("Internal", root + "/server/internal", root + "/build/modules/internal");
                    ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", internal);

                    // set the root only once
                    break;
                }
                catch (IOException e)
                {
                    // ignore
                }
            }
        }
    }

    @Override
    public Set<ModuleResourceLoader> getResourceLoaders()
    {
        return Collections.<ModuleResourceLoader>singleton(new FolderTypeResourceLoader());
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(new AlwaysAvailableWebPartFactory("Contacts")
            {
                public WebPartView getWebPartView(ViewContext ctx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                {
                    return new ContactWebPart();
                }
            },
                new AlwaysAvailableWebPartFactory("Folders", "menubar", false, false) {
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
                }));
    }


    @Override
    public void beforeUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
        {
            CoreSchema core = CoreSchema.getInstance();

            try
            {
                core.getSqlDialect().prepareNewDatabase(core.getSchema());
            }
            catch(ServletException e)
            {
                throw new RuntimeException(e);
            }
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

        if (moduleContext.getInstalledVersion() < 9.11)
        {
            getUpgradeCode().installDefaultQcValues();
        }
    }


    private void bootstrap()
    {
        // Create the initial groups
        GroupManager.bootstrapGroup(Group.groupAdministrators, "Administrators");
        GroupManager.bootstrapGroup(Group.groupUsers, "Users");
        GroupManager.bootstrapGroup(Group.groupGuests, "Guests");

        // Other containers inherit permissions from root; admins get all permisssions, users & guests none
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        Role siteAdminRole = RoleManager.getRole(SiteAdminRole.class);
        Role readerRole = RoleManager.getRole(ReaderRole.class);

        ContainerManager.bootstrapContainer("/",
                siteAdminRole, noPermsRole, noPermsRole);

        // Users & guests can read from /home
        ContainerManager.bootstrapContainer(ContainerManager.HOME_PROJECT_PATH, siteAdminRole, readerRole, readerRole);
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


    public void startup(ModuleContext moduleContext)
    {
        initWebApplicationContext();

        // This listener deletes all properties; make sure it executes after most of the other listeners
        ContainerManager.addContainerListener(new CoreContainerListener(), ContainerManager.ContainerListener.Order.Last);
        org.labkey.api.security.SecurityManager.init();
        ModuleLoader.getInstance().registerFolderType(FolderType.NONE);
        AppProps.getInstance().getUsageReportingLevel().scheduleUpgradeCheck();
        SystemMaintenance.setTimer();

        AuditLogService.get().addAuditViewFactory(UserAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(GroupAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(AttachmentAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ContainerAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(FileSystemAuditViewFactory.getInstance());

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
                    catch (ServletException e)
                    {
                        Logger.getLogger(CoreModule.class).error("Exception exporting action stats", e);
                    }

                    logger.info(buf.toString());
                }
            }
        });

        AdminController.registerAdminConsoleLinks();
        AnalyticsController.registerAdminConsoleLinks();

        WebdavService.get().setResolver(WebdavResolverImpl.get());
        ModuleLoader.getInstance().registerFolderType(new WorkbookFolderType());


        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
        {
            ss.addDocumentProvider(this);
        }
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
            return PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(c, user.getUserId());
        }
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_NEVER;
    }


    @Override
    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            org.labkey.api.data.Table.TestCase.class,
            org.labkey.api.data.DbSchema.TestCase.class,
            org.labkey.api.data.TableViewFormTestCase.class,
            ActionURL.TestCase.class,
            org.labkey.api.security.SecurityManager.TestCase.class,
            org.labkey.api.data.PropertyManager.TestCase.class,
            org.labkey.api.util.DateUtil.TestCase.class,
            org.labkey.api.data.ContainerManager.TestCase.class,
            TabLoader.TabLoaderTestCase.class,
            MapTabLoader.MapLoaderTestCase.class,
            org.labkey.api.data.TSVWriter.TestCase.class,
            ExcelLoader.ExcelLoaderTestCase.class,
            ModuleDependencySorter.TestCase.class,
            org.labkey.api.security.GroupManager.TestCase.class,
            DateUtil.TestCase.class,
            DatabaseCache.TestCase.class,
            SecurityController.TestCase.class,
            PasswordExpiration.TestCase.class,
            AttachmentServiceImpl.TestCase.class,
            BooleanFormat.TestCase.class,
            XMLWriterTest.TestCase.class,
            WebdavResolverImpl.TestCase.class,
            org.labkey.api.exp.Lsid.TestCase.class,
            MimeMap.TestCase.class,
            FileUtil.TestCase.class,
            FileType.TestCase.class,
            MemTracker.TestCase.class,
            SqlDialect.SqlDialectTestCase.class,
            HString.TestCase.class,
            StringExpressionFactory.TestCase.class,
            Path.TestCase.class,
            ModuleStaticResolverImpl.TestCase.class
            //,RateLimiter.TestCase.class
        ));
    }


    @Override
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
    protected Class[] getResourceClasses()
    {
        return new Class[] { getClass(), org.labkey.api.RemoteControllerProxy.class };
    }

    @NotNull
    @Override
    protected List<File> getResourceDirectories()
    {
        List<File> resources = super.getResourceDirectories();

        String root = AppProps.getInstance().getProjectRoot();

        resources.add(new File(root + "/server/api"));
        resources.add(new File(root + "/server/internal"));
        if (AppProps.getInstance().isDevMode())
        {
            resources.add(new File(root + "/build/modules/api"));
            resources.add(new File(root + "/build/modules/internal"));
        }

        return resources;
    }

    public void enumerateDocuments(SearchService.IndexTask task, Container c, Date since)
    {
        containerSimple(task,c);
    }
    
    private void containerSimple(SearchService.IndexTask task, Container c)
    {
        if (null == c || c.isRoot())
            return;
        Container p = c.getProject();
        String displayTitle;
        String searchTitle = null;
        String body;

        // UNDONE: generalize to other folder types
        Study study = StudyService.get().getStudy(c);
        if (null != study)
        {
            displayTitle = "Study -- " + study.getLabel();

            if (p.equals(c))
                body = "Study Project " + p.getName();
            else
                body = "Study Folder " + c.getName() + " in Project " + p.getName();

            searchTitle = study.getSearchKeywords();
        }
        else if (c.isProject())
        {
            displayTitle = "Project -- " + c.getName();
            body = "";
            body += "\n" + StringUtils.trimToEmpty(c.getDescription());
        }
        else if (c.isWorkbook())
        {
            displayTitle = "Workbook -- " + c.getName();
            body = "Workbook " + c.getName() + " in Project " + p.getName();
        }
        else
        {
            displayTitle = "Folder -- " + c.getName();
            body = "Folder " + c.getName() + " in Project " + p.getName();
            body += "\n" + StringUtils.trimToEmpty(c.getDescription());
        }

        Map<String, Object> properties = new HashMap<String, Object>();

        if (null != searchTitle)
            properties.put(SearchService.PROPERTY.searchTitle.toString(), searchTitle);
        properties.put(SearchService.PROPERTY.displayTitle.toString(), displayTitle);
        properties.put(SearchService.PROPERTY.categories.toString(), SearchService.navigationCategory.getName());
        ActionURL startURL = new ActionURL("project","start",c);
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

    
    private void containerPortal(SearchService.IndexTask task, Container c)
    {
        if (null == c || c.isRoot())
            return;
        String title;
        Study study = StudyService.get().getStudy(c);
        if (c.isProject())
        {
            title = "Project -- " + c.getName();
        }
        else if (c.isWorkbook())
        {
            title = "Workbook -- " + c.getName();
        }
        else if (null != study)
        {
            title = "Study -- " + study.getLabel();
        }
        else
        {
            title = "Folder -- " + c.getName();
        }

        ViewContext v = new ViewContext();
        v.setUser(User.getSearchUser());
        ActionURL url = c.getStartURL(v);
        final String docid = "container:" + c.getId();
        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put(SearchService.PROPERTY.displayTitle.toString(), title);
        map.put(SearchService.PROPERTY.categories.toString(), SearchService.navigationCategory.getName());
        ActionResource r = new ActionResource(SearchService.navigationCategory, docid, url, url, map);
        task.addResource(r, SearchService.PRIORITY.item);
    }


    public void indexDeleted() throws SQLException
    {
        Table.execute(CoreSchema.getInstance().getSchema(), new SQLFragment(
            "UPDATE core.Documents SET lastIndexed=NULL"
        ));
    }
}
