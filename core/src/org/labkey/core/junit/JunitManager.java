/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
import java.util.Collections;
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
    public static synchronized Map<String, List<Class>> getTestCases()
    {
        Map<String, List<Class>> testCases = new TreeMap<>();

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            Set<Class> moduleClazzes = new HashSet<>();

            module.getIntegrationTestFactories().forEach(f -> moduleClazzes.add(f.create()));
            moduleClazzes.addAll(module.getUnitTests());

            if (!moduleClazzes.isEmpty())
            {
                List<Class> moduleClazzList = new ArrayList<>(moduleClazzes);
                moduleClazzList.sort(Comparator.comparing(Class::getName));

                testCases.put(module.getName(), Collections.unmodifiableList(moduleClazzList));
            }
        }
        return Collections.unmodifiableMap(testCases);
    }
}
