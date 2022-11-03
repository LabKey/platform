/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.module;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.collections4.Factory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SchemaTableInfoFactory;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.OlapSchemaInfo;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.writer.ContainerUser;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Modules are the basic unit of deployment for code and resources within LabKey Server. Modules are deployable
 * independently of one another. They can have dependencies on services or other resources provided by other modules,
 * but should declare these dependencies.
 * User: migra
 * Date: Jul 14, 2005
 */
public interface Module
{
    enum TabDisplayMode
    {
        DISPLAY_NEVER,
        DISPLAY_USER_PREFERENCE,
        DISPLAY_USER_PREFERENCE_DEFAULT,
        DISPLAY_FOLDER_TYPE
    }

    enum SupportedDatabase
    {
        mssql, pgsql;

        public static SupportedDatabase get(SqlDialect dialect)
        {
            if (dialect.isSqlServer())
                return mssql;

            if (dialect.isPostgreSQL())
                return pgsql;

            throw new IllegalStateException("Dialect not supported");
        }
    }


    /**
     * Perform any post-constructor initialization
     */
    void initialize();

    /**
     * Name of this module
     */
    String getName();

    /**
     * Can this module be enabled in this container?
     */
    default boolean canBeEnabled(Container c)
    {
        return true;
    }

    /**
     * Return this module's schema version. This version controls the upgrade process, particularly the running of SQL upgrade scripts.
     */
    @Nullable Double getSchemaVersion();

    @Nullable default String getFormattedSchemaVersion()
    {
        Double schemaVersion = getSchemaVersion();
        return null != schemaVersion ? ModuleContext.formatVersion(schemaVersion) : null;
    }

    /**
     * Return this module's release version, e.g., "20.3-SNAPSHOT" or "20.3.4"
     */
    @Nullable String getReleaseVersion();

    /** One line description of module's purpose (capitalized and without a period at the end) */
    @Nullable String getLabel();

    /** Multi-line description of module. */
    @Nullable String getDescription();

    /** The homepage URL for additional information on the module. */
    @Nullable String getUrl();

    /** Comma separated list of names and, optionally, email addresses: e.g. "Adam Rauch &lt;adam@labkey.com&gt;, Kevin Krouse" */
    @Nullable String getAuthor();

    /** Comma separated list of names and, optionally, email addresses: e.g. "Adam Rauch &lt;adam@labkey.com&gt;, Kevin Krouse" */
    @Nullable String getMaintainer();

    @Nullable String getOrganization();

    @Nullable String getOrganizationUrl();

    /**
     * The type of build that created this module - may be null if not explicitly set, but typically either Development
     * (from running ant build) or Production (from running ant production)
     */
    @Nullable String getBuildType();

    /** License name: e.g. "Apache 2.0", "LabKey Software License" */
    @Nullable String getLicense();

    /** License URL: e.g. "http://www.apache.org/licenses/LICENSE-2.0" */
    @Nullable String getLicenseUrl();

    /**
     * Called on every module in REVERSE dependency order before versionUpdate() is called, as long as at least one module
     * requires updating. This is a fine place to drop views and other dependent objects.
     */
    void beforeUpdate(ModuleContext moduleContext);

    /**
     * Do any version updating module needs to do.
     * <p/>
     * At installation time, ModuleContext.getInstalledVersion() will be 0.0
     * <p/>
     */
    void versionUpdate(ModuleContext moduleContext) throws Exception;

    /** Called on each module in dependency order after versionUpdate(), as long as at least one module requires updating. */
    void afterUpdate(ModuleContext moduleContext);

    /**
     * The application is starting. Version updating is complete. startup() has been called on all dependencies.
     */
    void startup(ModuleContext moduleContext);

    /**
     * All modules have been upgraded and started, plus the base server URL is known (either the property is set or first
     * request has been received). It's now safe to produce absolute URLs (e.g., URLHelper.getURIString()), which background
     * threads like to do (when sending email or constructing mock requests).
     */
    default void startBackgroundThreads()
    {
    }

    /**
     * The application is shutting down "gracefully". Module
     * should do any cleanup (file handles etc) that is required.
     * Note: There is no guarantee that this will run if the server
     * process is without a nice shutdown.
     */
    void destroy();

    /**
     * Return Collection of WebPartFactory objects for this module.
     * NOTE: This may be called before startup, but will never be called
     * before upgrade is complete.
     *
     * @return Collection of WebPartFactory (empty collection if none)
     */
    @JsonIgnore
    @NotNull Collection<WebPartFactory> getWebPartFactories();

    /**
     * @param c container in which the items would be stored
     * @return description of the objects that this module has stored in the container
     */
    @NotNull Collection<String> getSummary(Container c);

    /**
     * Returns a map of pageflow name to controller class (for example, "wiki" -> WikiController) whose
     * functionality is considered part of this module. All pageflows in the system
     * must be associated with one (and only one) module.
     *
     * @return A map of pageflow name to controller class
     */
    @JsonIgnore
    Map<String, Class<? extends Controller>> getControllerNameToClass();

    @JsonIgnore
    Map<Class<? extends Controller>, String> getControllerClassToName();

    /**
     * Name to show on the tab in the UI
     */
    String getTabName(ViewContext context);

    /**
     * Returns the url that will be the target of a click on the module's tab.
     */
    ActionURL getTabURL(Container c, User user);

    /**
     * @return under what conditions this module's tab should be shown in the UI
     */
    TabDisplayMode getTabDisplayMode();

    /**
     * Modules can provide JUnit tests that must be run inside the server
     * VM. These tests will be executed as part of the DRT. These are not true unit tests, and may rely on external
     * resources such as a database connection or services provided by other modules.
     * @return the integration tests that this module provides
     */
    @NotNull
    @JsonIgnore
    Set<Class> getIntegrationTests();

    /**
     * Modules can provide JUnit tests that must be run inside the server
     * VM. These tests will be executed as part of the DRT. These are not true unit tests, and may rely on external
     * resources such as a database connection or services provided by other modules.
     * @return the integration tests that this module provides
     */
    @JsonIgnore
    default @NotNull Collection<Factory<Class<?>>> getIntegrationTestFactories()
    {
        return getIntegrationTests().stream().map(c -> (Factory<Class<?>>)() -> c).collect(Collectors.toList());
    }

    /**
     * Modules can provide JUnit tests that can be run independent of the server VM. Satisfies the requirements for a
     * traditional unit test.
     * @return the unit tests that this module provides
     */
    @NotNull
    @JsonIgnore
    Set<Class> getUnitTests();

    /**
     * Returns a set of schemas that the module wants tested.
     * The DbSchema junit test calls this and ensures that the
     * the tables/views/columns described in schema XML match
     * those in the database metadata.
     * @return the schemas associated with this module that should be tested
     */
    @NotNull
    @JsonIgnore
    Set<DbSchema> getSchemasToTest();

    /**
     * Returns the names of all schemas that this module owns, both module and provisioned. Used to determine which module
     * should load the related resources and to help administrators manage schemas & modules.
     * @return the schema names owned by this module
     */
    @NotNull
    Collection<String> getSchemaNames();

    /**
     * Returns the names of the provisioned schemas that this module owns. Used to distinguish module vs. provisioned
     * schemas in general code paths that can't determine schema type (e.g., sql script runner).
     * @return the provisioned schema names owned by this module
     */
    @NotNull
    Collection<String> getProvisionedSchemaNames();

    @NotNull Set<SupportedDatabase> getSupportedDatabasesSet();

    @JsonIgnore
    ModuleResourceResolver getModuleResolver();
    Resource getModuleResource(String path);
    Resource getModuleResource(Path path);
    InputStream getResourceStream(String filename) throws IOException;

    String getSourcePath();
    String getBuildPath();
    String getVcsRevision();
    String getVcsUrl();
    String getVcsBranch();
    String getVcsTag();
    String getBuildNumber();

    default String getBuildTime()
    {
        return null;
    }

    Map<String, String> getProperties();
    Set<String> getModuleDependenciesAsSet();
    @JsonIgnore
    Set<Module> getResolvedModuleDependencies();
    boolean shouldManageVersion();

    /**
     * Returns the exploded path for the module.
     * @return The path to the exploded module directory
     */
    File getExplodedPath();

    /**
     * This is called by the module loader to let the module know where its exploded path is
     * so that the module can later load resources.
     * @param path The path to the module's exploded directory
     */
    void setExplodedPath(File path);

    @Nullable
    File getZippedPath();
    void setZippedPath(File zipped);

    /**
     * Returns a list of sql script file names for a given schema
     *
     * @param schema The schema
     * @return The list of sql script names
     */
    Set<String> getSqlScripts(@NotNull DbSchema schema);

    /**
     * Returns the file path for this modules sql scripts
     * @param dialect The sql dialect for the scripts
     * @return The script file path
     */
    String getSqlScriptsPath(@NotNull SqlDialect dialect);

    /**
     * handle a http request
     *
     * @param request as provided by Servlet.service
     * @param response as provided by Servlet.service
     * @param url parsed ActionURL for a .view/.post requests
     */
    void dispatch(HttpServletRequest request, HttpServletResponse response, ActionURL url) throws ServletException, IOException;

    Controller getController(HttpServletRequest request, String name);

    /**
     * return a list of locations to look for static website files. Files in these directories have no security.
     * @return a list of static file locations
     */
    @NotNull
    List<File> getStaticFileDirectories();

    /**
     * Used in dev mode to verify that the module credits pages is complete.
     * @return Jar filenames used by this module
     */
    @NotNull Collection<String> getJarFilenames();

    // Should LabKey should automatically uninstall this module (drop its schemas, delete SqlScripts rows, delete Modules rows)
    // if the module no longer exists?  This setting gets saved to the Modules table.
    boolean isAutoUninstall();

    /**
     * Methods used by the module loader to add and execute upgrade tasks that need to be invoked after
     * a module is initialized.
     */
    void addDeferredUpgradeRunnable(String description, Runnable runnable);
    void runDeferredUpgradeRunnables();

    @JsonIgnore
    Map<String, ModuleProperty> getModuleProperties();

    /**
     * The returned JSONObject will be written to the page automatically. By default, it includes any module properties where 'writeToClient'
     * is true. However, individual modules can override this to return any content they choose. The method is called at page rendering time
     * once startup is complete, so all module schemas should be upgraded and services should be registered.
     * Note: this is written as plain text.
     * @param context ContainerUser of the request
     */
    JSONObject getPageContextJson(ContainerUser context);

    @NotNull List<Supplier<ClientDependency>> getClientDependencies(Container c);

    @JsonIgnore
    @Nullable UpgradeCode getUpgradeCode();

    @Nullable
    String getResourcePath();

    boolean getRequireSitePermission();

    /**
     * Enables modules to publish schema information for Olap queries.
     */
    @Nullable
    @JsonIgnore
    OlapSchemaInfo getOlapSchemaInfo();

    DbSchema createModuleDbSchema(DbScope scope, String metaDataName, Map<String, SchemaTableInfoFactory> tableInfoFactoryMap);

    /**
     * Lets a Module map a schema name used in code to a different schema name in the database. For example, Argos uses this to provide
     * an administrator-configurable schema name; code always references "caisis" but administrators can point Argos to a different
     * database schema.
     *
     * @param fullyQualifiedSchemaName The name passed into DbSchema.get()
     * @return The actual schema name in the database
     */
    String getDatabaseSchemaName(String fullyQualifiedSchemaName);

    /**
     * Called by ModuleLoader to lock module info after construction.
     */
    void lock();

}
