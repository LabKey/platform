/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.api.study;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

/**
 * User: Nick
 * Date: 7/22/11
 */
public interface StudyUrls extends UrlProvider
{
    ActionURL getCreateStudyURL(Container container);
    ActionURL getManageStudyURL(Container container);
    ActionURL getStudyOverviewURL(Container container);
    ActionURL getDatasetURL(Container container, int datasetId);
    ActionURL getDatasetsURL(Container container);
    ActionURL getManageDatasetsURL(Container container);
    ActionURL getManageReportPermissions(Container container);
    ActionURL getManageAssayScheduleURL(Container container, boolean useAlternateLookupFields);
    ActionURL getManageTreatmentsURL(Container container, boolean useSingleTableEditor);
    ActionURL getManageFileWatchersURL(Container container);
}
