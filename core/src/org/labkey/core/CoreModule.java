/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.module.*;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.*;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.menu.ContainerMenu;
import org.labkey.api.view.menu.ProjectsMenu;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavService;
import org.labkey.core.admin.AdminController;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.analytics.AnalyticsController;
import org.labkey.core.analytics.AnalyticsServiceImpl;
import org.labkey.core.attachment.AttachmentServiceImpl;
import org.labkey.core.ftp.FtpController;
import org.labkey.core.junit.JunitController;
import org.labkey.core.login.LoginController;
import org.labkey.core.query.*;
import org.labkey.core.security.SecurityController;
import org.labkey.core.test.TestController;
import org.labkey.core.user.UserController;
import org.labkey.core.webdav.DavController;
import org.labkey.core.webdav.FileSystemAuditViewFactory;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: migra
 * Date: Jul 25, 2005
 * Time: 2:54:30 PM
 */
public class CoreModule extends SpringModule
{
    public String getName()
    {
        return CORE_MODULE_NAME;
    }

    public double getVersion()
    {
        return 8.31;
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

        DefaultSchema.registerProvider("core", new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new CoreQuerySchema(schema.getUser(), schema.getContainer());
            }
        });

        if (null != getSourcePath())
        {
            File projectRoot = new File(getSourcePath(), "../../..");
            if (projectRoot.exists())
            {
                try
                {
                    AppProps.getInstance().setProjectRoot(projectRoot.getCanonicalPath());

                    String root = AppProps.getInstance().getProjectRoot();
                    ResourceFinder api = new ResourceFinder("API", root + "/server/api", root + "/build/modules/api");
                    ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", api);
                    ResourceFinder internal = new ResourceFinder("Internal", root + "/server/internal", root + "/build/modules/internal");
                    ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", internal);
                }
                catch(IOException e)
                {
                    // Do nothing -- leave project root null
                }
            }
        }
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(new BaseWebPartFactory("Contacts")
            {
                public WebPartView getWebPartView(ViewContext ctx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                {
                    return new ContactWebPart();
                }
            },
                new AlwaysAvailableWebPartFactory("Folders", "menubar", false, false) {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        ProjectsMenu projectsMenu = new ProjectsMenu(portalCtx);
                        projectsMenu.setCollapsed(false);
                        HBox v = new HBox(new ContainerMenu(portalCtx), new HtmlView("&nbsp;&nbsp;"), projectsMenu);
                        v.setTitle("Folders");
                        v.setFrame(WebPartView.FrameType.PORTAL);
                        return v;
                    }
                });
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
            bootstrap(moduleContext);

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


    private void bootstrap(ModuleContext moduleContext)
    {
        // Other containers inherit permissions from root; admins get all permisssions, users & guests none
        ContainerManager.bootstrapContainer("/", ACL.PERM_ALLOWALL, 0, 0);

        // Users & guests can read from /home
        ContainerManager.bootstrapContainer(ContainerManager.HOME_PROJECT_PATH, ACL.PERM_ALLOWALL, ACL.PERM_READ, ACL.PERM_READ);

        // Create the initial groups
        GroupManager.bootstrapGroup(Group.groupAdministrators, "Administrators");
        GroupManager.bootstrapGroup(Group.groupUsers, "Users");
        GroupManager.bootstrapGroup(Group.groupGuests, "Guests");
    }


    @Override
    public UpgradeCode getUpgradeCode()
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

        ContainerManager.addContainerListener(new CoreContainerListener());
        org.labkey.api.security.SecurityManager.init();
        ModuleLoader.getInstance().registerFolderType(FolderType.NONE);
        AppProps.getInstance().getUsageReportingLevel().scheduleUpgradeCheck();
        SystemMaintenance.setTimer();

        AuditLogService.get().addAuditViewFactory(UserAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(GroupAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(AttachmentAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ContainerAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(FileSystemAuditViewFactory.getInstance());

        ContextListener.addStartupListener(TempTableTracker.getStartupListener());
        ContextListener.addShutdownListener(TempTableTracker.getShutdownListener());
        ContextListener.addShutdownListener(DavController.getShutdownListener());

        AdminController.registerAdminConsoleLinks();
        AnalyticsController.registerAdminConsoleLinks();

        WebdavService.setResolver(WebdavResolverImpl.get());
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
        else if (c != null && c.hasPermission(user, ACL.PERM_ADMIN))
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
            org.labkey.common.tools.PeptideTestCase.class,
            org.labkey.api.data.ContainerManager.TestCase.class,
            org.labkey.common.tools.TabLoader.TabLoaderTestCase.class,
            org.labkey.common.tools.ExcelLoader.ExcelLoaderTestCase.class,
            ModuleDependencySorter.TestCase.class,
            org.labkey.api.security.GroupManager.TestCase.class,
            DateUtil.TestCase.class,
            DatabaseCache.TestCase.class,
            SecurityController.TestCase.class,
            AttachmentServiceImpl.TestCase.class,
            BooleanFormat.TestCase.class,
            XMLWriterTest.TestCase.class,
            WebdavResolverImpl.TestCase.class,
            org.labkey.api.exp.Lsid.TestCase.class,
            MimeMap.TestCase.class,
            FileUtil.TestCase.class,
            MemTracker.TestCase.class,
            SqlDialect.SqlDialectTestCase.class,
            HString.TestCase.class
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
}
