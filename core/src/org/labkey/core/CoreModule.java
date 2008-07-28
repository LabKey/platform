/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
import org.apache.log4j.Logger;
import org.fhcrc.cpas.util.NetworkDrive;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.data.SqlScriptRunner.SqlScript;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.module.*;
import org.labkey.api.security.*;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.core.admin.AdminController;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.analytics.AnalyticsController;
import org.labkey.core.analytics.AnalyticsServiceImpl;
import org.labkey.core.attachment.AttachmentServiceImpl;
import org.labkey.core.ftp.FtpController;
import org.labkey.core.junit.JunitController;
import org.labkey.core.login.LoginController;
import org.labkey.core.query.AttachmentAuditViewFactory;
import org.labkey.core.query.ContainerAuditViewFactory;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.query.UserAuditViewFactory;
import org.labkey.core.security.SecurityController;
import org.labkey.core.test.TestController;
import org.labkey.core.user.UserController;
import org.labkey.core.webdav.FileSystemAuditViewFactory;
import org.labkey.core.webdav.WebdavResolverImpl;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.beans.PropertyChangeEvent;
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
public class CoreModule extends SpringModule implements ContainerManager.ContainerListener, FirstRequestHandler.FirstRequestListener
{
    private static Logger _log = Logger.getLogger(CoreModule.class);
    private static final String NAME = CORE_MODULE_NAME;


    public CoreModule()
    {
        super(NAME, 8.21, "/org/labkey/core", false,
            new WebPartFactory("Contacts")
            {
                public WebPartView getWebPartView(ViewContext ctx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                {
                    return new ContactWebPart();
                }
            });

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
        FirstRequestHandler.addFirstRequestListener(this);

//        DefaultSchema.registerProvider("core", new DefaultSchema.SchemaProvider()
//        {
//            public QuerySchema getSchema(DefaultSchema schema)
//            {
//                return new CoreQuerySchema(schema.getUser(), schema.getContainer());
//            }
//        });            
    }

    @Override
    public void bootstrap()
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

        super.bootstrap();
    }


    // Note: Core module is special -- versionUpdate gets called during Tomcat startup so we don't hit the Logins, ACLs,
    // Members, UsersData, etc. tables before they're created (bootstrap time) or modified (upgrade time).  This code
    // is not thread-safe -- it should be called once at startup.
    @Override
    public ActionURL versionUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        // TODO: PageFlowUtil.getContentsAsString()
        // TODO: Break up PG scripts into multiple executes

        beforeSchemaUpdate(moduleContext, viewContext);

        try
        {
            // This provider retrieves scripts in the core schema
            SqlScriptProvider coreProvider = new FileSqlScriptProvider(this) {
                @Override
                protected boolean shouldInclude(SqlScript script)
                {
                    return script.getSchemaName().equals("core");
                }
            };

            // This provider retrieves scripts in the other schemas (portal, prop, test)
            SqlScriptProvider nonCoreProvider = new FileSqlScriptProvider(this) {
                @Override
                protected boolean shouldInclude(SqlScript script)
                {
                    return !script.getSchemaName().equals("core");
                }
            };

            List<SqlScript> scripts = new ArrayList<SqlScript>();

            if (0.0 != moduleContext.getInstalledVersion())
            {
                scripts.addAll(coreProvider.getDropScripts());
                scripts.addAll(nonCoreProvider.getDropScripts());
            }

            // Must run all the core schema scripts first followed by the other schemas
            scripts.addAll(SqlScriptRunner.getRecommendedScripts(coreProvider, null, moduleContext.getInstalledVersion(), getVersion()));
            scripts.addAll(SqlScriptRunner.getRecommendedScripts(nonCoreProvider, null, moduleContext.getInstalledVersion(), getVersion()));
            scripts.addAll(coreProvider.getCreateScripts());
            scripts.addAll(nonCoreProvider.getCreateScripts());

            SqlScriptRunner.runScripts(null, scripts, coreProvider);
            SqlScriptRunner.waitForScriptsToFinish();

            DbSchema.invalidateSchemas();
        }
        catch(SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (InterruptedException e)
        {
            throw(new RuntimeException(e));
        }
        catch (SqlScriptRunner.SqlScriptException e)
        {
            throw new RuntimeException(e);
        }

        Exception se = SqlScriptRunner.getException();
        if (null != se)
            throw new RuntimeException(se);

        afterSchemaUpdate(moduleContext, viewContext);

        return null;
    }


    @Override
    public void beforeUpdate()
    {
        // Do nothing
    }

    @Override
    public void afterSchemaUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        double installedVersion = moduleContext.getInstalledVersion();

        if (installedVersion == 0.0)
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
        else if (installedVersion < 1.6)
        {
            upgradeTo160();
        }
        else if (installedVersion < 1.74)
        {
            try
            {
                upgradeTo174();
            }
            catch (NamingException e)
            {
                throw new RuntimeException(e);
            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }
        }

        if (installedVersion < 8.11)
            GroupManager.bootstrapGroup(Group.groupDevelopers, "Developers", GroupManager.PrincipalType.ROLE);
        if (installedVersion > 0 && installedVersion < 8.12)
            migrateLdapSettings();

        // TODO: New internalAfterSchemaUpdate method in base class to avoid calling this.  super.afterSchemaUpdate(moduleContext, viewContext);
    }

    @Override
    public void afterUpdate()
    {
    }

    private void migrateLdapSettings()
    {
        try
        {
            Map<String, String> props = PropertyManager.getSiteConfigProperties();
            String domain = props.get("LDAPDomain");

            if (null != domain && domain.trim().length() > 0)
            {
                PropertyManager.PropertyMap map = PropertyManager.getWritableProperties("LDAPAuthentication", true);
                map.put("Servers", props.get("LDAPServers"));
                map.put("Domain", props.get("LDAPDomain"));
                map.put("PrincipalTemplate", props.get("LDAPPrincipalTemplate"));
                map.put("SASL", props.get("LDAPAuthentication"));
                PropertyManager.saveProperties(map);
                saveAuthenticationProviders(true);
            }
            else
            {
                saveAuthenticationProviders(false);
            }
        }
        catch (SQLException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }

    private static void saveAuthenticationProviders(boolean enableLdap)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties("Authentication", true);
        String activeAuthProviders = map.get("Authentication");

        if (null == activeAuthProviders)
            activeAuthProviders = "Database";

        if (enableLdap)
        {
            if (!activeAuthProviders.contains("LDAP"))
                activeAuthProviders = activeAuthProviders + ":LDAP";
        }
        else
        {
            activeAuthProviders = activeAuthProviders.replaceFirst("LDAP:", "").replaceFirst(":LDAP", "").replaceFirst("LDAP", "");
        }

        map.put("Authentication", activeAuthProviders);
        PropertyManager.saveProperties(map);
    }

    private void upgradeTo174() throws NamingException, SQLException
    {
        Context initCtx = new InitialContext();
        Context env = (Context) initCtx.lookup("java:comp/env");
        NetworkDrive drive;
        for (char driveChar = 'a'; driveChar <= 'z'; driveChar++)
        {
            try
            {
                drive = (NetworkDrive) env.lookup("drive/" + driveChar);
                WriteableAppProps appProps = AppProps.getWriteableInstance();
                appProps.setNetworkDriveLetter(Character.toString(driveChar));
                appProps.setNetworkDriveUser(drive.getUser());
                appProps.setNetworkDrivePassword(drive.getPassword());
                appProps.setNetworkDrivePath(drive.getPath());
                appProps.save();

                // We currently only support a single network drive configuration
                break;
            }
            catch (NameNotFoundException e)
            {
                // Bail out - not configured as a network drive we can try to map for the user
            }
        }
    }


    @Override
    public void destroy()
    {
        super.destroy();
        UsageReportingLevel.cancelUpgradeCheck();
    }


    @Override
    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);

        ContainerManager.addContainerListener(this);
        org.labkey.api.security.SecurityManager.init();
        ModuleLoader.getInstance().registerFolderType(FolderType.NONE);
        AppProps.getInstance().getUsageReportingLevel().scheduleUpgradeCheck();
        SystemMaintenance.setTimer();

        AuditLogService.get().addAuditViewFactory(UserAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(GroupAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(AttachmentAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ContainerAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(FileSystemAuditViewFactory.getInstance());

        if (null != getBuildPath())
        {
            File projectRoot = new File(getBuildPath(), "../../..");
            if (projectRoot.exists())
            {
                try
                {
                    AppProps.getInstance().setProjectRoot(projectRoot.getCanonicalPath());
                }
                catch(IOException e)
                {
                    // Do nothing -- leave project root null
                }
            }
        }

        ContextListener.addStartupListener(TempTableTracker.getStartupListener());
        ContextListener.addShutdownListener(TempTableTracker.getShutdownListener());
        ContextListener.addShutdownListener(org.labkey.core.webdav.DavController.getShutdownListener());

        AdminController.registerAdminConsoleLinks();
        AnalyticsController.registerAdminConsoleLinks();
    }

    @Override
    public String getTabName(ViewContext context)
    {
        return "Admin";
    }


    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        String containerPath = c == null ? null : c.getPath();
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
            ActionURL result = new ActionURL("User", "details.view", containerPath);
            result.addParameter("pk", Integer.toString(user.getUserId()));
            return result;
        }
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_NEVER;
    }


    public void containerCreated(Container c)
    {
        User user = UserManager.getGuestUser();
        try {
            ViewContext context = HttpView.currentContext();
            if (context != null)
            {
                user = context.getUser();
            }
        }
        catch (RuntimeException e){}
        String message = c.isProject() ? "Project " + c.getName() + " was created" :
                "Folder " + c.getName() + " was created";
        addAuditEvent(user, c, message);
    }

    private void addAuditEvent(User user, Container c, String comment)
    {
        if (user != null)
        {
            AuditLogEvent event = new AuditLogEvent();

            event.setCreatedBy(user);
            event.setEventType(ContainerManager.CONTAINER_AUDIT_EVENT);
            event.setContainerId(c.getId());
            event.setComment(comment);

            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());

            AuditLogService.get().addEvent(event);
        }
    }

    public void containerDeleted(Container c, User user)
    {
        try
        {
            PropertyManager.purgeObjectProperties(c.getId());
            // Let containerManager delete ACLs, we want that to happen last

            String message = c.isProject() ? "Project " + c.getName() + " was deleted" :
                    "Folder " + c.getName() + " was deleted";
            addAuditEvent(user, c, message);
        }
        catch (SQLException e)
        {
            _log.error("Failed to delete Properties for container '" + c.getPath() + "'.", e);
        }
    }


    public void propertyChange(PropertyChangeEvent evt)
    {
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
            ModuleDependencySorter.TestCase.class,
            org.labkey.api.security.GroupManager.TestCase.class,
            DateUtil.TestCase.class,
            DatabaseCache.TestCase.class,
            SecurityController.TestCase.class,
            AttachmentServiceImpl.TestCase.class,
            BooleanFormat.TestCase.class,
            XMLWriterTest.TestCase.class,
            WebdavResolverImpl.TestCase.class,
            org.labkey.api.exp.Lsid.TestCase.class
        )
        );
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

    // On PostgreSQL installations prior to CPAS 1.6, the same email address could be added more than once using different casing.
    // This routine deletes duplicate email addresses on PostgreSQL installations and forces all emails to lowercase.
    private void upgradeTo160()
    {
        DbSchema core = CoreSchema.getInstance().getSchema();
        TableInfo users = CoreSchema.getInstance().getTableInfoUsers();

        // Only need to delete users on case-sensitive installations (PostgreSQL)
        if (core.getSqlDialect().isCaseSensitive())
        {
            // For email addresses that have duplicates, keep the most recently used user.  Most recently used is the user with
            // the latest LastLogin.  If LastLogin is NULL for all duplicates, then keep the most recently modified.
            SQLFragment sql = new SQLFragment("SELECT UserId FROM " + users + " u JOIN (SELECT LOWER(Email) AS Email, MAX(LastLogin) AS ll, MAX(Modified) AS mod FROM " + users + "\n" +
                    "GROUP BY LOWER(Email)\n" +
                    "HAVING COUNT(*) > 1) dup ON dup.email = LOWER(u.email)\n" +
                    "WHERE CASE WHEN ll IS NULL THEN mod <> Modified ELSE LastLogin IS NULL OR ll <> LastLogin END");

            Integer[] duplicateUserIds;

            try
            {
                duplicateUserIds = Table.executeArray(core, sql, Integer.class);
            }
            catch(SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            for (int userId : duplicateUserIds)
            {
                try
                {
                    UserManager.deleteUser(userId);
                }
                catch(SecurityManager.UserManagementException e)
                {
                    _log.error("Error attempting to delete user", e);
                }
            }
        }

        // For all database types, force user names in Principals and Logins to all lowercase
        TableInfo principals = CoreSchema.getInstance().getTableInfoPrincipals();
        TableInfo logins = CoreSchema.getInstance().getTableInfoLogins();

        try
        {
            Table.execute(core, "UPDATE " + logins + " SET Email = LOWER(Email)", null);
            Table.execute(core, "UPDATE " + principals + " SET Name = LOWER(Name) WHERE Type = 'u'", null);
        }
        catch (SQLException e)
        {
            _log.error("Error attempting to convert to lowercase user names", e);
        }
    }

    public void handleFirstRequest(HttpServletRequest request)
    {
        ViewServlet.initialize();
        ModuleLoader.getInstance().initPageFlowToModule();        
        AuthenticationManager.setLoginURLFactory(new LoginController.LoginURLFactoryImpl());
        AuthenticationManager.setLogoutURL(LoginController.getLogoutURL());
        AuthenticationManager.initialize();
        UserManager.registerUserDetailsURLFactory(new UserController.DetailsURLFactoryImpl());
    }


    // Base class returns _shouldRunScripts -- override because Core does have scripts, it just doesn't want
    // DefaultModule to run them.
    @Override
    public boolean hasScripts()
    {
        return true;
    }
}
