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
package org.labkey.api.module;

import junit.framework.TestCase;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.common.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.servlet.ServletContext;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * User: migra
 * Date: Jul 14, 2005
 * Time: 11:43:45 AM
 */
public interface Module
{
    public enum TabDisplayMode
    {
        DISPLAY_NEVER,
        DISPLAY_USER_PREFERENCE,
        DISPLAY_USER_PREFERENCE_DEFAULT,
        DISPLAY_FOLDER_TYPE
    }

    /**
     * Perform any post-constructor initialization
     */
    public void initialize();

    /**
     * Name of this module
     */
    String getName();

    /**
     * Name to show on the tab in the UI
     */
    public String getTabName(ViewContext context);

    /**
     * Return the version of this module. Allows us to track whether
     * module's version has changed.
     */
    double getVersion();

    // Formatted version number for display purposes.
    String getFormattedVersion();

    // Called on every module in REVERSE dependency order before versionUpdate() is called, as long as at least one module
    // requires updating.  This is a fine place to drop views and other dependent objects.
    public void beforeUpdate(ModuleContext moduleContext);

    /**
     * Do any version updating module needs to do.
     * <p/>
     * At installation time, ModuleContext.getInstalledVersion() will be 0.0
     * <p/>
     */
    public void versionUpdate(ModuleContext moduleContext) throws Exception;

    // Called on each module in dependency order after versionUpdate(), as long as at least one module requires
    // updating.  This is a fine place to create views and other dependent objects.
    public void afterUpdate(ModuleContext moduleContext);

    //TODO: Spring ApplicationContext might be good here

    /**
     * The application is starting. Version updating is complete. startup() has been called on all dependencies.
     */
    public void startup(ModuleContext moduleContext);

    /**
     * The application is shutting down "gracefully". Module
     * should do any cleanup (file handles etc) that is required.
     * Note: There is no guarantee that this will run if the server
     * process is without a nice shutdown.
     */
    public void destroy();

    /**
     * Return Collection of WebPartFactory objects for this module.
     * NOTE: This may be called before startup, but will never be called
     * before upgrade is complete.
     *
     * @return null if no web parts, otherwise Collection of WebPartFactory
     */
    public Collection<? extends WebPartFactory> getWebPartFactories();

    /**
     * @param c container in which the items would be stored
     * @return description of the objects that this module has stored in the container
     */
    public Collection<String> getSummary(Container c);

    /**
     * Returns a map of pageflow to controller class (for example, "wiki" -> WikiController) whose
     * functionality is considered part of this module.  All pageflows in the system
     * must be associated with one (and only one) module.
     *
     * @return A map of pageflow name to controller class
     */
    public Map<String, Class> getPageFlowNameToClass();

    public Map<Class, String> getPageFlowClassToName();

    /**
     * Returns the url that will be the target of a click on the module's tab.
     */
    public ActionURL getTabURL(Container c, User user);

    /**
     * @return under what conditions this module's tab should be shown in the UI
     */
    public TabDisplayMode getTabDisplayMode();

    /**
     * Modules can provide JUnit tests that will be run inside the server
     * VM. These tests will be executed as part of the DRT.
     * @return the unit tests that this module provides
     */
    public Set<Class<? extends TestCase>> getJUnitTests();

    /**
     * Returns a set of schemas that the module wants tested.
     * The DbSchema junit test calls this and ensures that the
     * the tables/views/columns described in schema XML match
     * those in the database metadata.
     * @return the schemas associated with this module that should be tested
     */
    public Set<DbSchema> getSchemasToTest();

    /**
     * Returns a set of schema names that the module owns.  Used to determine which module
     * should load the related resources.
     * @return the schema names owned by this module
     */
    public Set<String> getSchemaNames();

    public InputStream getResourceStreamFromWebapp(ServletContext ctx, String filename) throws FileNotFoundException;
    public InputStream getResourceStream(String filename) throws FileNotFoundException;
    public Pair<InputStream, Long> getResourceStreamIfChanged(String filename, long tsPrevious) throws FileNotFoundException;
    public String getSourcePath();
    public String getBuildPath();
    public String getSvnRevision();
    public String getSvnUrl();
    public Map<String, String> getProperties();
    public Set<String> getModuleDependenciesAsSet();

    /** Get a list of attributions (in HTML) that should be shown before users can log in. */
    public List<String> getAttributions();

    /**
     * Returns the exploded path for the module.
     * @return The path to the exploded module directory
     */
    public File getExplodedPath();

    /**
     * This is called by the module loader to let the module know where it's exploded path is
     * so that the module can later load resources.
     * @param path The path to the module's exploded directory
     */
    public void setExplodedPath(File path);

    /**
     * Returns a list of sql script file names for a given schema name,
     * or all sql script names if the schema name is null.
     * @param schemaName The schema name, or null
     * @param dialect The desired sql dialect
     * @return The list of sql script names
     */
    public Set<String> getSqlScripts(@Nullable String schemaName, @NotNull SqlDialect dialect);

    /**
     * Returns the file path for this modules sql scripts
     * @param dialect The sql dialect for the scripts
     * @return The script file path
     */
    public String getSqlScriptsPath(@NotNull SqlDialect dialect);

    /**
     * Returns a list of report descriptors for a given key
     * @param key The report key
     * @return A list of ReportDescriptors for that key
     */
    public List<ReportDescriptor> getReportDescriptors(String key);
}
