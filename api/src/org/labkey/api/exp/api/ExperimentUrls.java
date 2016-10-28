/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

/**
 * User: jeckels
 * Date: Jan 27, 2008
 */
public interface ExperimentUrls extends UrlProvider
{
    ActionURL getRunGraphURL(ExpRun run);
    ActionURL getRunGraphURL(Container c, int rowId);

    ActionURL getRunGraphDetailURL(ExpRun run, @Nullable ExpData focus);

    ActionURL getRunTextURL(Container c, int rowId);
    ActionURL getRunTextURL(ExpRun run);

    ActionURL getDeleteProtocolURL(@NotNull ExpProtocol protocol, URLHelper returnURL);

    ActionURL getDeleteExperimentsURL(Container container, URLHelper returnURL);

    ActionURL getDeleteDatasURL(Container container, URLHelper returnURL);

    ActionURL getExperimentDetailsURL(Container c, ExpExperiment expExperiment);

    ActionURL getRemoveSelectedExpRunsURL(Container container, URLHelper returnURL, ExpExperiment expExperiment);

    ActionURL getExportProtocolURL(Container container, ExpProtocol protocol);

    ActionURL getProtocolDetailsURL(ExpProtocol protocol);

    ActionURL getProtocolApplicationDetailsURL(ExpProtocolApplication app);

    ActionURL getMoveRunsLocationURL(Container container);

    ActionURL getDeleteSelectedExpRunsURL(Container container, URLHelper returnURL);

    ActionURL getCreateRunGroupURL(Container container, URLHelper returnURL, boolean addSelectedRuns);

    ActionURL getAddRunsToExperimentURL(Container container, ExpExperiment expExperiment);

    ActionURL getDomainEditorURL(Container container, String domainURI, boolean allowAttachmentProperties, boolean allowFileLinkProperties, boolean showDefaultValueSettings);

    ActionURL getShowDataClassURL(Container container, int rowId);

    ActionURL getShowFileURL(ExpData data, boolean inline);

    ActionURL getMaterialDetailsURL(ExpMaterial material);

    ActionURL getMaterialDetailsURL(Container c, int materialRowId);

    ActionURL getShowUploadMaterialsURL(Container container);

    ActionURL getDataDetailsURL(ExpData data);

    ActionURL getShowFileURL(Container container);

    ActionURL getSetFlagURL(Container container);

    ActionURL getShowSampleSetListURL(Container container);

    ActionURL getShowSampleSetURL(ExpSampleSet sampleSet);

    ActionURL getDataClassListURL(Container container);

    ActionURL getShowRunGraphURL(ExpRun run);

    ActionURL getUploadXARURL(Container container);

    ActionURL getRepairTypeURL(Container container);
}
