/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.api.admin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.springframework.web.servlet.mvc.Controller;

public interface AdminUrls extends UrlProvider
{
    ActionURL getModuleErrorsURL();
    ActionURL getAdminConsoleURL();
    ActionURL getModuleStatusURL(URLHelper returnURL);
    ActionURL getCustomizeSiteURL();
    ActionURL getCustomizeSiteURL(boolean upgradeInProgress);
    ActionURL getMaintenanceURL(URLHelper returnURL);
    ActionURL getModulesDetailsURL();
    ActionURL getDeleteModuleURL(String moduleName);

    // URLs to key Folder Management tabs
    ActionURL getManageFoldersURL(Container c);
    ActionURL getFolderTypeURL(Container c);
    ActionURL getMissingValuesURL(Container c);
    ActionURL getModulePropertiesURL(Container c);
    ActionURL getNotificationsURL(Container c);
    ActionURL getExportFolderURL(Container c);
    ActionURL getImportFolderURL(Container c);
    ActionURL getFileRootsURL(Container c);

    /**
     * Get the appropriate settings page for the passed in container (root, project, or folder)
     */
    ActionURL getLookAndFeelSettingsURL(Container c);
    ActionURL getSiteLookAndFeelSettingsURL();
    ActionURL getProjectSettingsURL(Container c);
    ActionURL getProjectSettingsMenuURL(Container c);
    ActionURL getProjectSettingsFileURL(Container c);
    ActionURL getFolderSettingsURL(Container c);

    ActionURL getCreateProjectURL(@Nullable ActionURL returnURL);
    ActionURL getCreateFolderURL(Container c, @Nullable ActionURL returnURL);
    ActionURL getMemTrackerURL();
    ActionURL getCustomizeEmailURL(Container c, Class<? extends EmailTemplate> selectedTemplate, URLHelper returnURL);
    ActionURL getFilesSiteSettingsURL(boolean upgrade);
    ActionURL getSessionLoggingURL();
    ActionURL getTrackedAllocationsViewerURL();
    ActionURL getSystemMaintenanceURL();

    /**
     * Simply adds an "Admin Console" link to nav trail if invoked in the root container. Otherwise, root is unchanged.
     */
    void addAdminNavTrail(NavTree root, @NotNull Container container);

    /**
     * Adds an "Admin Console" link to the nav trail if invoked in the root container. In all cases, adds childTitle
     * that links to the action. This ensures appropriate and consistent navtrails whether the action is invoked in the
     * root or elsewhere.
     */
    void addAdminNavTrail(NavTree root, String childTitle, @NotNull Class<? extends Controller> action, @NotNull Container container);

    /**
     * Adds "Admin Console / Modules" links to the nav trail if invoked in the root container. Otherwise, just displays
     * childTitle.
     */
    void addModulesNavTrail(NavTree root, String childTitle, @NotNull Container container);
}
