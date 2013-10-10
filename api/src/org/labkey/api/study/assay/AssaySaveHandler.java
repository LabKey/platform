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

package org.labkey.api.study.assay;

import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.view.ViewContext;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * User: dax
 * Date: Oct 8, 2013
 */
public interface AssaySaveHandler
{
    void setProvider(AssayProvider provider);
    AssayProvider getProvider();

    ExpExperiment handleBatch(ViewContext context, JSONObject batchJson, ExpProtocol protocol) throws Exception;
    ExpRun handleRun(ViewContext context, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch) throws JSONException, ValidationException, ExperimentException, SQLException;
    ExpData handleData(ViewContext context, JSONObject dataJson) throws ValidationException;
    ExpMaterial handleMaterial(ViewContext context, JSONObject materialJson) throws ValidationException;
    void handleProperties(ViewContext context, ExpObject object, DomainProperty[] dps, JSONObject propertiesJson) throws ValidationException, JSONException;
    void importRows(ViewContext context, ExpData data, ExpRun run, ExpProtocol protocol, JSONObject runJson, List<Map<String, Object>> rawData) throws ExperimentException, ValidationException;
}
