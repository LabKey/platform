/*
 * Copyright (c) 2009 LabKey Corporation
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

import junit.framework.TestCase;

import java.util.*;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;

/*
* User: adam
* Date: Jul 4, 2009
* Time: 10:34:22 PM
*/
public class JunitManager
{
    private static final Map<String, List<Class<? extends TestCase>>> _testCases;

    static
    {
        Set<Class<? extends TestCase>> allCases = new HashSet<Class<? extends TestCase>>();

        _testCases = new TreeMap<String, List<Class<? extends TestCase>>>();

        for (Module module : ModuleLoader.getInstance().getModules())
        {
            Set<Class<? extends TestCase>> clazzes = module.getJUnitTests();
            List<Class<? extends TestCase>> moduleClazzes = new ArrayList<Class<? extends TestCase>>();

            for (Class<? extends TestCase> clazz : clazzes)
            {
                if (allCases.contains(clazz))
                    continue;

                allCases.add(clazz);
                moduleClazzes.add(clazz);
            }

            if (!moduleClazzes.isEmpty())
            {
                Collections.sort(moduleClazzes, new Comparator<Class<? extends TestCase>>(){
                    public int compare(Class<? extends TestCase> c1, Class<? extends TestCase> c2)
                    {
                        return c1.getName().compareTo(c2.getName());
                    }
                });

                _testCases.put(module.getName(), moduleClazzes);
            }
        }
    }

    public static Map<String, List<Class<? extends TestCase>>> getTestCases()
    {
        return _testCases;
    }
}
