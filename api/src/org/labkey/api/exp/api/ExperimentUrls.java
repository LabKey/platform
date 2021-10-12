/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

/**
 * User: jeckels
 * Date: Jan 27, 2008
 */
public interface ExperimentUrls extends UrlProvider
{
    static ExperimentUrls get()
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class);
    }

    default ActionURL getRunGraphURL(ExpRun run) { return null; }
    default ActionURL getRunGraphURL(Container c, int rowId) { return null; }

    default ActionURL getRunGraphDetailURL(ExpRun run, @Nullable ExpData focus) { return null; }

    default ActionURL getRunTextURL(Container c, int rowId) { return null; }
    default ActionURL getRunTextURL(ExpRun run) { return null; }

    default ActionURL getDeleteProtocolURL(@NotNull ExpProtocol protocol, URLHelper returnURL) { return null; }

    default ActionURL getDeleteExperimentsURL(Container container, URLHelper returnURL) { return null; }

    default ActionURL getDeleteDatasURL(Container container, URLHelper returnURL) { return null; }

    default ActionURL getExperimentDetailsURL(Container c, ExpExperiment expExperiment) { return null; }

    default ActionURL getRemoveSelectedExpRunsURL(Container container, URLHelper returnURL, ExpExperiment expExperiment) { return null; }

    default ActionURL getExportProtocolURL(Container container, ExpProtocol protocol) { return null; }

    default ActionURL getProtocolDetailsURL(ExpProtocol protocol) { return null; }

    default ActionURL getProtocolApplicationDetailsURL(ExpProtocolApplication app) { return null; }

    default ActionURL getMoveRunsLocationURL(Container container) { return null; }

    default ActionURL getDeleteSelectedExpRunsURL(Container container, URLHelper returnURL) { return null; }

    default ActionURL getCreateRunGroupURL(Container container, URLHelper returnURL, boolean addSelectedRuns) { return null; }

    default ActionURL getShowRunsURL(Container c, ExperimentRunType type) { return null; }

    default ActionURL getAddRunsToExperimentURL(Container container, ExpExperiment expExperiment) { return null; }

    default ActionURL getDomainEditorURL(Container container, String domainURI, boolean createOrEdit) { return null; }

    default ActionURL getDomainEditorURL(Container container, Domain domain) { return null; }

    default ActionURL getCreateDataClassURL(Container c) { return null; }

    default ActionURL getShowDataClassURL(Container container, int rowId) { return null; }

    default ActionURL getShowFileURL(ExpData data, boolean inline) { return null; }

    default ActionURL getMaterialDetailsURL(ExpMaterial material) { return null; }

    default ActionURL getMaterialDetailsURL(Container c, int materialRowId) { return null; }

    default ActionURL getMaterialDetailsBaseURL(Container c, @Nullable String materialIdFieldKey) { return null; }

    default ActionURL getCreateSampleTypeURL(Container c) { return null; }

    default ActionURL getImportSamplesURL(Container c, String sampleTypeName) { return null; }

    default ActionURL getImportDataURL(Container c, String dataClassName) { return null; }

    default ActionURL getDataDetailsURL(ExpData data) { return null; }

    default ActionURL getShowFileURL(Container container) { return null; }

    default ActionURL getSetFlagURL(Container container) { return null; }

    default ActionURL getShowSampleTypeListURL(Container container) { return null; }

    default ActionURL getShowSampleTypeURL(ExpSampleType sampleType) { return null; }

    default ActionURL getShowSampleTypeURL(ExpSampleType sampleType, Container container) { return null; }

    default ActionURL getDataClassListURL(Container container) { return null; }

    default ActionURL getShowRunGraphURL(ExpRun run) { return null; }

    default ActionURL getUploadXARURL(Container container) { return null; }

    default ActionURL getRepairTypeURL(Container container) { return null; }

    default ActionURL getUpdateMaterialQueryRowAction(Container c, TableInfo table) { return null; }

    default ActionURL getInsertMaterialQueryRowAction(Container c, TableInfo table) { return null; }

}
