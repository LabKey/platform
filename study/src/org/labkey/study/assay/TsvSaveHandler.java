/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.study.assay;

import org.json.JSONObject;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.qc.DataTransformer;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.assay.DefaultAssaySaveHandler;
import org.labkey.api.view.ViewContext;

import java.util.List;
import java.util.Map;

/**
 * User: dax
 * Date: Oct 8, 2013
 * Time: 11:17:56 AM
 */
public class TsvSaveHandler extends DefaultAssaySaveHandler
{
    public TsvSaveHandler(TsvAssayProvider provider)
    {
        super(provider);
    }

    @Override
    public void importRows(ViewContext context, ExpData data, ExpRun run, ExpProtocol protocol, JSONObject runJson, List<Map<String, Object>> rawData) throws ExperimentException, ValidationException
    {
        // programmatic qc validation
        DataTransformer dataTransformer = _provider.getRunCreator().getDataTransformer();
        if (dataTransformer != null)
            dataTransformer.transformAndValidate(new ModuleRunUploadContext(context, protocol.getRowId(), runJson, rawData), run);

        TsvDataHandler dataHandler = new TsvDataHandler();
        dataHandler.setAllowEmptyData(true);
        dataHandler.importRows(data, context.getUser(), run, protocol, _provider, rawData);
    }
}
