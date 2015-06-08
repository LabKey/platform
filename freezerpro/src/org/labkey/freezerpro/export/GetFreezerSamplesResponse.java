/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.freezerpro.export;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.freezerpro.FreezerProConfig;

/**
 * Created by klum on 10/28/2014.
 */
public class GetFreezerSamplesResponse extends FreezerProCommandResponse
{
    public static final String DATA_NODE_NAME = "Samples";

    public GetFreezerSamplesResponse(FreezerProExport export, String text, int statusCode, PipelineJob job)
    {
        super(export, text, statusCode, DATA_NODE_NAME, job);
    }

    @Override
    public String translateFieldName(String fieldName)
    {
        // special case for this API using obj_id and id as the sample id
        if ("obj_id".equalsIgnoreCase(fieldName))
            return FreezerProConfig.SAMPLE_ID_FIELD_NAME;
        else
            return super.translateFieldName(fieldName);
    }
}
