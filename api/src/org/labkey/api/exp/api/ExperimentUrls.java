/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.URLHelper;

import javax.servlet.http.HttpServletRequest;

/**
 * User: jeckels
 * Date: Jan 27, 2008
 */
public interface ExperimentUrls extends UrlProvider
{
    ActionURL getRunGraphURL(ExpRun run);
    ActionURL getRunGraphURL(Container c, int rowId);

    ActionURL getRunTextURL(Container c, int rowId);
    ActionURL getRunTextURL(ExpRun run);

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

    ActionURL getDomainEditorURL(Container container, int domainId);

    ActionURL getShowFileURL(Container container, ExpData data, boolean inline);

    ActionURL getMaterialDetailsURL(ExpMaterial material);

    ActionURL getDataDetailsURL(ExpData data);

    ActionURL getShowFileURL(Container container);

    ActionURL getSetFlagURL(Container container);

    ActionURL getShowSampleSetListURL(Container container);

    ActionURL getShowSampleSetURL(ExpSampleSet sampleSet);

    ActionURL getShowRunGraphURL(ExpRun run);

    ActionURL getUploadXARURL(Container container);
}
