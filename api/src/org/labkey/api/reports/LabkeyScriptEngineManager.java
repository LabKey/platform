/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.reports;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.*;

/*
* User: Karl Lum
* Date: Dec 12, 2008
* Time: 12:52:28 PM
*/
public class LabkeyScriptEngineManager extends ScriptEngineManager
{
    private static final String SCRIPT_ENGINE_MAP = "ExternalScriptEngineMap";
    private static final String ENGINE_DEF_MAP_PREFIX = "ScriptEngineDefinition_";

    enum Props {
        key,
        name,
        extensions,
        languageName,
        languageVersion,
        exePath,
        exeCommand,
        outputFileName,
    }

    @Override
    public ScriptEngine getEngineByName(String shortName)
    {
        ScriptEngine engine = super.getEngineByName(shortName);

        if (engine == null)
        {
            for (ExternalScriptEngineDefinition def : getEngineDefinitions())
            {
                if (shortName.equals(def.getName()))
                {
                    ScriptEngineFactory factory = new ExternalScriptEngineFactory(def);
                    return factory.getScriptEngine();
                }
            }
        }
        return engine;
    }

    @Override
    public List<ScriptEngineFactory> getEngineFactories()
    {
        List<ScriptEngineFactory> factories = new ArrayList(super.getEngineFactories());
        for (ExternalScriptEngineDefinition def : getEngineDefinitions())
        {
            factories.add(new ExternalScriptEngineFactory(def));
        }
        return Collections.unmodifiableList(factories);
    }

    @Override
    public ScriptEngine getEngineByExtension(String extension)
    {
        ScriptEngine engine = super.getEngineByExtension(extension);

        if (engine == null)
        {
            for (ExternalScriptEngineDefinition def : getEngineDefinitions())
            {
                if (Arrays.asList(def.getExtensions()).contains(extension))
                {
                    ScriptEngineFactory factory = new ExternalScriptEngineFactory(def);
                    return factory.getScriptEngine();
                }
            }
        }
        return engine;
    }

    public static List<ExternalScriptEngineDefinition> getEngineDefinitions()
    {
        List<ExternalScriptEngineDefinition> engines = new ArrayList<ExternalScriptEngineDefinition>();
        Map<String, String> map = PropertyManager.getProperties(ContainerManager.getRoot().getId(), SCRIPT_ENGINE_MAP, false);

        if (map != null)
        {
            for (String name : map.values())
            {
                Map<String, String> def = PropertyManager.getProperties(ContainerManager.getRoot().getId(), name, false);
                if (def != null)
                {
                    try {
                        engines.add(createDefinition(def));
                    }
                    catch (Exception e)
                    {
                        deleteDefinition(name);
                    }
                }
            }
        }
        return engines;
    }

    public static ExternalScriptEngineDefinition createDefinition()
    {
        return new EngineDefinition();
    }

    public static void deleteDefinition(ExternalScriptEngineDefinition def)
    {
        if (def instanceof EngineDefinition)
        {
            deleteDefinition(((EngineDefinition)def).getKey());
        }
        else
            throw new IllegalArgumentException("Engine definition must be an instance of LabkeyScriptEngineManager.EngineDefinition");
    }

    private static void deleteDefinition(String key)
    {
        if (key != null)
        {
            Map<String, String> engines = PropertyManager.getWritableProperties(0, ContainerManager.getRoot().getId(), SCRIPT_ENGINE_MAP, false);
            if (engines != null && engines.containsKey(key))
            {
                engines.remove(key);
                PropertyManager.saveProperties(engines);

                Map<String, String> definition = PropertyManager.getWritableProperties(0, ContainerManager.getRoot().getId(), key, false);
                if (definition != null)
                {
                    definition.clear();
                    PropertyManager.saveProperties(definition);
                }
            }
        }
    }

    public static ExternalScriptEngineDefinition saveDefinition(ExternalScriptEngineDefinition def)
    {
        if (def instanceof EngineDefinition)
        {
            String key = ((EngineDefinition)def).getKey();

            if (key == null)
            {
                // new engine definition
                key = makeKey(def);
                if (getProp(key, SCRIPT_ENGINE_MAP) != null)
                    throw new IllegalArgumentException("An existing definition is already mapped to those file extensions");

                setProp(key, key, SCRIPT_ENGINE_MAP);
            }

            if (getProp(key, SCRIPT_ENGINE_MAP) != null)
            {
                setProp(Props.key.name(), key, key);
                setProp(Props.name.name(), def.getName(), key);
                setProp(Props.extensions.name(), StringUtils.join(def.getExtensions(), ','), key);
                setProp(Props.languageName.name(), def.getLanguageName(), key);
                setProp(Props.languageVersion.name(), def.getLanguageVersion(), key);
                setProp(Props.exePath.name(), def.getExePath(), key);
                setProp(Props.exeCommand.name(), def.getExeCommand(), key);
                setProp(Props.outputFileName.name(), def.getOutputFileName(), key);
            }
            else
                throw new IllegalArgumentException("Existing definition does not exist in the DB");
        }
        else
            throw new IllegalArgumentException("Engine definition must be an instance of LabkeyScriptEngineManager.EngineDefinition");
        return def;
    }

    private static String makeKey(ExternalScriptEngineDefinition def)
    {
        return ENGINE_DEF_MAP_PREFIX + StringUtils.join(def.getExtensions(), ',');
    }

    private static ExternalScriptEngineDefinition createDefinition(Map<String, String> props)
    {
        String key = props.get(Props.key.name());
        String name = props.get(Props.name.name());
        String exePath = props.get(Props.exePath.name());
        String extensionStr = props.get(Props.extensions.name());

        if (key != null && name != null && exePath != null && extensionStr != null)
        {
            try {
                String[] extensions = StringUtils.split(extensionStr, ',');
                EngineDefinition def = new EngineDefinition();

                BeanUtils.populate(def, props);
                def.setExtensions(extensions);

                return def;
            }
            catch (Exception e)
            {
                throw new RuntimeException("Unable to create an engine definition ", e);
            }
        }
        throw new IllegalArgumentException("Unable to create an engine definition from the specified properties : " + props);
    }

    private static String getProp(String prop, String mapName)
    {
        Map<String, String> map = PropertyManager.getProperties(ContainerManager.getRoot().getId(), mapName, false);
        if (map != null)
        {
            String ret = map.get(prop);
            if (!StringUtils.isEmpty(ret))
                return ret;
        }
        return null;
    }

    private static void setProp(String prop, String value, String mapName)
    {
        Map<String, String> map = PropertyManager.getWritableProperties(0, ContainerManager.getRoot().getId(), mapName, true);

        map.put(prop, value);
        PropertyManager.saveProperties(map);
    }

    public static class EngineDefinition implements ExternalScriptEngineDefinition
    {
        String _key;
        String _name;
        String[] _extensions;
        String _languageName;
        String _languageVersion;
        String _exePath;
        String _exeCommand;
        String _outputFileName;

        public String getKey()
        {
            return _key;
        }

        public void setKey(String key)
        {
            _key = key;
        }

        public String getName()
        {
            return _name;
        }

        public String[] getExtensions()
        {
            return _extensions;
        }

        public String getLanguageName()
        {
            return _languageName;
        }

        public String getLanguageVersion()
        {
            return _languageVersion;
        }

        public String getExePath()
        {
            return _exePath;
        }

        public String getExeCommand()
        {
            return _exeCommand;
        }

        public String getOutputFileName()
        {
            return _outputFileName;
        }

        public void setOutputFileName(String outputFileName)
        {
            _outputFileName = outputFileName;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public void setExtensions(String[] extensions)
        {
            _extensions = extensions;
        }

        public void setLanguageName(String languageName)
        {
            _languageName = languageName;
        }

        public void setLanguageVersion(String languageVersion)
        {
            _languageVersion = languageVersion;
        }

        public void setExePath(String exePath)
        {
            _exePath = exePath;
        }

        public void setExeCommand(String exeCommand)
        {
            _exeCommand = exeCommand;
        }
    }
}