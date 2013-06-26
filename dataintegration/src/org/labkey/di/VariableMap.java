/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.di;

import org.labkey.api.data.ParameterDescription;

import java.util.Set;

/**
 * User: matthew
 * Date: 4/22/13
 * Time: 11:43 AM
 */
public interface VariableMap
{
    enum Scope { global, local, parent }

    Object get(String key);

    Object put(String key, Object value);
    Object put(String key, Object value, Enum scope);
    Object put(ParameterDescription p, Object value);
    Object put(ParameterDescription p, Object value, Enum scope);

    ParameterDescription getDescriptor(String key);
    Set<String> keySet();
}
