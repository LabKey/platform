/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen.report;

import org.labkey.api.query.FieldKey;

public class SpecimenTypeBeanProperty
{
    private final FieldKey _typeKey;
    private final String _beanProperty;
    private final SpecimenTypeLevel _level;

    public SpecimenTypeBeanProperty(FieldKey typeKey, String beanProperty, SpecimenTypeLevel level)
    {
        _typeKey = typeKey;
        _beanProperty = beanProperty;
        _level = level;
    }

    public FieldKey getTypeKey()
    {
        return _typeKey;
    }

    public String getBeanProperty()
    {
        return _beanProperty;
    }

    public SpecimenTypeLevel getLevel()
    {
        return _level;
    }
}
