/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.assay.DefaultAssaySaveHandler;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.springframework.validation.BindException;

/**
 * User: jeckels
 * Date: Jan 15, 2009
 */
@RequiresPermission(ReadPermission.class)
public class GetAssayBatchAction extends BaseProtocolAPIAction<SimpleApiJsonForm>
{
    @Override
    public ApiResponse executeAction(ExpProtocol protocol, SimpleApiJsonForm form, BindException errors)
    {
        ExpExperiment batch = null;
        if (form.getJsonObject().has(AssayJSONConverter.BATCH_ID))
        {
            int batchId = form.getJsonObject().getInt(AssayJSONConverter.BATCH_ID);
            batch = DefaultAssaySaveHandler.lookupBatch(getContainer(), batchId);
        }

        return AssayJSONConverter.serializeResult(getAssayProvider(), protocol, batch, getUser());
    }
}