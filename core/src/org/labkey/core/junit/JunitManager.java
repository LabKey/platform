/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

package org.labkey.core.junit;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/*
* User: adam
* Date: Jul 4, 2009
* Time: 10:34:22 PM
*/
public class JunitManager
{
    private static final Map<String, List<Class>> _testCases;

    static
    {
        Set<Class> allCases = new HashSet<>();

        _testCases = new TreeMap<>();

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            List<Class> moduleClazzes = new ArrayList<>();

            for (Class clazz : module.getIntegrationTests())
            {
                if (allCases.contains(clazz))
                    continue;

                allCases.add(clazz);
                moduleClazzes.add(clazz);
            }

            for (Class clazz : module.getUnitTests())
            {
                if (allCases.contains(clazz))
                    continue;

                allCases.add(clazz);
                moduleClazzes.add(clazz);
            }

            if (!moduleClazzes.isEmpty())
            {
                moduleClazzes.sort(Comparator.comparing(Class::getName));

                _testCases.put(module.getName(), moduleClazzes);
            }
        }
    }

    public static Map<String, List<Class>> getTestCases()
    {
        return _testCases;
    }
}
