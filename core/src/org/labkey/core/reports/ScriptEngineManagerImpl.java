/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.core.reports;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.RDockerScriptEngineFactory;
import org.labkey.api.reports.RScriptEngineFactory;
import org.labkey.api.reports.RserveScriptEngineFactory;
import org.labkey.api.rstudio.RStudioService;
import org.labkey.api.script.ScriptService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ConfigurationException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
* User: Karl Lum
* Date: Dec 12, 2008
* Time: 12:52:28 PM
*/
public class ScriptEngineManagerImpl extends ScriptEngineManager implements LabkeyScriptEngineManager
{
    private static final Logger LOG = Logger.getLogger(ScriptEngineManagerImpl.class);

    public static final String SCRIPT_ENGINE_MAP = "ExternalScriptEngineMap";
    private static final String ENGINE_DEF_MAP_PREFIX = "ScriptEngineDefinition_";

    enum Props
    {
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
        remote,
        pandocEnabled,
        docker
    }

    ScriptEngineFactory rhino;

    public ScriptEngineManagerImpl()
    {
        super();

        // replace the JDK bundled ScriptEngineFactory with the full, non-JDK version.
        rhino = ScriptService.get();
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
    public ScriptEngine getEngineByName(String name)
    {
        ScriptEngine engine = super.getEngineByName(name);
        assert engine == null || !engine.getClass().getSimpleName().equals("com.sun.script.javascript.RhinoScriptEngine") : "Should not use jdk bundled script engine";

        if (engine == null)
        {
            for (ExternalScriptEngineDefinition def : getEngineDefinitions())
            {
                if (def.isEnabled() && name.equals(def.getName()))
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
    public @Nullable ScriptEngine getEngineByExtension(Container c, String extension)
    {
        return getEngineByExtension(c, extension, false, false);
    }

    @Override
    public @Nullable ScriptEngine getEngineByExtension(Container c, String extension, boolean requestRemote)
    {
        return getEngineByExtension(c, extension,  requestRemote, false);
    }

    @Override
    public ScriptEngine getEngineByExtension(Container container, String extension, boolean requestRemote, boolean requestDocker)
    {
        ScriptEngine engine = super.getEngineByExtension(extension);
        assert engine == null || !engine.getClass().getSimpleName().equals("com.sun.script.javascript.RhinoScriptEngine") : "Should not use jdk bundled script engine";

        if (engine != null)
        {
            return isFactoryEnabled(engine.getFactory()) ? engine : null;
        }

        ExternalScriptEngineDefinition def = getEngine(container, extension, requestRemote, requestDocker);
        if (def != null)
        {
            if (def.getType().equals(ExternalScriptEngineDefinition.Type.R))
            {
                if (AppProps.getInstance().isExperimentalFeatureEnabled(RStudioService.R_DOCKER_SANDBOX) && def.isDocker())
                    return new RDockerScriptEngineFactory(def).getScriptEngine();
                else if (def.isRemote())
                    return new RserveScriptEngineFactory(def).getScriptEngine();
                else
                    return new RScriptEngineFactory(def).getScriptEngine();
            }
            else
                return new ExternalScriptEngineFactory(def).getScriptEngine();
        }
        return null;
    }

    /**
     * Returns the engine definition for a particular folder scope. We will search for a engine at the folder level
     * followed by the project level and finally for the default engine at the site level.
     */
    private ExternalScriptEngineDefinition getEngine(Container container, String extension, boolean requestRemote, boolean requestDocker)
    {
        ExternalScriptEngineDefinition engine = null;
        for (ExternalScriptEngineDefinition def : getScopedEngines(container))
        {
            if (def.isEnabled() && Arrays.asList(def.getExtensions()).contains(extension))
            {
                engine = def;
                break;
            }
        }

        // check project level scoping
        if (engine == null && !container.isProject())
        {
            for (ExternalScriptEngineDefinition def : getScopedEngines(container.getProject()))
            {
                if (def.isEnabled() && Arrays.asList(def.getExtensions()).contains(extension))
                {
                    engine = def;
                    break;
                }
            }
        }

        if (engine != null && (!requestRemote || engine.isRemote()))
            return engine;

        // look through all the registered engines at the site level
        List<ExternalScriptEngineDefinition> definitions = new ArrayList<>();
        for (ExternalScriptEngineDefinition def : getEngineDefinitions())
        {
            if (Arrays.asList(def.getExtensions()).contains(extension) && def.isEnabled())
                definitions.add(def);
        }

        if (!definitions.isEmpty())
        {
            // if there is only one, assume this is the default (this code can go away after the UI piece is finished)
            if (definitions.size() == 1)
                return definitions.get(0);

            // more than one registered engine, choose the one marked as the site default
            for (ExternalScriptEngineDefinition def : definitions)
            {
                if (requestDocker && AppProps.getInstance().isExperimentalFeatureEnabled(RStudioService.R_DOCKER_SANDBOX) && def.isDocker())
                    return def;
                else if (def.isDefault() && (!requestRemote || def.isRemote()))
                    return def;
            }

            if (requestRemote)
                return definitions.stream().filter(ExternalScriptEngineDefinition::isRemote).findFirst().orElse(null);
        }
        return null;
    }

    @Override
    public void setEngineScope(Container container, ExternalScriptEngineDefinition def)
    {
        if (def.getRowId() != null)
        {
            SQLFragment sql = new SQLFragment("SELECT EngineId FROM ")
                    .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                    .append(" WHERE EngineId = ? AND Container = ?")
                    .add(def.getRowId())
                    .add(container);

            if(new SqlSelector(CoreSchema.getInstance().getSchema(), sql).exists())
            {
                SQLFragment insert = new SQLFragment("INSERT INTO ")
                        .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                        .append(" (EngineId, Container) VALUES(?,?)")
                        .add(def.getRowId())
                        .add(container);

                new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(insert);
            }
            else
            {
                SQLFragment update = new SQLFragment("UPDATE ")
                        .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                        .append(" SET engineId = ? ").add(def.getRowId())
                        .append(" WHERE container = ? ").add(container);

                new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(update);
            }
        }
    }

    @Override
    public void removeEngineScope(Container container, ExternalScriptEngineDefinition def)
    {
        SQLFragment sql = new SQLFragment("DELETE FROM ")
                .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                .append(" WHERE container = ? ").add(container);

        new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(sql);
    }

    /**
     * Returns the list of engines scoped to the specified container
     */
    public List<ExternalScriptEngineDefinition> getScopedEngines(Container container)
    {
        SimpleFilter.createContainerFilter(container);
        SQLFragment sql = new SQLFragment("SELECT * FROM ")
                .append(CoreSchema.getInstance().getTableInfoReportEngines(), "")
                .append(" WHERE RowId IN (SELECT EngineId FROM ")
                .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                .append(" WHERE Container = ?)")
                .add(container);

        return new ArrayList<>(new SqlSelector(CoreSchema.getInstance().getSchema(), sql).getCollection(ExternalScriptEngineDefinitionImpl.class));
    }

    @Deprecated     // delete once the new UI is in place
    public ScriptEngine getEngineByExtension(String extension, boolean requestRemote, boolean requestDocker)
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

                return (rEngines.size() == 0) ? null : selectRScriptEngine(rEngines, requestRemote, requestDocker);
            }
            else if (isFactoryEnabled(engine.getFactory()))
                return engine;
        }
        return null;
    }

    /**
     *      If a R Docker engine has been registered and enabled, use it regardless of any registered local or remote
     *      engines.
     *      Otherwise, we allow both a local and a remote R engine to be registered. Important: this function assumes that remote
     *      engines are only returned from getEngineDefinitions() if the Rserve feature has been enabled.
     *      If a single engine is registered, return it.
     *      If both local and remote are available then return appropriate engine based on the requestRemote flag.
     *
     * @param rEngines Any available R script engines
     * @param requestRemote prefer a remote engine over local when available
     * @param requestDocker use R Docker sandbox when available
     * @return The correct ScriptEngine
     */
    private static ScriptEngine selectRScriptEngine(ArrayList<ExternalScriptEngineDefinition> rEngines, boolean requestRemote, boolean requestDocker)
    {
        ScriptEngineFactory factory;

        Optional<ExternalScriptEngineDefinition> rDockerEngine = rEngines.stream()
                                                                        .filter(def -> requestDocker && AppProps.getInstance().isExperimentalFeatureEnabled(RStudioService.R_DOCKER_SANDBOX) && def.isDocker())
                                                                        .findAny();
        if (rDockerEngine.isPresent())
        {
            factory = new RDockerScriptEngineFactory(rDockerEngine.get());
        }
        else if (rEngines.size() == 1)
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

    @Override
    public List<ExternalScriptEngineDefinition> getEngineDefinitions()
    {
        return new ArrayList<>(new TableSelector(CoreSchema.getInstance().getTableInfoReportEngines()).getCollection(ExternalScriptEngineDefinitionImpl.class));
    }

    @Override
    public List<ExternalScriptEngineDefinition> getEngineDefinitions(ExternalScriptEngineDefinition.Type type)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Type"), type);
        return new ArrayList<>(new TableSelector(CoreSchema.getInstance().getTableInfoReportEngines(), filter, null).getCollection(ExternalScriptEngineDefinitionImpl.class));
    }

    @Override
    public List<ExternalScriptEngineDefinition> getEngineDefinitions(ExternalScriptEngineDefinition.Type type, boolean enabled)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Type"), type);
        filter.addCondition(FieldKey.fromParts("Enabled"), enabled);
        return new ArrayList<>(new TableSelector(CoreSchema.getInstance().getTableInfoReportEngines(), filter, null).getCollection(ExternalScriptEngineDefinitionImpl.class));
    }

    @Override
    @Nullable
    public ExternalScriptEngineDefinition getEngineDefinition(String name, ExternalScriptEngineDefinition.Type type)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Name"), name);
        filter.addCondition(FieldKey.fromParts("Type"), type);

        return new TableSelector(CoreSchema.getInstance().getTableInfoReportEngines(), filter, null).getObject(ExternalScriptEngineDefinitionImpl.class);
    }

    @Override
    @Nullable
    public ExternalScriptEngineDefinition getEngineDefinition(int rowId, ExternalScriptEngineDefinition.Type type)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        filter.addCondition(FieldKey.fromParts("Type"), type);

        return new TableSelector(CoreSchema.getInstance().getTableInfoReportEngines(), filter, null).getObject(ExternalScriptEngineDefinitionImpl.class);
    }

    /**
     * Returns the collection of engine configurations that were stored in the legacy
     * property store. This is only used in the upgrade script from 18.22-18.23
     */
    @Deprecated
    public List<ExternalScriptEngineDefinition> getLegacyEngineDefinitions()
    {
        List<ExternalScriptEngineDefinition> engines = new ArrayList<>();
        Map<String, String> map = PropertyManager.getProperties(SCRIPT_ENGINE_MAP);

        for (String name : map.values())
        {
            Map<String, String> def = PropertyManager.getProperties(name);
            boolean isRemote = false;

            if (def.containsKey(Props.remote.name()))
                isRemote = Boolean.valueOf(def.get(Props.remote.name()));

            boolean isDocker = false;
            if (def.containsKey(Props.docker.name()))
                isDocker = Boolean.valueOf(def.get(Props.docker.name()));
            try
            {
                if (!isRemote || AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_RSERVE_REPORTING) || isDocker)
                    engines.add(createDefinition(def, false));
            }
            catch (Exception e)
            {
                LOG.error("Failed to parse script engine definition: " + e.getMessage());
            }
        }
        return engines;
    }

    @Override
    public void deleteDefinition(User user, ExternalScriptEngineDefinition def)
    {
        if (def.getRowId() != null)
        {
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getScope().ensureTransaction())
            {
                // delete any folder scoped mappings
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("EngineId"), def.getRowId());
                Table.delete(CoreSchema.getInstance().getTableInfoReportEngineMap(), filter);
                Table.delete(CoreSchema.getInstance().getTableInfoReportEngines(), def.getRowId());

                transaction.commit();
            }
        }
        else
            LOG.error("Script engine definition cannot be deleted: " + def.getName());
    }

    @Override
    public ExternalScriptEngineDefinition saveDefinition(User user, ExternalScriptEngineDefinition def)
    {
        if (def instanceof ExternalScriptEngineDefinition)
        {
            if (def.isExternal())
            {
                if (def.getRowId() != null)
                    Table.update(user, CoreSchema.getInstance().getTableInfoReportEngines(), def, def.getRowId());
                else
                    Table.insert(user, CoreSchema.getInstance().getTableInfoReportEngines(), def);
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

        // Issue 22354: It's a little heavy handed, but clear all caches so any file-based pipeline tasks that may require a script engine will be re-loaded
        CacheManager.clearAllKnownCaches();

        return def;
    }

    @Override
    public boolean isFactoryEnabled(ScriptEngineFactory factory)
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

    private static ExternalScriptEngineDefinition createDefinition(Map<String, String> props, boolean isFromStartupProps)
    {
        String key = null;
        // if this definition is being built from startup props then dont look for a key because the key wont be defined in the startup properties
        // leave the key null and it will get created and populate when the definition is saved.
        if (!isFromStartupProps)
            key = props.get(Props.key.name());
        String name = props.get(Props.name.name());
        String exePath = props.get(Props.exePath.name());
        String extensionStr = props.get(Props.extensions.name());
        String pathMapStr = props.get(Props.pathMap.name());
        boolean isRemote = Boolean.valueOf(props.get(Props.remote.name()));
        ExternalScriptEngineDefinition.Type type = ExternalScriptEngineDefinition.Type.External;

        if (extensionStr.contains("r"))
            type = ExternalScriptEngineDefinition.Type.R;
        else if (extensionStr.contains("pl"))
            type = ExternalScriptEngineDefinition.Type.Perl;

        if ((key != null || isFromStartupProps) && name != null && extensionStr != null && (isRemote || (exePath!=null)))
        {
            try
            {
                ExternalScriptEngineDefinitionImpl def = new ExternalScriptEngineDefinitionImpl();

                BeanUtils.populate(def, props);
                def.setExternal(true);
                def.setType(type);
                def.setEnabled(!BooleanUtils.toBoolean(props.get(Props.disabled.name())));
                if (pathMapStr != null)
                    def.setPathMap(pathMapStr);

                return def;
            }
            catch (Exception e)
            {
                throw new RuntimeException("Unable to create an engine definition ", e);
            }
        }
        throw new IllegalArgumentException("Unable to create an engine definition from the specified properties : " + props);
    }

    @Override
    public ExternalScriptEngineDefinition createEngineDefinition()
    {
        return new ExternalScriptEngineDefinitionImpl();
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

    public void populateScriptEngineDefinitionsWithStartupProps()
    {
        populateScriptEngineDefinitionsWithStartupProps(null);
    }

    // ScriptEngineManagerImpl.TestCase tests bootstrap property setting, so we need a way to force this into new install mode
    public void populateScriptEngineDefinitionsWithStartupProps(Boolean startupModeForTest)
    {
        final boolean isBootstrap = null != startupModeForTest?startupModeForTest : ModuleLoader.getInstance().isNewInstall();

        // populate script engine definition with values from startup configuration as appropriate for prop modifier and isBootstrap flag
        // expects startup properties formatted like:
        //        ScriptEngineDefinition.{name}.external;bootstrap=True
        //        ScriptEngineDefinition.{name}.name;bootstrap=R Scripting Engine
        //        ScriptEngineDefinition.{name}.extensions;bootstrap=R,r
        //        ScriptEngineDefinition.{name}.languageName;bootstrap=R
        //        ScriptEngineDefinition.{name}.exePath;bootstrap=/usr/bin/R
        //

        Collection<ConfigProperty> startupProps = ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION);
        Map<String, Map<String, String>> enginePropertyMap = new HashMap<>();

        for (ConfigProperty prop: startupProps)
        {
            if (prop.getModifier() == ConfigProperty.modifier.startup || (isBootstrap && prop.getModifier() == ConfigProperty.modifier.bootstrap))
            {
                String[] scriptEngineNameAndParamSplit = prop.getName().split("\\.");
                if (scriptEngineNameAndParamSplit.length == 2)
                {
                    String engineName = scriptEngineNameAndParamSplit[0];
                    String engineParam = scriptEngineNameAndParamSplit[1];
                    String paramValue = prop.getValue();
                    if (enginePropertyMap.containsKey(engineName))
                    {
                        Map<String, String> propertyMap = enginePropertyMap.get(engineName);
                        propertyMap.put(engineParam, paramValue);
                        enginePropertyMap.put(engineName, propertyMap);
                    }
                    else
                    {
                        Map<String, String> propertyMap = new HashMap<>();
                        propertyMap.put(engineParam, paramValue);
                        enginePropertyMap.put(engineName, propertyMap);
                    }
                }
                else
                {
                    throw new ConfigurationException("Startup properties for creating script engine definition not formatted correctly: " + prop.getName());
                }
            }
        }

        // for each engine create a definition from the map of properties and save it
        for (Map.Entry<String, Map <String, String>> entry : enginePropertyMap.entrySet())
        {
            // Set a default value for external to true since script engines defined in startup props will likely be external
            entry.getValue().putIfAbsent("external", "True");
            ExternalScriptEngineDefinition def = createDefinition(entry.getValue(), true);
            // Only attempt to create the script engine if no script engine with this key has been created before.
            // This means that the property modifier 'startup' will be applied only once for script engine definitions.
            // It will create a script engine definition, but does not modify an existing script engine definition.
            if (getEngineDefinition(def.getName(), def.getType()) == null)
                saveDefinition(User.getSearchUser(), def);
        }
    }

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        String SCRIPT_ENGINE_NAME = "R Scripting Engine Test";

        /**
         * Test that Script Engine Definitions can be configured from startup properties
         */
        @Test
        public void testStartupPropertiesForScriptEngineDefinition()
        {
            LabkeyScriptEngineManager svc = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);

            // ensure that the site wide ModuleLoader had test startup property values in the _configPropertyMap
            prepareTestStartupProperties();

            // examine the original list of Script Engine Definitions to ensure the test script engine is not already setup
            List<ExternalScriptEngineDefinition> defList = svc.getEngineDefinitions();
            assertFalse("The script engine defined in the startup properties was already setup on this server: " + SCRIPT_ENGINE_NAME, defList.stream().anyMatch((ExternalScriptEngineDefinition def) -> def.getName().equals(SCRIPT_ENGINE_NAME))) ;

            // call the method that makes use of the test startup properties to add a new Script Engine Definition to the server
            if (svc instanceof ScriptEngineManagerImpl)
                ((ScriptEngineManagerImpl)svc).populateScriptEngineDefinitionsWithStartupProps(true);

            // now check that the expected changes occurred to the Scripting Engine Definitions on the server
            defList = svc.getEngineDefinitions();
            assertTrue("The script engine defined in the startup properties was not setup: " + SCRIPT_ENGINE_NAME, defList.stream().anyMatch((ExternalScriptEngineDefinition def) -> def.getName().equals(SCRIPT_ENGINE_NAME))) ;

            // restore the Script Engine Definitions to how they were originally
            svc.deleteDefinition(User.getSearchUser(), defList.stream().filter((ExternalScriptEngineDefinition def) -> def.getName().equals(SCRIPT_ENGINE_NAME)).findAny().get());
        }

        private void prepareTestStartupProperties()
        {
            // prepare a multimap of config properties to test with that has properties assigned for the ScriptEngineDefinition
            MultiValuedMap<String, ConfigProperty> testConfigPropertyMap = new HashSetValuedHashMap<>();

            // prepare test Script Engine Definition properties - requries multiple lines in the propertry file for each script engine being setup
            ConfigProperty scriptEngineDefinition1 = new ConfigProperty("Rtest.external", "True", "bootstrap", ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION);
            testConfigPropertyMap.put(ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION, scriptEngineDefinition1);
            ConfigProperty scriptEngineDefinition2 = new ConfigProperty("Rtest.name", SCRIPT_ENGINE_NAME, "bootstrap", ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION);
            testConfigPropertyMap.put(ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION, scriptEngineDefinition2);
            ConfigProperty scriptEngineDefinition3 = new ConfigProperty("Rtest.extensions", "Rtest,rtest", "bootstrap", ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION);
            testConfigPropertyMap.put(ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION, scriptEngineDefinition3);
            ConfigProperty scriptEngineDefinition4 = new ConfigProperty("Rtest.languageName", "Rtest", "bootstrap", ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION);
            testConfigPropertyMap.put(ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION, scriptEngineDefinition4);
            ConfigProperty scriptEngineDefinition5 = new ConfigProperty("Rtest.exePath", ".", "bootstrap", ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION);
            testConfigPropertyMap.put(ConfigProperty.SCOPE_SCRIPT_ENGINE_DEFINITION, scriptEngineDefinition5);

            // set these test startup test properties to be used by the entire server
            ModuleLoader.getInstance().setConfigProperties(testConfigPropertyMap);
        }
    }
}
