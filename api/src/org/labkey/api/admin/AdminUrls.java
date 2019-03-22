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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

/**
 * User: jgarms
 * Date: Jan 27, 2008
 */
public interface AdminUrls extends UrlProvider
{
    ActionURL getModuleErrorsURL(Container container);
    ActionURL getAdminConsoleURL();
    ActionURL getModuleStatusURL(URLHelper returnURL);
    ActionURL getCustomizeSiteURL();
    ActionURL getCustomizeSiteURL(boolean upgradeInProgress);
    ActionURL getExperimentalFeaturesURL();
    ActionURL getMaintenanceURL(URLHelper returnURL);

    // URLs to key Folder Management tabs
    ActionURL getManageFoldersURL(Container c);
    ActionURL getMissingValuesURL(Container c);
    ActionURL getModulePropertiesURL(Container c);
    ActionURL getNotificationsURL(Container c);
    ActionURL getExportFolderURL(Container c);
    ActionURL getImportFolderURL(Container c);
    ActionURL getFolderSettingsURL(Container c);
    ActionURL getFileRootsURL(Container c);

    ActionURL getCreateProjectURL(@Nullable ActionURL returnURL);
    ActionURL getCreateFolderURL(Container c, @Nullable ActionURL returnURL);
    ActionURL getMemTrackerURL();
    ActionURL getProjectSettingsURL(Container c);
    ActionURL getProjectSettingsMenuURL(Container c);
    ActionURL getProjectSettingsFileURL(Container c);
    ActionURL getCustomizeEmailURL(Container c, Class<? extends EmailTemplate> selectedTemplate, URLHelper returnURL);
    ActionURL getFilesSiteSettingsURL(boolean upgrade);
    ActionURL getSessionLoggingURL();
    ActionURL getTrackedAllocationsViewerURL();

    NavTree appendAdminNavTrail(NavTree root, String childTitle, @Nullable ActionURL childURL);
}
