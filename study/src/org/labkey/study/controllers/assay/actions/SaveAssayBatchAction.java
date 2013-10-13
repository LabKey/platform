/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssaySaveHandler;
import org.springframework.validation.BindException;

/**
 * User: jeckels
 * Date: Jan 14, 2009
 */
@RequiresPermissionClass(InsertPermission.class)
@ApiVersion(9.1)
public class SaveAssayBatchAction extends AbstractAssayAPIAction<SimpleApiJsonForm>
{
    public ApiResponse executeAction(ExpProtocol protocol, AssayProvider provider, SimpleApiJsonForm form, BindException errors) throws Exception
    {
        JSONObject rootJsonObject = form.getJsonObject();

        JSONObject batchJsonObject = rootJsonObject.getJSONObject(AssayJSONConverter.BATCH);
        if (batchJsonObject == null)
        {
            throw new IllegalArgumentException("No batch object found");
        }

        AssaySaveHandler saveHandler = provider.getSaveHandler();

        if (null == saveHandler)
        {
            throw new IllegalArgumentException("SaveAssayBatch is not supported for assay provider: " + provider);
        }

        ExpExperiment batch;

        saveHandler.beforeSave(getViewContext(), rootJsonObject, protocol);
        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            batch = saveHandler.handleBatch(getViewContext(), batchJsonObject, protocol);
            transaction.commit();
        }
        saveHandler.afterSave(getViewContext(), batch, protocol);
        return AssayJSONConverter.serializeResult(provider, protocol, batch, getViewContext().getUser());
    }
}

