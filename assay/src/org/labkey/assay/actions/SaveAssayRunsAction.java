/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.assay.actions;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentSaveHandler;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;

@RequiresPermission(InsertPermission.class)
public class SaveAssayRunsAction extends BaseProtocolAPIAction<SimpleApiJsonForm>
{
    @Override
    protected ApiResponse executeAction(ExpProtocol protocol, SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject rootJsonObject = form.getJsonObject();

        JSONArray runsJsonArray = null;

        if (rootJsonObject.has(AssayJSONConverter.RUNS))
            runsJsonArray = rootJsonObject.getJSONArray(AssayJSONConverter.RUNS);

        if (runsJsonArray == null)
            throw new IllegalArgumentException("No run array found.");

        if (runsJsonArray.isEmpty())
            throw new IllegalArgumentException("No runs provided. You must provide at least one run in your runs array.");

        ExperimentSaveHandler saveHandler = getExperimentSaveHandler(getAssayProvider());

        return executeAction(saveHandler, protocol, getAssayProvider(),runsJsonArray);
    }

    private ApiResponse executeAction(ExperimentSaveHandler saveHandler, ExpProtocol protocol, AssayProvider provider, JSONArray runsJsonArray) throws Exception
    {
        List<ExpRun> runs = new ArrayList<>();
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            for (int i = 0; i < runsJsonArray.length(); i++)
            {
                JSONObject runJsonObject = runsJsonArray.getJSONObject(i);
                runs.add(saveHandler.handleRunWithoutBatch(getViewContext(), runJsonObject, protocol));
            }

            transaction.commit();
        }
        return AssayJSONConverter.serializeRuns(provider, protocol, runs, getUser(), ExperimentJSONConverter.DEFAULT_SETTINGS);

    }
}
