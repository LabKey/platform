/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.assay.actions;

import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.assay.plate.PlateBasedAssayProvider;
import org.labkey.api.assay.plate.PlateSamplePropertyHelper;

import java.util.Map;

/**
 * User: klum
 * Date: 10/9/12
 */
public class PlateUploadFormImpl<ProviderType extends PlateBasedAssayProvider> extends AssayRunUploadForm<ProviderType> implements PlateUploadForm<ProviderType>
{
    private Map<String, Map<DomainProperty, String>> _sampleProperties;
    private PlateSamplePropertyHelper _samplePropertyHelper;


    @Override
    public PlateSamplePropertyHelper getSamplePropertyHelper()
    {
        return _samplePropertyHelper;
    }

    @Override
    public void setSamplePropertyHelper(PlateSamplePropertyHelper helper)
    {
        _samplePropertyHelper = helper;
    }

    public Map<String, Map<DomainProperty, String>> getSampleProperties()
    {
        return _sampleProperties;
    }

    public void setSampleProperties(Map<String, Map<DomainProperty, String>> sampleProperties)
    {
        _sampleProperties = sampleProperties;
    }
}
