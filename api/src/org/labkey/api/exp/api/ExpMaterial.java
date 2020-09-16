/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.security.User;

import java.util.Map;

/** Represents a physical object in the experiment data model - typically a sample or specimen */
public interface ExpMaterial extends ExpRunItem
{
    String DEFAULT_CPAS_TYPE = "Material";
    String MATERIAL_INPUT_PARENT = "MaterialInputs";
    String MATERIAL_OUTPUT_CHILD = "MaterialOutputs";

    @Nullable
    ExpSampleType getSampleType();

    Map<PropertyDescriptor, Object> getPropertyValues();

    /** @return the search document id for this material */
    String getDocumentId();

    /** Override to signal that we never throw BatchValidationExceptions */
    @Override
    void save(User user);
}
