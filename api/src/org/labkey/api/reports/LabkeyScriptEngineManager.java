/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.script.ScriptService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        disabled,
        machine,
        port,
        reportShare,
        pipelineShare,
        user,
        password
    }

    ScriptEngineFactory rhino = null;

    public LabkeyScriptEngineManager()
    {
        super();

        // replace the JDK bundled ScriptEngineFactory with the full, non-JDK version.
        rhino = ServiceRegistry.get().getService(ScriptService.class);
        assert rhino != null : "RhinoScriptEngineFactory not found";
        if (rhino != null)
        {
            for (String name : rhino.getNames())
                registerEngineName(name, rhino);

            for (String type : rhino.getMimeTypes())
                registerEngineMimeType(type, rhino);

            for (String extension : rhino.getExtensions())
                registerEngineExtension(extension, rhino);
        }
    }

    @Override
    public ScriptEngine getEngineByName(String shortName)
    {
        ScriptEngine engine = super.getEngineByName(shortName);
        assert engine == null || !engine.getClass().getSimpleName().equals("com.sun.script.javascript.RhinoScriptEngine") : "Should not use jdk bundled script engine";

        if (engine == null)
        {
            for (ExternalScriptEngineDefinition def : getEngineDefinitions())
            {
                if (def.isEnabled() && shortName.equals(def.getName()))
                {
                    ScriptEngineFactory factory = new ExternalScriptEngineFactory(def);
                    return factory.getScriptEngine();
                }
            }
        }
        else if (isFactoryEnabled(engine.getFactory()))
            return engine;

        return null;
    }

    @Override
    public List<ScriptEngineFactory> getEngineFactories()
    {
        List<ScriptEngineFactory> factories = new ArrayList<>();
        if (rhino != null)
            factories.add(rhino);
        for (ExternalScriptEngineDefinition def : getEngineDefinitions())
        {
            factories.add(new ExternalScriptEngineFactory(def));
        }
        return Collections.unmodifiableList(factories);
    }

    @Override
    public ScriptEngine getEngineByExtension(String extension)
    {
        if (!StringUtils.isBlank(extension))
        {
            ScriptEngine engine = super.getEngineByExtension(extension);
            assert engine == null || !engine.getClass().getSimpleName().equals("com.sun.script.javascript.RhinoScriptEngine") : "Should not use jdk bundled script engine";

            if (engine == null)
            {
                for (ExternalScriptEngineDefinition def : getEngineDefinitions())
                {
                    if (def.isEnabled() && Arrays.asList(def.getExtensions()).contains(extension))
                    {
                        ScriptEngineFactory factory;

                        if (RScriptEngineFactory.isRScriptEngine(def.getExtensions()))
                        {
                            if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
                                factory = new RserveScriptEngineFactory(def);
                            else
                                factory = new RScriptEngineFactory(def);
                        }
                        else
                            factory = new ExternalScriptEngineFactory(def);

                        return factory.getScriptEngine();
                    }
                }
            }
            else if (isFactoryEnabled(engine.getFactory()))
                return engine;
        }
        return null;
    }

    public static List<ExternalScriptEngineDefinition> getEngineDefinitions()
    {
        List<ExternalScriptEngineDefinition> engines = new ArrayList<>();
        Map<String, String> map = PropertyManager.getProperties(SCRIPT_ENGINE_MAP);

        for (String name : map.values())
        {
            Map<String, String> def = PropertyManager.getProperties(name);

            try
            {
                engines.add(createDefinition(def));
            }
            catch (Exception e)
            {
                deleteDefinition(name);
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
            Map<String, String> engines = PropertyManager.getWritableProperties(SCRIPT_ENGINE_MAP, false);
            if (engines != null && engines.containsKey(key))
            {
                engines.remove(key);
                PropertyManager.saveProperties(engines);

                Map<String, String> definition = PropertyManager.getWritableProperties(key, false);
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
            if (def.isExternal())
            {
                String key = ((EngineDefinition)def).getKey();

                if (key == null)
                {
                    // new engine definition
                    key = makeKey(def.getExtensions());
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

                    if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
                    {
                        setProp(Props.machine.name(), def.getMachine(), key);
                        setProp(Props.port.name(), String.valueOf(def.getPort()), key);
                        setProp(Props.reportShare.name(), def.getReportShare(), key);
                        setProp(Props.pipelineShare.name(), def.getPipelineShare(), key);
                        setProp(Props.user.name(), def.getUser(), key);
                        setProp(Props.password.name(), def.getPassword(), key);
                    }
                    else
                    {
                        setProp(Props.exePath.name(), def.getExePath(), key);
                        setProp(Props.exeCommand.name(), def.getExeCommand(), key);
                    }

                    setProp(Props.outputFileName.name(), def.getOutputFileName(), key);
                    setProp(Props.disabled.name(), String.valueOf(!def.isEnabled()), key);

                }
                else
                    throw new IllegalArgumentException("Existing definition does not exist in the DB");
            }
            else
            {
                // jdk 1.6 script engine implementation, create an engine meta data map but don't add an entry
                // to the external script engine table.
                String key = makeKey(def.getExtensions());

                setProp(Props.disabled.name(), String.valueOf(!def.isEnabled()), key);
            }
        }
        else
            throw new IllegalArgumentException("Engine definition must be an instance of LabkeyScriptEngineManager.EngineDefinition");
        return def;
    }

    public static boolean isFactoryEnabled(ScriptEngineFactory factory)
    {
        if (factory instanceof ExternalScriptEngineFactory)
        {
            return ((ExternalScriptEngineFactory)factory).getDefinition().isEnabled();
        }
        else
        {
            String key = makeKey(factory.getExtensions().toArray(new String[0]));
            return !BooleanUtils.toBoolean(getProp(Props.disabled.name(), key));
        }
    }

    private static String makeKey(String[] engineExtensions)
    {
        return ENGINE_DEF_MAP_PREFIX + StringUtils.join(engineExtensions, ',');
    }

    private static ExternalScriptEngineDefinition createDefinition(Map<String, String> props)
    {
        String key = props.get(Props.key.name());
        String name = props.get(Props.name.name());
        String exePath = props.get(Props.exePath.name());
        String extensionStr = props.get(Props.extensions.name());

        //
        // if using Rserve then it's okay for the exePath to be null
        //
        boolean useRserve = AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING);

        if (key != null && name != null && extensionStr != null && (useRserve || (exePath!=null)))
        {
            try
            {
                String[] extensions = StringUtils.split(extensionStr, ',');
                EngineDefinition def = new EngineDefinition();

                BeanUtils.populate(def, props);
                def.setExtensions(extensions);
                def.setEnabled(!BooleanUtils.toBoolean(props.get(Props.disabled.name())));

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
        Map<String, String> map = PropertyManager.getProperties(mapName);
        String ret = map.get(prop);

        if (!StringUtils.isEmpty(ret))
            return ret;
        else
            return null;
    }

    private static void setProp(String prop, String value, String mapName)
    {
        Map<String, String> map = PropertyManager.getWritableProperties(mapName, true);

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
        String _machine;
        int    _port;
        String _user;
        String _password;
        String _reportShare;
        String _pipelineShare;
        boolean _enabled;
        boolean _external;


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

        public String getMachine()
        {
            return _machine;
        }

        public int getPort()
        {
            return _port;
        }

        public String getReportShare()
        {
            return _reportShare;
        }

        public String getPipelineShare()
        {
            return _pipelineShare;
        }

        public String getUser()
        {
            return _user;
        }

        public String getPassword()
        {
            return _password;
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

        public boolean isEnabled()
        {
            return _enabled;
        }

        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }

        public boolean isExternal()
        {
            return _external;
        }

        public void setExternal(boolean external)
        {
            _external = external;
        }

        public void setPort(int port)
        {
            _port = port;
        }

        public void setMachine(String machine)
        {
            _machine = machine;
        }

        public void setUser(String user)
        {
            _user = user;
        }

        public void setPassword(String password)
        {
            _password = password;
        }

        public void setReportShare(String reportShare)
        {
            _reportShare = reportShare;
        }
        public void setPipelineShare(String pipelineShare)
        {
            _pipelineShare = pipelineShare;
        }
    }
}
