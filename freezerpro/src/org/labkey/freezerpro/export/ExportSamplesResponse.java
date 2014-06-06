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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.JsonUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.JsonUtil.expectArrayStart;

/**
 * Created by klum on 5/21/2014.
 */
public class ExportSamplesResponse extends FreezerProCommandResonse
{
    public static final String DATA_NODE_NAME = "Samples";

    public ExportSamplesResponse(String text, int statusCode, PipelineJob job)
    {
        super(text, statusCode, DATA_NODE_NAME, job);
    }
}
