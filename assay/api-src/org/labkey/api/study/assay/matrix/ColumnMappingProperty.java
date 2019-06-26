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

package org.labkey.api.study.assay.matrix;

public class ColumnMappingProperty
{
    private final String _name;
    private final String _label;
    private final boolean _required;

    public ColumnMappingProperty(String name, String label, boolean required)
    {
        _name = name;
        _label = label;
        _required = required;
    }

    public String getName()
    {
        return _name;
    }

    public String getLabel()
    {
        return _label;
    }

    public boolean isRequired()
    {
        return _required;
    }
}
