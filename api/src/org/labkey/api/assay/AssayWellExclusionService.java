/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;
import org.labkey.api.assay.query.ResultsQueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;

import java.util.Set;

public interface AssayWellExclusionService
{
    AssayWellExclusionService[] _providers = new AssayWellExclusionService[1];

    static void registerProvider(AssayWellExclusionService provider)
    {
        if (_providers[0] == null)
            _providers[0] = provider;
        else
            throw new RuntimeException("An Assay Well Exclusion Provider has already been registered");
    }

    @Nullable
    static AssayWellExclusionService getProvider(ExpProtocol protocol)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider != null && provider.isExclusionSupported())
            return _providers[0];
        else
            return null;
    }

    static AssayWellExclusionService getProvider(AssayProvider provider)
    {
        if (provider != null && provider.isExclusionSupported())
            return _providers[0];
        else
            return null;
    }

    @Nullable
    MutableColumnInfo createExcludedColumn(TableInfo tinfo, AssayProvider provider);
    @Nullable
    MutableColumnInfo createExcludedByColumn(TableInfo tinfo, AssayProvider provider);
    @Nullable
    MutableColumnInfo createExcludedAtColumn(TableInfo tinfo, AssayProvider provider);
    @Nullable
    MutableColumnInfo createExclusionCommentColumn(TableInfo tinfo, AssayProvider provider);

    void createExclusionEvent(ExpRun run, Set<String> rowIds, String comment, User user, Container container);

    void deleteExclusionsForRun(ExpProtocol protocol, int runId);

    int getExclusionCount(ExpRun run);

    @Nullable
    ResultsQueryView.ResultsDataRegion createResultsDataRegion(ExpProtocol protocol);

    ActionURL getExclusionReportURL(Container container, ExpRun run);
    ActionURL getExclusionURL(Container container, AssayProvider provider, int rowId, String runId, String returnUrl);

    @Nullable
    // returns a view containing warnings if the specified run has current well exclusions associated with it
    HttpView getAssayReImportWarningView(Container container, ExpRun run);
}
