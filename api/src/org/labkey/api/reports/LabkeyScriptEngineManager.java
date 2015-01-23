/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.script.ScriptService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.springframework.beans.MutablePropertyValues;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* User: Karl Lum
* Date: Dec 12, 2008
* Time: 12:52:28 PM
*/
public class LabkeyScriptEngineManager extends ScriptEngineManager
{
    private static final Logger LOG = Logger.getLogger(LabkeyScriptEngineManager.class);

    private static final String SCRIPT_ENGINE_MAP = "ExternalScriptEngineMap";
    private static final String ENGINE_DEF_MAP_PREFIX = "ScriptEngineDefinition_";
    private static final String REMOTE_ENGINE_DEF_MAP_PREFIX = ENGINE_DEF_MAP_PREFIX + "remote_";

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
        pathMap,
        user,
        password,
        remote
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
        return getEngineByExtension(extension, false);
    }

    public ScriptEngine getEngineByExtension(String extension, boolean requestRemote)
    {
        if (!StringUtils.isBlank(extension))
        {
            ScriptEngine engine = super.getEngineByExtension(extension);
            assert engine == null || !engine.getClass().getSimpleName().equals("com.sun.script.javascript.RhinoScriptEngine") : "Should not use jdk bundled script engine";

            if (engine == null)
            {
                ArrayList<ExternalScriptEngineDefinition> rEngines = new ArrayList<>();

                for (ExternalScriptEngineDefinition def : getEngineDefinitions())
                {
                    if (def.isEnabled() && Arrays.asList(def.getExtensions()).contains(extension))
                    {
                        if (RScriptEngineFactory.isRScriptEngine(def.getExtensions()))
                            rEngines.add(def);
                        else
                            return (new ExternalScriptEngineFactory(def)).getScriptEngine();
                    }
                }

                return (rEngines.size() == 0) ? null : selectRScriptEngine(rEngines, requestRemote);
            }
            else if (isFactoryEnabled(engine.getFactory()))
                return engine;
        }
        return null;
    }

    //
    // We allow both a local and a remote R engine to be registered.  Important:  this function assumes that remote
    // engines are only returned from getEngineDefinitions() if the Rserve feature has been enabled.
    //
    // If a single engine is registered, return it.
    //
    // If both local and remote are available then return appropriate engine based on the requestRemote flag.
    //
    private static ScriptEngine selectRScriptEngine(ArrayList<ExternalScriptEngineDefinition> rEngines, boolean requestRemote)
    {
        ScriptEngineFactory factory;

        if (rEngines.size() == 1)
        {
            ExternalScriptEngineDefinition def = rEngines.get(0);
            factory = def.isRemote() ? new RserveScriptEngineFactory(def) : new RScriptEngineFactory(def);
        }
        else
        {
            assert 2 == rEngines.size() : "At most only two R script engines should be registered";
            assert rEngines.get(0).isRemote() != rEngines.get(1).isRemote() : "One local and one remote R engine should be registered";

            ExternalScriptEngineDefinition defRemote = rEngines.get(0).isRemote() ? rEngines.get(0) : rEngines.get(1);
            ExternalScriptEngineDefinition defLocal = rEngines.get(0).isRemote() ? rEngines.get(1) : rEngines.get(0);

            factory = requestRemote ? new RserveScriptEngineFactory(defRemote) : new RScriptEngineFactory(defLocal);
        }

        return factory.getScriptEngine();
   }

    public static List<ExternalScriptEngineDefinition> getEngineDefinitions()
    {
        List<ExternalScriptEngineDefinition> engines = new ArrayList<>();
        Map<String, String> map = PropertyManager.getProperties(SCRIPT_ENGINE_MAP);

        for (String name : map.values())
        {
            Map<String, String> def = PropertyManager.getProperties(name);
            boolean isRemote = false;

            if (def.containsKey(Props.remote.name()))
                isRemote = Boolean.valueOf(def.get(Props.remote.name()));

            try
            {
                if (!isRemote || AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING))
                    engines.add(createDefinition(def));
            }
            catch (Exception e)
            {
                LOG.error("Failed to parse script engine definition: " + e.getMessage());
                //deleteDefinition(name);
            }
        }

        return engines;
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
            PropertyManager.PropertyMap engines = PropertyManager.getWritableProperties(SCRIPT_ENGINE_MAP, false);
            if (engines != null && engines.containsKey(key))
            {
                engines.remove(key);
                engines.save();

                PropertyManager.PropertyMap definition = PropertyManager.getWritableProperties(key, false);
                if (definition != null)
                {
                    definition.clear();
                    definition.save();
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
                    key = makeKey(def.isRemote(), def.getExtensions());
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

                    if (def.isRemote())
                    {
                        setProp(Props.machine.name(), def.getMachine(), key);
                        setProp(Props.port.name(), String.valueOf(def.getPort()), key);

                        String pathMapStr = null;
                        PathMapper pathMapper = def.getPathMap();
                        if (pathMapper != null && !pathMapper.getPathMap().isEmpty())
                        {
                            pathMapStr = ((PathMapperImpl)pathMapper).toJSON().toString();
                        }

                        setProp(Props.pathMap.name(), pathMapStr, key);
                        setProp(Props.user.name(), def.getUser(), key);
                        setProp(Props.password.name(), def.getPassword(), key);
                    }
                    else
                    {
                        setProp(Props.exePath.name(), def.getExePath(), key);
                    }
                    // note that this really is the invocation command for both local and
                    // remote engines
                    setProp(Props.exeCommand.name(), def.getExeCommand(), key);
                    setProp(Props.outputFileName.name(), def.getOutputFileName(), key);
                    setProp(Props.disabled.name(), String.valueOf(!def.isEnabled()), key);
                    setProp(Props.remote.name(), String.valueOf(def.isRemote()), key);
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

    private static String makeKey(boolean isRemote, String[] engineExtensions)
    {
        String prefix = isRemote ? REMOTE_ENGINE_DEF_MAP_PREFIX : ENGINE_DEF_MAP_PREFIX;
        return prefix + StringUtils.join(engineExtensions, ',');
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
        String pathMapStr = props.get(Props.pathMap.name());
        boolean isRemote = Boolean.valueOf(props.get(Props.remote.name()));

        // Create a copy of the props so we can remove pathMap
        props = new HashMap<>(props);
        props.remove(Props.pathMap.name());

        if (key != null && name != null && extensionStr != null && (isRemote || (exePath!=null)))
        {
            try
            {
                String[] extensions = StringUtils.split(extensionStr, ',');
                EngineDefinition def = new EngineDefinition();

                BeanUtils.populate(def, props);
                def.setExtensions(extensions);
                def.setEnabled(!BooleanUtils.toBoolean(props.get(Props.disabled.name())));

                PathMapper pathMapper;
                if (pathMapStr != null && !pathMapStr.equals("null"))
                {
                    JSONObject pathMapJson = new JSONObject(pathMapStr);
                    pathMapper = PathMapperImpl.fromJSON(pathMapJson);
                }
                else
                {
                    pathMapper = new PathMapperImpl();
                }
                def.setPathMap(pathMapper);

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
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(mapName, true);

        map.put(prop, value);
        map.save();
    }

    public static class EngineDefinition implements ExternalScriptEngineDefinition, CustomApiForm
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
        PathMapper _pathMap;
        boolean _enabled;
        boolean _external;
        boolean _remote;

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

        public boolean isRemote() { return _remote; }

        public void setRemote(boolean remote) {_remote = remote; }

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

        @Override
        public PathMapper getPathMap()
        {
            return _pathMap;
        }

        @Override
        public void setPathMap(PathMapper pathMap)
        {
            _pathMap = pathMap;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            // Use default binding for most fields
            MutablePropertyValues params = new MutablePropertyValues(props);
            BaseViewAction.defaultBindParameters(this, "form", params);

            // Handle pathMap
            JSONObject jsonPathMap = (JSONObject)props.get("pathMap");
            if (jsonPathMap != null)
                _pathMap = PathMapperImpl.fromJSON(jsonPathMap, true /*trackValidationErrors*/);
        }

    }

}
