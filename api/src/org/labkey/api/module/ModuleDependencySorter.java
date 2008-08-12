/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.module;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.labkey.common.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: Jun 6, 2006
 */
public class ModuleDependencySorter
{
    public List<ModuleMetaData> sortModulesByDependencies(List<ModuleMetaData> moduleMetaDatas)
    {
        List<Pair<ModuleMetaData, Set<String>>> dependencies = new ArrayList<Pair<ModuleMetaData, Set<String>>>();
        for (ModuleMetaData module : moduleMetaDatas)
        {
            Pair<ModuleMetaData, Set<String>> dependencyInfo = new Pair<ModuleMetaData, Set<String>>(module, new HashSet<String>(module.getModuleDependencies()));
            dependencies.add(dependencyInfo);
        }

        List<ModuleMetaData> result = new ArrayList<ModuleMetaData>();
        while (!dependencies.isEmpty())
        {
            ModuleMetaData module = findModuleWithoutDependencies(dependencies);
            result.add(module);
            for (Pair<ModuleMetaData, Set<String>> dependencyInfo : dependencies)
            {
                dependencyInfo.getValue().remove(module.getName());
            }
        }

        // Core is special and needs to be first
        for (int i = 0; i < result.size(); i++)
        {
            ModuleMetaData module = result.get(i);
            if (module.getName().toLowerCase().equals("core"))
            {
                result.remove(i);
                result.add(0, module);
                break;
            }
        }

        return result;
    }

    private ModuleMetaData findModuleWithoutDependencies(List<Pair<ModuleMetaData, Set<String>>> dependencies)
    {
        for (int i = 0; i < dependencies.size(); i++)
        {
            Pair<ModuleMetaData, Set<String>> dependencyInfo = dependencies.get(i);
            if (dependencyInfo.getValue().isEmpty())
            {
                dependencies.remove(i);
                return dependencyInfo.getKey();
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Pair<ModuleMetaData, Set<String>> dependencyInfo : dependencies)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append(dependencyInfo.getKey().getName());
        }

        throw new IllegalArgumentException("Unable to resolve module dependencies. The following modules are somehow involved: " + sb.toString());
    }


    public static class TestCase extends junit.framework.TestCase
    {
        public void testSimpleCyclicalDependency()
        {
            try
            {
                List<ModuleMetaData> testModules = new ArrayList<ModuleMetaData>();
                testModules.add(new ModuleMetaData("a", "b"));
                testModules.add(new ModuleMetaData("b", "a"));
                ModuleDependencySorter sorter = new ModuleDependencySorter();
                sorter.sortModulesByDependencies(testModules);
                fail("Should have detected a problem");
            }
            catch (IllegalArgumentException e) { /* Expected failure */ }
        }

        public void testSelfReferral()
        {
            try
            {
                List<ModuleMetaData> testModules = new ArrayList<ModuleMetaData>();
                testModules.add(new ModuleMetaData("a", "a"));
                ModuleDependencySorter sorter = new ModuleDependencySorter();
                sorter.sortModulesByDependencies(testModules);
                fail("Should have detected a problem");
            }
            catch (IllegalArgumentException e) { /* Expected failure */ }
        }

        public void testUnsatisfiedDependency()
        {
            try
            {
                List<ModuleMetaData> testModules = new ArrayList<ModuleMetaData>();
                testModules.add(new ModuleMetaData("a", "b"));
                testModules.add(new ModuleMetaData("b", "c"));
                testModules.add(new ModuleMetaData("d", "e"));
                testModules.add(new ModuleMetaData("e"));
                ModuleDependencySorter sorter = new ModuleDependencySorter();
                sorter.sortModulesByDependencies(testModules);
                fail("Should have detected a problem");
            }
            catch (IllegalArgumentException e) { /* Expected failure */ }
        }

        public void testGoodDependencies()
        {
            List<ModuleMetaData> testModules = new ArrayList<ModuleMetaData>();
            testModules.add(new ModuleMetaData("a", "b"));
            testModules.add(new ModuleMetaData("b", "c", "d"));
            testModules.add(new ModuleMetaData("c", "h"));
            testModules.add(new ModuleMetaData("d", "e"));
            testModules.add(new ModuleMetaData("e"));
            testModules.add(new ModuleMetaData("f", "g"));
            testModules.add(new ModuleMetaData("g"));
            testModules.add(new ModuleMetaData("h"));
            ModuleDependencySorter sorter = new ModuleDependencySorter();
            List<ModuleMetaData> sortedModules = sorter.sortModulesByDependencies(testModules);
            assertEquals(sortedModules.size(), testModules.size());
            assertEquals(sortedModules.get(0).getName(), "e");
            assertEquals(sortedModules.get(1).getName(), "d");
            assertEquals(sortedModules.get(2).getName(), "g");
            assertEquals(sortedModules.get(3).getName(), "f");
            assertEquals(sortedModules.get(4).getName(), "h");
            assertEquals(sortedModules.get(5).getName(), "c");
            assertEquals(sortedModules.get(6).getName(), "b");
            assertEquals(sortedModules.get(7).getName(), "a");
        }

        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
    }
}
