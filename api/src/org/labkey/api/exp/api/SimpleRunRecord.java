/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.labkey.api.exp.api.ExpMaterial;

import java.util.Map;

/**
 * Created by klum on 11/25/2015.
 */
public interface SimpleRunRecord
{
    public Map<ExpMaterial, String> getInputMaterialMap();
    public Map<ExpMaterial, String> getOutputMaterialMap();
    public Map<ExpData, String> getInputDataMap();
    public Map<ExpData, String> getOutputDataMap();
}
