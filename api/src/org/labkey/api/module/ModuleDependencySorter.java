/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.util.DotRunner;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Orders modules so that each module will always be after all of the modules it depends on.
 * User: jeckels
 * Date: Jun 6, 2006
 */
public class ModuleDependencySorter
{
    public List<Module> sortModulesByDependencies(List<Module> modules)
    {
        List<Pair<Module, Set<String>>> dependencies = new ArrayList<>();
        Set<String> moduleNames = new CaseInsensitiveHashSet();
        for (Module module : modules)
        {
            Pair<Module, Set<String>> dependencyInfo = new Pair<>(module, new CaseInsensitiveHashSet(module.getModuleDependenciesAsSet()));
            dependencies.add(dependencyInfo);
            moduleNames.add(module.getName());
        }

        for (Pair<Module, Set<String>> dependency : dependencies)
        {
            for (String dependencyName : dependency.getValue())
            {
                if (!moduleNames.contains(dependencyName))
                {
                    throw new IllegalArgumentException("Could not find module '" + dependencyName + "' on which module '" + dependency.getKey().getName() + "' depends");
                }
            }
        }

//  Uncomment this to generate an SVG graph of all module dependencies
//        graphModuleDependencies(dependencies, "all");

        List<Module> result = new ArrayList<>(modules.size());
        while (!dependencies.isEmpty())
        {
            Module module = findModuleWithoutDependencies(dependencies);
            result.add(module);
            String moduleName = module.getName();
            for (Pair<Module, Set<String>> dependencyInfo : dependencies)
            {
                dependencyInfo.getValue().remove(moduleName);
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

        for (Pair<Module, Set<String>> entry : dependencies)
        {
            String moduleName = entry.getKey().getName();
            if (entry.getValue().contains(moduleName))
                throw new IllegalArgumentException("Module '" + moduleName + "' (" + entry.getKey().getClass().getName() + ") is listed as being dependent on itself.");
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

        // Generate an SVG diagram that shows all remaining dependencies
        graphModuleDependencies(dependencies, "involved");

        throw new IllegalArgumentException("Unable to resolve module dependencies. The following modules are somehow involved: " + sb.toString());
    }


    private void graphModuleDependencies(List<Pair<Module, Set<String>>> dependencies, @SuppressWarnings("SameParameterValue") String adjective)
    {
        Logger log = Logger.getLogger(ModuleDependencySorter.class);

        try
        {
            File dir = FileUtil.getTempDirectory();
            String dot = buildDigraph(dependencies);
            File svgFile = File.createTempFile("modules", ".svg", dir);
            DotRunner runner = new DotRunner(dir, dot);
            runner.addSvgOutput(svgFile);
            runner.execute();

            log.info("For a diagram of " + adjective + " module dependencies, see " + svgFile.getAbsolutePath());
        }
        catch (Exception e)
        {
            log.error("Error running dot", e);
        }
    }


    private String buildDigraph(List<Pair<Module, Set<String>>> dependencies)
    {
        StringBuilder dot = new StringBuilder("digraph modules {\n");

        for (Pair<Module, Set<String>> dependencyInfo : dependencies)
        {
            String parent = dependencyInfo.getKey().getName().toLowerCase();
            Set<String> children = dependencyInfo.getValue();

            if (children.isEmpty())
            {
                dot.append("\t").append(parent).append(";\n");
            }
            else
            {
                for (String child : dependencyInfo.getValue())
                {
                    dot.append("\t").append(child.toLowerCase()).append(" -> ").append(parent).append(" [dir=back];\n");
                }
            }
        }

        dot.append("}");

        return dot.toString();
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testSimpleCyclicalDependency()
        {
            try
            {
                List<Module> testModules = new ArrayList<>();
                testModules.add(new MockModule("a", "b"));
                testModules.add(new MockModule("b", "a"));
                ModuleDependencySorter sorter = new ModuleDependencySorter();
                sorter.sortModulesByDependencies(testModules);
                fail("Should have detected a problem");
            }
            catch (IllegalArgumentException e) { /* Expected failure */ }
        }

        @Test
        public void testSelfReferral()
        {
            try
            {
                List<Module> testModules = new ArrayList<>();
                testModules.add(new MockModule("a", "a"));
                ModuleDependencySorter sorter = new ModuleDependencySorter();
                sorter.sortModulesByDependencies(testModules);
                fail("Should have detected a problem");
            }
            catch (IllegalArgumentException e) { /* Expected failure */ }
        }

        @Test
        public void testUnsatisfiedDependency()
        {
            try
            {
                List<Module> testModules = new ArrayList<>();
                testModules.add(new MockModule("a", "b"));
                testModules.add(new MockModule("b", "c"));
                testModules.add(new MockModule("d", "e"));
                testModules.add(new MockModule("e"));
                ModuleDependencySorter sorter = new ModuleDependencySorter();
                sorter.sortModulesByDependencies(testModules);
                fail("Should have detected a problem");
            }
            catch (IllegalArgumentException e) { /* Expected failure */ }
        }

        @Test
        public void testGoodDependencies()
        {
            List<Module> testModules = new ArrayList<>();
            testModules.add(new MockModule("a", "b"));
            testModules.add(new MockModule("b", "c", "d"));
            testModules.add(new MockModule("c", "h"));
            testModules.add(new MockModule("d", "e"));
            testModules.add(new MockModule("e"));
            testModules.add(new MockModule("f", "g"));
            testModules.add(new MockModule("g"));
            testModules.add(new MockModule("h"));
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
    }
}
