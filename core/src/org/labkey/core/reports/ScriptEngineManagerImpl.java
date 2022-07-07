/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.BlockingCache;
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.premium.PremiumFeatureNotEnabledException;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.RDockerScriptEngineFactory;
import org.labkey.api.reports.RScriptEngineFactory;
import org.labkey.api.reports.RemoteRNotEnabledException;
import org.labkey.api.reports.RserveScriptEngineFactory;
import org.labkey.api.script.RhinoScriptEngine;
import org.labkey.api.script.ScriptService;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.Encryption.Algorithm;
import org.labkey.api.security.Encryption.DecryptionException;
import org.labkey.api.security.Encryption.EncryptionMigrationHandler;
import org.labkey.api.security.User;
import org.labkey.api.settings.LenientStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.PageFlowUtil;

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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableList;

/*
* User: Karl Lum
* Date: Dec 12, 2008
* Time: 12:52:28 PM
*/
public class ScriptEngineManagerImpl extends ScriptEngineManager implements LabKeyScriptEngineManager
{
    private static final Logger LOG = LogManager.getLogger(ScriptEngineManagerImpl.class);
    private static final String ENGINE_DEF_MAP_PREFIX = "ScriptEngineDefinition_";
    private static final String ALL_ENGINES = "ALL";
    private static final String SCOPE_SCRIPT_ENGINE_DEFINITION = "ScriptEngineDefinition";

    // cache engine definitions by:
    // - "ALL" -> all engines
    // - container+context -> engines scoped to a single container and context enum
    private static final BlockingCache<String, List<ExternalScriptEngineDefinition>> ENGINE_DEFINITION_CACHE = CacheManager.getBlockingStringKeyCache(100, CacheManager.DAY, "Script engine definitions", (key, argument) -> {
        if (key == ALL_ENGINES)
        {
            // fetch all script engine definitions
            return unmodifiableList(new TableSelector(CoreSchema.getInstance().getTableInfoReportEngines()).getArrayList(ExternalScriptEngineDefinitionImpl.class));
        }
        else
        {
            // strip the engine context off of the end of the containerId
            int colon = key.indexOf(':');
            String containerId = key.substring(0, colon);
            String context = key.substring(colon + 1);

            // fetch script engine definitions scoped to the container
            SQLFragment sql = new SQLFragment("SELECT * FROM ")
                .append(CoreSchema.getInstance().getTableInfoReportEngines(), "")
                .append(" WHERE RowId IN (SELECT EngineId FROM ")
                .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                .append(" WHERE Container = ? AND EngineContext = ?)")
                .add(containerId)
                .add(context);

            return unmodifiableList(new SqlSelector(CoreSchema.getInstance().getSchema(), sql).getArrayList(ExternalScriptEngineDefinitionImpl.class));
        }
    });

    private static final String PASSWORD_FIELD = "password";

    static final EncryptionMigrationHandler ENCRYPTION_MIGRATION_HANDLER = (oldPassPhrase, keySource) -> {
        LOG.info("  Attempting to migrate encrypted content in scripting engine configurations");
        Algorithm decryptAes = Encryption.getAES128(oldPassPhrase, keySource);
        TableInfo tinfo = CoreSchema.getInstance().getTableInfoReportEngines();
        Algorithm encryptAes = ExternalScriptEngineDefinitionImpl.AES.get();
        new TableSelector(tinfo, PageFlowUtil.set("RowId", "Configuration")).<Integer, String>getValueMap().forEach((rowId, configuration) -> {
            JSONObject json = new JSONObject(configuration);
            String oldEncryptedPassword = json.getString(PASSWORD_FIELD);
            if (null != oldEncryptedPassword)
            {
                LOG.info("    Migrating script engine configuration " + rowId);
                try
                {
                    String decryptedPassword = decryptAes.decrypt(Base64.decodeBase64(oldEncryptedPassword));
                    String newEncryptedPassword = Base64.encodeBase64String(encryptAes.encrypt(decryptedPassword));
                    json.replace(PASSWORD_FIELD, newEncryptedPassword);
                    assert decryptedPassword.equals(encryptAes.decrypt(Base64.decodeBase64(json.getString(PASSWORD_FIELD))));
                    Table.update(null, tinfo, PageFlowUtil.map("Configuration", json.toString()), rowId);
                }
                catch (DecryptionException e)
                {
                    LOG.info("    Failed to decrypt password for configuration " + rowId + ". This configuration will be skipped.");
                }
                catch (Exception e)
                {
                    LOG.error("Exception while attempting to migrate configuration " + rowId, e);
                }
            }
        });
        LOG.info("  Migration of encrypted content in scripting engine configurations is complete");
    };

    public static void registerEncryptionMigrationHandler()
    {
        EncryptionMigrationHandler.registerHandler(ENCRYPTION_MIGRATION_HANDLER);
    }

    private static String makeCacheKey(@NotNull Container c, @NotNull EngineContext context)
    {
        return c.getId() + ":" + context;
    }

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
    public ScriptEngine getEngineByName(@NotNull String name)
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
        return unmodifiableList(factories);
    }

    @Override
    public @Nullable ScriptEngine getEngineByExtension(@NotNull Container container, @NotNull String extension) throws PremiumFeatureNotEnabledException
    {
        return getEngineByExtension(container, extension, EngineContext.report);
    }

    @Override
    public @Nullable ScriptEngine getEngineByExtension(@NotNull Container container, @NotNull String extension, @NotNull EngineContext context) throws PremiumFeatureNotEnabledException
    {
        ScriptEngine engine = super.getEngineByExtension(extension);
        assert engine == null || !engine.getClass().getSimpleName().equals("com.sun.script.javascript.RhinoScriptEngine") : "Should not use jdk bundled script engine";

        if (engine != null)
        {
            // a bit of a hack here, rhino script engines are incorrectly associated with javascript reports even though
            // they are just rendered to the browser, need to return an instance where sandboxed is set to true to allow
            // trusted analysts the ability to create js reports
            if (engine instanceof RhinoScriptEngine && isFactoryEnabled(engine.getFactory()))
            {
                return new RhinoScriptEngine()
                {
                    @Override
                    public boolean isSandboxed()
                    {
                        return true;
                    }
                };
            }
            else
                return isFactoryEnabled(engine.getFactory()) ? engine : null;
        }

        ExternalScriptEngineDefinition def = getEngine(container, extension, context);
        if (def != null)
        {
            if (def.getType().equals(ExternalScriptEngineDefinition.Type.R))
            {
                if (def.isDocker())
                    return new RDockerScriptEngineFactory(def).getScriptEngine();
                else if (def.isRemote())
                {
                    if (PremiumService.get().isRemoteREnabled())
                        return new RserveScriptEngineFactory(def).getScriptEngine();
                    else
                    {
                        LOG.error(String.format("Remote R engine [%1$s] requested, but premium module not available/enabled.", def.getName()));
                        throw new RemoteRNotEnabledException(def);
                    }
                }
                else
                    return new RScriptEngineFactory(def).getScriptEngine();
            }
            else
                return new ExternalScriptEngineFactory(def).getScriptEngine();
        }
        return null;
    }

    // Locates any specific engines scoped at either the container or project level for an engine context
    @Override
    @Nullable
    public ExternalScriptEngineDefinition getScopedEngine(@NotNull Container container, @NotNull String extension, @NotNull EngineContext context, boolean includeProject)
    {
        // look for a folder level override
        for (ExternalScriptEngineDefinition def : getScopedEngines(container, context))
        {
            if (def.isEnabled() && Arrays.asList(def.getExtensions()).contains(extension))
            {
                return def;
            }
        }

        // check project level override
        Container project = container.getProject();
        if (includeProject && project != null)
        {
            for (ExternalScriptEngineDefinition def : getScopedEngines(project, context))
            {
                if (def.isEnabled() && Arrays.asList(def.getExtensions()).contains(extension))
                {
                    return def;
                }
            }
        }
        return null;
    }

    /**
     * Returns the engine definition for a particular folder scope. We will search for a engine at the folder level
     * followed by the project level and finally for the default engine at the site level.
     */
    private ExternalScriptEngineDefinition getEngine(Container container, String extension, EngineContext context)
    {
        ExternalScriptEngineDefinition engine = getScopedEngine(container, extension, context, true);

        if (engine != null)
            return engine;

        // return the configured site level default
        ExternalScriptEngineDefinition defaultEngine = null;
        for (ExternalScriptEngineDefinition def : getEngineDefinitions())
        {
            if (Arrays.asList(def.getExtensions()).contains(extension) && def.isEnabled())
            {
                if (def.isDefault())
                    return def;
                else if (defaultEngine == null)
                    defaultEngine = def;
            }
        }

        return defaultEngine;
    }

    @Override
    public void setEngineScope(@NotNull Container container, @NotNull ExternalScriptEngineDefinition def, @NotNull EngineContext context)
    {
        if (def.getRowId() != null)
        {
            try (DbScope.Transaction tx = CoreSchema.getInstance().getScope().ensureTransaction())
            {
                tx.addCommitTask(ENGINE_DEFINITION_CACHE::clear, DbScope.CommitTaskOption.POSTCOMMIT);

                SQLFragment sql = new SQLFragment("SELECT EngineId FROM ")
                        .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                        .append(" WHERE Container = ? AND EngineContext = ?")
                        .add(container)
                        .add(context);

                if (!new SqlSelector(CoreSchema.getInstance().getSchema(), sql).exists())
                {
                    SQLFragment insert = new SQLFragment("INSERT INTO ")
                            .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                            .append(" (EngineId, Container, EngineContext) VALUES(?,?,?)")
                            .add(def.getRowId())
                            .add(container)
                            .add(context);

                    new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(insert);
                }
                else
                {
                    SQLFragment update = new SQLFragment("UPDATE ")
                            .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                            .append(" SET EngineId = ? ")
                            .append(" WHERE Container = ? AND EngineContext = ?")
                            .add(def.getRowId())
                            .add(container)
                            .add(context);

                    new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(update);
                }

                tx.commit();
            }
        }
    }

    @Override
    public void removeEngineScope(@NotNull Container container, @NotNull ExternalScriptEngineDefinition def, @NotNull EngineContext context)
    {
        try (DbScope.Transaction tx = CoreSchema.getInstance().getScope().ensureTransaction())
        {
            tx.addCommitTask(ENGINE_DEFINITION_CACHE::clear, DbScope.CommitTaskOption.POSTCOMMIT);

            SQLFragment sql = new SQLFragment("DELETE FROM ")
                    .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                    .append(" WHERE Container = ? AND EngineContext = ?")
                    .add(container)
                    .add(context);

            new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(sql);

            tx.commit();
        }
    }

    /**
     * Returns the list of engines scoped to the specified container
     */
    public List<ExternalScriptEngineDefinition> getScopedEngines(@NotNull Container container, @NotNull EngineContext context)
    {
        return ENGINE_DEFINITION_CACHE.get(makeCacheKey(container, context));
    }

    @Override
    public List<ExternalScriptEngineDefinition> getEngineDefinitions()
    {
        return ENGINE_DEFINITION_CACHE.get(ALL_ENGINES);
    }

    @Override
    public List<ExternalScriptEngineDefinition> getEngineDefinitions(@NotNull ExternalScriptEngineDefinition.Type type)
    {
        return ENGINE_DEFINITION_CACHE.get(ALL_ENGINES)
                .stream()
                .filter(e -> e.getType() == type)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExternalScriptEngineDefinition> getEngineDefinitions(@NotNull ExternalScriptEngineDefinition.Type type, boolean enabled)
    {
        Stream<ExternalScriptEngineDefinition> stream = ENGINE_DEFINITION_CACHE.get(ALL_ENGINES)
                .stream()
                .filter(e -> e.getType() == type)
                .filter(ExternalScriptEngineDefinition::isEnabled);

        if (type == ExternalScriptEngineDefinition.Type.R)
        {
            stream = stream.filter(e -> !e.isRemote() || PremiumService.get().isRemoteREnabled());
        }

        return stream.collect(Collectors.toList());
    }

    @Override
    @Nullable
    public ExternalScriptEngineDefinition getEngineDefinition(@NotNull String name, @NotNull ExternalScriptEngineDefinition.Type type)
    {
        return ENGINE_DEFINITION_CACHE.get(ALL_ENGINES)
                .stream()
                .filter(e -> Objects.equals(e.getName(), name))
                .filter(e -> e.getType() == type)
                .findFirst().orElse(null);
    }

    @Override
    @Nullable
    public ExternalScriptEngineDefinition getEngineDefinition(int rowId, @NotNull ExternalScriptEngineDefinition.Type type)
    {
        return ENGINE_DEFINITION_CACHE.get(ALL_ENGINES)
                .stream()
                .filter(e -> Objects.equals(e.getRowId(), rowId))
                .filter(e -> e.getType() == type)
                .findFirst().orElse(null);
    }

    @Override
    public void deleteDefinition(@NotNull User user, @NotNull ExternalScriptEngineDefinition def)
    {
        if (def.getRowId() != null)
        {
            try (DbScope.Transaction tx = CoreSchema.getInstance().getScope().ensureTransaction())
            {
                tx.addCommitTask(ENGINE_DEFINITION_CACHE::clear, DbScope.CommitTaskOption.POSTCOMMIT);

                // delete any folder scoped mappings
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("EngineId"), def.getRowId());
                Table.delete(CoreSchema.getInstance().getTableInfoReportEngineMap(), filter);
                Table.delete(CoreSchema.getInstance().getTableInfoReportEngines(), def.getRowId());

                tx.commit();
            }
        }
        else
            LOG.error("Script engine definition cannot be deleted: " + def.getName());
    }

    @Override
    public ExternalScriptEngineDefinition saveDefinition(@NotNull User user, @NotNull ExternalScriptEngineDefinition def)
    {
        if (def.isExternal())
        {
            try (DbScope.Transaction tx = CoreSchema.getInstance().getScope().ensureTransaction())
            {
                tx.addCommitTask(ENGINE_DEFINITION_CACHE::clear, DbScope.CommitTaskOption.POSTCOMMIT);

                if (def.getRowId() != null)
                    Table.update(user, CoreSchema.getInstance().getTableInfoReportEngines(), def, def.getRowId());
                else
                    Table.insert(user, CoreSchema.getInstance().getTableInfoReportEngines(), def);

                tx.commit();
            }
        }
        else
        {
            // jdk 1.6 script engine implementation, create an engine meta data map but don't add an entry
            // to the external script engine table.
            String key = makeKey(def.getExtensions());

            setProp(Props.disabled.name(), String.valueOf(!def.isEnabled()), key);
        }

        // Issue 22354: It's a little heavy-handed, but clear all caches so any file-based pipeline tasks that may require a script engine will be re-loaded
        CacheManager.clearAllKnownCaches();

        return def;
    }

    @Override
    public boolean isFactoryEnabled(@NotNull ScriptEngineFactory factory)
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
        // if this definition is being built from startup props then don't look for a key because the key won't be defined in the startup properties
        // leave the key null and it will get created and populate when the definition is saved.
        if (!isFromStartupProps)
            key = props.get(Props.key.name());
        String name = props.get(Props.name.name());
        String exePath = props.get(Props.exePath.name());
        String extensionStr = props.get(Props.extensions.name());
        List<String> extensions = Collections.emptyList();
        if (extensionStr != null)
            extensions = Arrays.asList(StringUtils.split(extensionStr, ","));
        String pathMapStr = props.get(Props.pathMap.name());
        boolean isRemote = Boolean.valueOf(props.get(Props.remote.name()));
        ExternalScriptEngineDefinition.Type type = ExternalScriptEngineDefinition.Type.External;

        if (extensions.contains("r"))
            type = ExternalScriptEngineDefinition.Type.R;
        else if (extensions.contains("pl"))
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

    private static class ScriptEngineStartupProperty implements StartupProperty
    {
        @Override
        public String getPropertyName()
        {
            return "<unique name>.<property name>";
        }

        @Override
        public String getDescription()
        {
            return "Script engine definition properties. Prefix properties for each definition with a unique name.";
        }
    }

    // populates script engine definition with values from startup configuration
    // expects startup properties formatted like:
    //        ScriptEngineDefinition.{unique name}.external;bootstrap=True
    //        ScriptEngineDefinition.{unique name}.name;bootstrap=R Scripting Engine
    //        ScriptEngineDefinition.{unique name}.extensions;bootstrap=R,r
    //        ScriptEngineDefinition.{unique name}.languageName;bootstrap=R
    //        ScriptEngineDefinition.{unique name}.exePath;bootstrap=/usr/bin/R
    //
    private static class ScriptEngineStartupPropertyHandler extends LenientStartupPropertyHandler<StartupProperty>
    {
        public ScriptEngineStartupPropertyHandler()
        {
            super(SCOPE_SCRIPT_ENGINE_DEFINITION, new ScriptEngineStartupProperty());
        }

        @Override
        public void handle(Collection<StartupPropertyEntry> entries)
        {
            Map<String, Map<String, String>> enginePropertyMap = new HashMap<>();

            for (StartupPropertyEntry prop: entries)
            {
                String[] scriptEngineNameAndParamSplit = prop.getName().split("\\.");
                if (scriptEngineNameAndParamSplit.length == 2)
                {
                    String engineName = scriptEngineNameAndParamSplit[0];
                    String engineParam = scriptEngineNameAndParamSplit[1];
                    String paramValue = prop.getValue();
                    Map<String, String> propertyMap = enginePropertyMap.containsKey(engineName) ? enginePropertyMap.get(engineName) : new HashMap<>();
                    propertyMap.put(engineParam, paramValue);
                    enginePropertyMap.put(engineName, propertyMap);
                }
                else
                {
                    throw new ConfigurationException("Startup properties for creating script engine definition not formatted correctly: " + prop.getName());
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
                LabKeyScriptEngineManager mgr = LabKeyScriptEngineManager.get();
                if (mgr.getEngineDefinition(def.getName(), def.getType()) == null)
                    mgr.saveDefinition(User.getSearchUser(), def);
            }
        }
    }

    // ScriptEngineManagerImpl.TestCase tests bootstrap property setting, so we need a way to force this into new install mode
    public void populateScriptEngineDefinitionsWithStartupProps()
    {
        ModuleLoader.getInstance().handleStartupProperties(new ScriptEngineStartupPropertyHandler());
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
            LabKeyScriptEngineManager svc = LabKeyScriptEngineManager.get();

            // examine the original list of Script Engine Definitions to ensure the test script engine is not already setup
            List<ExternalScriptEngineDefinition> defList = svc.getEngineDefinitions();
            assertFalse("The script engine defined in the startup properties was already setup on this server: " + SCRIPT_ENGINE_NAME, defList.stream().anyMatch((ExternalScriptEngineDefinition def) -> def.getName().equals(SCRIPT_ENGINE_NAME))) ;

            ModuleLoader.getInstance().handleStartupProperties(new ScriptEngineStartupPropertyHandler(){
                @Override
                public @NotNull Collection<StartupPropertyEntry> getStartupPropertyEntries()
                {
                    return List.of(
                        new StartupPropertyEntry("Rtest.external", "True", "bootstrap", SCOPE_SCRIPT_ENGINE_DEFINITION),
                        new StartupPropertyEntry("Rtest.name", SCRIPT_ENGINE_NAME, "bootstrap", SCOPE_SCRIPT_ENGINE_DEFINITION),
                        new StartupPropertyEntry("Rtest.extensions", "Rtest,rtest", "bootstrap", SCOPE_SCRIPT_ENGINE_DEFINITION),
                        new StartupPropertyEntry("Rtest.languageName", "Rtest", "bootstrap", SCOPE_SCRIPT_ENGINE_DEFINITION),
                        new StartupPropertyEntry("Rtest.exePath", ".", "bootstrap", SCOPE_SCRIPT_ENGINE_DEFINITION)
                    );
                }

                @Override
                public boolean performChecks()
                {
                    return false;
                }
            });

            // now check that the expected changes occurred to the Scripting Engine Definitions on the server
            defList = svc.getEngineDefinitions();
            assertTrue("The script engine defined in the startup properties was not setup: " + SCRIPT_ENGINE_NAME, defList.stream().anyMatch((ExternalScriptEngineDefinition def) -> def.getName().equals(SCRIPT_ENGINE_NAME))) ;

            // restore the Script Engine Definitions to how they were originally
            svc.deleteDefinition(User.getSearchUser(), defList.stream().filter((ExternalScriptEngineDefinition def) -> def.getName().equals(SCRIPT_ENGINE_NAME)).findAny().get());
        }
    }
}
