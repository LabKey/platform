/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.di.data;

import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExperimentService;

//
// Placeholder for transform specific "data" used as inputs
// and outputs in transformation steps
//
public class TransformDataType extends DataType
{

    public static final String TRANSFORM_DATA_PREFIX = "Transform";

    public TransformDataType()
    {
        super(TRANSFORM_DATA_PREFIX);
    }

    static public void register()
    {
        ExperimentService.get().registerDataType(new TransformDataType());
    }
}
