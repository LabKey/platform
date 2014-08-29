/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.di.steps;

import java.util.HashMap;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 8/7/2014
 */
public abstract class StepProviderImpl implements StepProvider
{
    @Override
    public Map<String, StepProvider> getNameProviderMap()
    {
        Map<String, StepProvider> map = new HashMap<>();
        map.put(getName(), this);
        for (String legacyName : getLegacyNames())
        {
            map.put(legacyName, this);
        }

        return map;
    }
}
