/*
 * Copyright (c) 2009-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * This is a utility class to help with validation/transformation of uploaded
 * data for module based assays.
 *
 * User: klum
 * Date: Apr 29, 2009
 */
public class ModuleRunUploadContext extends AssayRunUploadForm<ModuleAssayProvider>
{
    JSONObject _jsonObject;
    List<Map<String, Object>> _uploadedData;

    public ModuleRunUploadContext(ViewContext context, int protocolId, JSONObject jsonObject, List<Map<String, Object>> uploadedData)
    {
        _jsonObject = jsonObject;
        _uploadedData = uploadedData;

        setViewContext(context);
        setRowId(protocolId);
    }

    @Override
    public Map<DomainProperty, String> getRunProperties() throws ExperimentException
    {
        if (_runProperties == null)
        {
            AssayProvider provider = AssayService.get().getProvider(getProtocol());
            _runProperties = new HashMap<DomainProperty, String>();

            if (_jsonObject.has(ExperimentJSONConverter.PROPERTIES))
            {
                for (Map.Entry<DomainProperty, Object> entry : ExperimentJSONConverter.convertProperties(_jsonObject.getJSONObject(ExperimentJSONConverter.PROPERTIES),
                        provider.getRunDomain(getProtocol()).getProperties(), getContainer(), false).entrySet())
                {
                    _runProperties.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        return Collections.unmodifiableMap(_runProperties);
    }

    @Override
    public Map<DomainProperty, String> getBatchProperties()
    {
        if (_uploadSetProperties == null)
        {
            AssayProvider provider = AssayService.get().getProvider(getProtocol());
            _uploadSetProperties = new HashMap<DomainProperty, String>();

            if (_jsonObject.has(ExperimentJSONConverter.PROPERTIES))
            {
                for (Map.Entry<DomainProperty, Object> entry : ExperimentJSONConverter.convertProperties(_jsonObject.getJSONObject(ExperimentJSONConverter.PROPERTIES),
                        provider.getBatchDomain(getProtocol()).getProperties(), getContainer(), false).entrySet())
                {
                    _uploadSetProperties.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        return Collections.unmodifiableMap(_uploadSetProperties);
    }

    public List<Map<String, Object>> getRawData()
    {
        return _uploadedData;
    }
}
