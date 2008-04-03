/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
import org.apache.beehive.netui.pageflow.Forward;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartFactory;
import org.labkey.common.util.Pair;

import javax.servlet.ServletContext;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

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

    /**
     * Module has never been run before. Module should do anything that
     * it needs to do before controller gets called. This may be nothing,
     * but may involve setting up databases.
     * <p/>
     */
    public void bootstrap();

    /**
     * Do any version updating module needs to do. If module
     * requires a UI for updating version, the ActionURL for
     * the upgrade UI should be returned here. Do NOT return null.
     * <p/>
     * For initialization, versionUpdate will be called with an oldVersion of 0.0
     * <p/>
     * When version updating is successful
     * module must call moduleContext.upgradeComplete(ViewContext viewContext, double newVersion)
     * This will redirect to the appropriate location to continue the upgrade process.
     * Note that if the module wishes to set up its own upgrade UI it should redirect to that
     * UI and then when complete call this method.
     */
    public Forward versionUpdate(ModuleContext moduleContext, ViewContext viewContext);

    //TODO: Spring ApplicationContext might be good here

    /**
     * The application is starting. Bootstrap has already happened. Version updating
     * has already happened.
     * No predicatable order for startup calls though.
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
     * Return array of WebPartDescription objects for this module.
     * NOTE: This may be called before startup, but will never be called
     * before bootstrap.
     *
     * @return null if no web parts, otherwise array of WebPartDescription
     */
    public WebPartFactory[] getModuleWebParts();

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

    /**
     * Module dependencies ensure that the SQL scripts for modules
     * are run in the correct order. For example, in order for module
     * A to create a foreign key that refers to a table defined in
     * module B, it needs its scripts to run after the table has been created
     * @return the names of the modules on which this module depends
     */
    public Set<String> getModuleDependencies();

    public void setMetaData(Map<String, String> metaData);
    public Map<String, String> getMetaData();
    public InputStream getResourceStreamFromWebapp(ServletContext ctx, String filename) throws FileNotFoundException;
    public InputStream getResourceStream(String filename) throws FileNotFoundException;
    public Pair<InputStream, Long> getResourceStreamIfChanged(String filename, long tsPrevious) throws FileNotFoundException;
    public String getBuildPath();
}
