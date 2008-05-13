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
    public List<Module> sortModulesByDependencies(List<Module> moduleList)
    {
        List<Pair<Module, Set<String>>> dependencies = new ArrayList<Pair<Module, Set<String>>>();
        for (Module module : moduleList)
        {
            Pair<Module, Set<String>> dependencyInfo = new Pair<Module, Set<String>>(module, new HashSet<String>(module.getModuleDependencies()));
            dependencies.add(dependencyInfo);
        }

        List<Module> result = new ArrayList<Module>();
        while (!dependencies.isEmpty())
        {
            Module module = findModuleWithoutDependencies(dependencies);
            result.add(module);
            for (Pair<Module, Set<String>> dependencyInfo : dependencies)
            {
                dependencyInfo.getValue().remove(module.getName());
            }
        }

        // Core is special and needs to be first
        for (int i = 0; i < result.size(); i++)
        {
            Module module = result.get(i);
            if (module.getName().toLowerCase().equals("core"))
            {
                result.remove(i);
                result.add(0, module);
                break;
            }
        }

        return result;
    }

    private Module findModuleWithoutDependencies(List<Pair<Module, Set<String>>> dependencies)
    {
        for (int i = 0; i < dependencies.size(); i++)
        {
            Pair<Module, Set<String>> dependencyInfo = dependencies.get(i);
            if (dependencyInfo.getValue().isEmpty())
            {
                dependencies.remove(i);
                return dependencyInfo.getKey();
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Pair<Module, Set<String>> dependencyInfo : dependencies)
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
                List<Module> testModules = new ArrayList<Module>();
                testModules.add(new DummyModule("a", "b"));
                testModules.add(new DummyModule("b", "a"));
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
                List<Module> testModules = new ArrayList<Module>();
                testModules.add(new DummyModule("a", "a"));
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
                List<Module> testModules = new ArrayList<Module>();
                testModules.add(new DummyModule("a", "b"));
                testModules.add(new DummyModule("b", "c"));
                testModules.add(new DummyModule("d", "e"));
                testModules.add(new DummyModule("e"));
                ModuleDependencySorter sorter = new ModuleDependencySorter();
                sorter.sortModulesByDependencies(testModules);
                fail("Should have detected a problem");
            }
            catch (IllegalArgumentException e) { /* Expected failure */ }
        }

        public void testGoodDependencies()
        {
            List<Module> testModules = new ArrayList<Module>();
            testModules.add(new DummyModule("a", "b"));
            testModules.add(new DummyModule("b", "c", "d"));
            testModules.add(new DummyModule("c", "h"));
            testModules.add(new DummyModule("d", "e"));
            testModules.add(new DummyModule("e"));
            testModules.add(new DummyModule("f", "g"));
            testModules.add(new DummyModule("g"));
            testModules.add(new DummyModule("h"));
            ModuleDependencySorter sorter = new ModuleDependencySorter();
            List<Module> sortedModules = sorter.sortModulesByDependencies(testModules);
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

    static class DummyModule extends DefaultModule
    {
        private Set<String> _dependencies = new HashSet<String>();

        public DummyModule(String name, String... dependencies)
        {
            super(name, 1.0, null, false);
            for (String dependency : dependencies)
            {
                _dependencies.add(dependency);
            }
        }

        public Set<String> getModuleDependencies()
        {
            return _dependencies;
        }
    }
}
