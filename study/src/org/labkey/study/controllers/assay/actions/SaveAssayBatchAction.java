/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
package org.labkey.study.controllers.assay.actions;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssaySaveHandler;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Jan 14, 2009
 */
@RequiresPermission(InsertPermission.class)
@ApiVersion(9.1)
public class SaveAssayBatchAction extends AbstractAssayAPIAction<SimpleApiJsonForm>
{
    public ApiResponse executeAction(ExpProtocol protocol, AssayProvider provider, SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject rootJsonObject = form.getJsonObject();

        // A user can send in either an array of batches or just a batch but not both.  If a user sends in an array of batches
        // then it must have at least one batch
        JSONObject batchJsonObject = null;
        JSONArray batchesJsonArray = null;

        if (rootJsonObject.has(AssayJSONConverter.BATCH))
            batchJsonObject = rootJsonObject.getJSONObject(AssayJSONConverter.BATCH);

        if (rootJsonObject.has(AssayJSONConverter.BATCHES))
            batchesJsonArray = rootJsonObject.getJSONArray(AssayJSONConverter.BATCHES);

        if (batchJsonObject == null && batchesJsonArray == null)
            throw new IllegalArgumentException("No batch object or batches array found");

        if ((null != batchesJsonArray) && (batchesJsonArray.length() == 0))
            throw new IllegalArgumentException("You must provide at least one batch in your batches array");

        if ((null != batchJsonObject) && (null != batchesJsonArray))
            throw new IllegalArgumentException("You cannot specify both a batch object and a batches array");

        AssaySaveHandler saveHandler = provider.getSaveHandler();
        if (null == saveHandler)
            throw new IllegalArgumentException("SaveAssayBatch is not supported for assay provider: " + provider);

        if (null != batchJsonObject)
            return executeAction(saveHandler, protocol, provider, rootJsonObject, batchJsonObject);

        return executeAction(saveHandler, protocol, provider, rootJsonObject, batchesJsonArray);
    }

    private ApiResponse executeAction(AssaySaveHandler saveHandler, ExpProtocol protocol, AssayProvider provider,
                                      JSONObject rootJsonObject, JSONArray batchesJsonArray) throws Exception
    {
        saveHandler.beforeSave(getViewContext(), rootJsonObject, protocol);
        List<ExpExperiment> batches = new ArrayList<>(batchesJsonArray.length());
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            for (int i = 0; i < batchesJsonArray.length(); i++)
            {
                JSONObject batchJsonObject = batchesJsonArray.getJSONObject(i);
                batches.add(saveHandler.handleBatch(getViewContext(), batchJsonObject, protocol));
            }

            transaction.commit();
        }
        saveHandler.afterSave(getViewContext(), batches, protocol);
        return AssayJSONConverter.serializeResult(provider, protocol, batches, getUser());

    }

    private ApiResponse executeAction(AssaySaveHandler saveHandler, ExpProtocol protocol, AssayProvider provider,
                                      JSONObject rootJsonObject, JSONObject batchJsonObject) throws Exception
    {
        List<ExpExperiment> batches = new ArrayList<>(1);

        saveHandler.beforeSave(getViewContext(), rootJsonObject, protocol);
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            batches.add(saveHandler.handleBatch(getViewContext(), batchJsonObject, protocol));
            transaction.commit();
        }
        saveHandler.afterSave(getViewContext(), batches, protocol);
        return AssayJSONConverter.serializeResult(provider, protocol, batches.get(0), getUser());
    }
}

