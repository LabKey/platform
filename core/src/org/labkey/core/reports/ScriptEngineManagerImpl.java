/*
 * Copyright (c) 2018-2026 LabKey Corporation
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
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.docker.DockerService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.premium.PremiumFeatureNotEnabledException;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.ExternalScriptEngineDefinition.EngineKind;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.ScriptEngineProvider;
import org.labkey.api.reports.ScriptEngineProviderRegistry;
import org.labkey.api.reports.report.r.RserveScriptEngine;
import org.labkey.api.script.ScriptService;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.Encryption.AESConfig;
import org.labkey.api.security.Encryption.Algorithm;
import org.labkey.api.security.Encryption.DecryptionException;
import org.labkey.api.security.Encryption.EncryptionMigrationHandler;
import org.labkey.api.security.User;
import org.labkey.api.settings.LenientStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.core.reports.report.r.RDockerScriptEngine;
import org.labkey.core.script.RhinoLanguageVersion;
import org.labkey.core.script.RhinoScriptEngine;
import org.springframework.validation.BindException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableList;

public class ScriptEngineManagerImpl extends ScriptEngineManager implements LabKeyScriptEngineManager
{
    private static final Logger LOG = LogHelper.getLogger(ScriptEngineManagerImpl.class, "Script engine anomalies");
    private static final String ENGINE_DEF_MAP_PREFIX = "ScriptEngineDefinition_";
    private static final String RHINO_KEY = ENGINE_DEF_MAP_PREFIX + "js";
    private static final String ALL_ENGINES = "ALL";
    private static final String SCOPE_SCRIPT_ENGINE_DEFINITION = "ScriptEngineDefinition";

    // cache engine definitions by:
    // - "ALL" -> all engines
    // - container+context -> engines scoped to a single container and context enum
    private static final BlockingCache<String, List<ExternalScriptEngineDefinition>> ENGINE_DEFINITION_CACHE = CacheManager.getBlockingCache(String.class, 100, CacheManager.DAY, "Script engine definitions", (key, _) -> {
        if (ALL_ENGINES.equals(key))
        {
            // fetch all script engine definitions
            return unmodifiableList(new TableSelector(CoreSchema.getInstance().getTableInfoReportEngines()).getArrayList(ExternalScriptEngineDefinitionImpl.class));
        }
        else
        {
            // strip the engine context off of the end of the containerId
            int colon = key.indexOf(':');
            return unmodifiableList(selectScopedEngines(key.substring(0, colon), key.substring(colon + 1)));
        }
    });

    private static List<ExternalScriptEngineDefinition> selectScopedEngines(String containerId, String context)
    {
        SQLFragment sql = SQLFragment.unsafe("SELECT * FROM ")
            .append(CoreSchema.getInstance().getTableInfoReportEngines(), "")
            .unsafeAppend(" WHERE RowId IN (SELECT EngineId FROM ")
            .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
            .unsafeAppend(" WHERE Container = ? AND EngineContext = ?)")
            .add(containerId)
            .add(context);

        return new ArrayList<>(new SqlSelector(CoreSchema.getInstance().getSchema(), sql).getArrayList(ExternalScriptEngineDefinitionImpl.class));
    }

    private static final String PASSWORD_FIELD = "password";

    static final EncryptionMigrationHandler ENCRYPTION_MIGRATION_HANDLER = new EncryptionMigrationHandler()
    {
        @Override
        public String getDescription()
        {
            return "Script Engine Passwords";
        }

        @Override
        public void migrateEncryptedContent(String oldPassPhrase, String keySource, AESConfig oldConfig)
        {
            String currentPassPhrase = Encryption.getEncryptionPassPhrase();
            if (currentPassPhrase == null)
            {
                LOG.warn("  Cannot migrate encrypted content: EncryptionKey not specified");
                return;
            }

            Algorithm oldAes = Encryption.getAES128(oldPassPhrase, keySource, oldConfig);
            Algorithm newAes = Encryption.getAES128(currentPassPhrase, keySource, AESConfig.current);

            TableInfo tinfo = CoreSchema.getInstance().getTableInfoReportEngines();
            new TableSelector(tinfo, PageFlowUtil.set("RowId", "Configuration")).<Integer, String>getValueMap(Integer.class).forEach((rowId, configuration) -> {
                JSONObject json = new JSONObject(configuration);
                String oldEncryptedPassword = json.optString(PASSWORD_FIELD, null);
                if (null != oldEncryptedPassword)
                {
                    LOG.info("    Migrating script engine configuration {}", rowId);
                    try
                    {
                        String decryptedPassword;
                        try
                        {
                            decryptedPassword = oldAes.decrypt(Base64.decodeBase64(oldEncryptedPassword));

                            String newEncryptedPassword = Base64.encodeBase64String(newAes.encrypt(decryptedPassword));
                            assert decryptedPassword.equals(newAes.decrypt(Base64.decodeBase64(newEncryptedPassword)));

                            if (newEncryptedPassword != null)
                            {
                                savePassword(tinfo, rowId, json, newEncryptedPassword);
                            }
                        }
                        catch (DecryptionException e)
                        {
                            LOG.info("    Failed to decrypt password for configuration {}. This configuration will be skipped.", rowId);
                        }
                    }
                    catch (Exception e)
                    {
                        LOG.error("Exception while attempting to migrate configuration {}", rowId, e);
                    }
                }
            });
            LOG.info("  Migration of encrypted content in scripting engine configurations is complete");
        }

        private void savePassword(TableInfo tinfo, int rowId, JSONObject json, @Nullable String newPassword)
        {
            json.put(PASSWORD_FIELD, newPassword);
            Table.update(null, tinfo, PageFlowUtil.map("Configuration", json.toString()), rowId);
        }

        @Override
        public void deleteEncryptedContent()
        {
            LOG.info("Clearing the password from all script engine configurations that have one");
            TableInfo tinfo = CoreSchema.getInstance().getTableInfoReportEngines();
            new TableSelector(tinfo, PageFlowUtil.set("RowId", "Configuration")).<Integer, String>getValueMap(Integer.class).forEach((rowId, configuration) -> {
                JSONObject json = new JSONObject(configuration);
                String encryptedPassword = json.optString(PASSWORD_FIELD, null);
                if (null != encryptedPassword)
                {
                    savePassword(tinfo, rowId, json, null);
                }
            });
        }
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
        docker,
        kind
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
            ScriptEngineProvider provider = ScriptEngineProviderRegistry.get().get(def.getType(), def.getKind());
            if (provider == null)
                throw new ConfigurationException("No script engine provider for " + def.getType() + " " + def.getKind() + " engine: " + def.getName());
            provider.ensureAvailable(def);
            return provider.create(def);
        }
        return null;
    }

    private static boolean hasPythonExtension(@Nullable List<String> extensions)
    {
        return extensions != null && extensions.stream().anyMatch("py"::equalsIgnoreCase);
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

        if (engine == null)
            engine = getScopedEngine(ContainerManager.getRoot(), extension, context, false);

        if (engine != null)
            return engine;

        // no site default for this language and context
        return getEngineDefinitions().stream()
                .filter(def -> def.isEnabled() && Arrays.asList(def.getExtensions()).contains(extension))
                .findFirst()
                .orElse(null);
    }

    public static boolean isSiteDefault(@NotNull ExternalScriptEngineDefinition def, @NotNull EngineContext context)
    {
        return ENGINE_DEFINITION_CACHE.get(makeCacheKey(ContainerManager.getRoot(), context)).stream()
                .anyMatch(scoped -> Objects.equals(scoped.getRowId(), def.getRowId()));
    }

    // As on the admin page, the last engine of a language can be deleted even when it is the site default
    public boolean isProtectedSiteDefault(@NotNull ExternalScriptEngineDefinition def)
    {
        return Arrays.stream(EngineContext.values()).anyMatch(context -> isSiteDefault(def, context))
                && getEngineDefinitions().stream().anyMatch(other -> other.isExternal() && !Objects.equals(other.getRowId(), def.getRowId()) && isSameLanguage(other, def));
    }

    public static boolean supportsContext(@NotNull ExternalScriptEngineDefinition def, @NotNull EngineContext context)
    {
        ScriptEngineProvider provider = ScriptEngineProviderRegistry.get().get(def.getType(), def.getKind());
        return provider != null && provider.supportsContext(def, context);
    }

    // Until the admin UI has a checkbox per context, one choice sets the site default for every context the engine supports
    private void setSiteDefault(@NotNull ExternalScriptEngineDefinition def, boolean isDefault)
    {
        Container root = ContainerManager.getRoot();
        for (EngineContext context : EngineContext.values())
        {
            if (isDefault)
            {
                if (supportsContext(def, context))
                    setEngineScope(root, def, context);
            }
            else if (isSiteDefault(def, context))
                removeEngineScope(root, def, context);
        }
    }

    @Override
    public void setEngineScope(@NotNull Container container, @NotNull ExternalScriptEngineDefinition def, @NotNull EngineContext context)
    {
        if (def.getRowId() != null)
        {
            // Mappings that predate the rule stay, so only a new mapping is rejected
            if (!supportsContext(def, context) && selectScopedEngines(container.getId(), context.name()).stream().noneMatch(scoped -> Objects.equals(scoped.getRowId(), def.getRowId())))
                throw new IllegalArgumentException("The script engine '" + def.getName() + "' can't run in the " + context + " context");

            try (DbScope.Transaction tx = CoreSchema.getInstance().getScope().ensureTransaction())
            {
                tx.addCommitTask(ENGINE_DEFINITION_CACHE::clear, DbScope.CommitTaskOption.POSTCOMMIT);

                deleteScopedRows(container, context, def);

                SQLFragment insert = SQLFragment.unsafe("INSERT INTO ")
                        .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                        .unsafeAppend(" (EngineId, Container, EngineContext) VALUES(?,?,?)")
                        .add(def.getRowId())
                        .add(container)
                        .add(context);

                new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(insert);

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

            deleteScopedRows(container, context, def);

            tx.commit();
        }
    }

    // Deletes the container's mappings for the context in def's language, leaving other languages' mappings in place
    private static void deleteScopedRows(@NotNull Container container, @NotNull EngineContext context, @NotNull ExternalScriptEngineDefinition def)
    {
        List<Integer> engineIds = selectScopedEngines(container.getId(), context.name()).stream()
                .filter(scoped -> isSameLanguage(scoped, def))
                .map(ExternalScriptEngineDefinition::getRowId)
                .toList();

        if (engineIds.isEmpty())
            return;

        SQLFragment sql = SQLFragment.unsafe("DELETE FROM ")
                .append(CoreSchema.getInstance().getTableInfoReportEngineMap(), "")
                .unsafeAppend(" WHERE Container = ? AND EngineContext = ? AND EngineId ")
                .add(container)
                .add(context)
                .appendInClause(engineIds, CoreSchema.getInstance().getSqlDialect());

        new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(sql);
    }

    // External covers many languages, so External definitions are the same language only when they share an extension
    static boolean isSameLanguage(@NotNull ExternalScriptEngineDefinition a, @NotNull ExternalScriptEngineDefinition b)
    {
        if (a.getType() != b.getType())
            return false;
        if (a.getType() != ExternalScriptEngineDefinition.Type.External)
            return true;

        Set<String> extensions = lowerCaseExtensions(a);
        return lowerCaseExtensions(b).stream().anyMatch(extensions::contains);
    }

    private static Set<String> lowerCaseExtensions(ExternalScriptEngineDefinition def)
    {
        return def.getExtensions() == null ? Set.of() : Arrays.stream(def.getExtensions()).map(String::toLowerCase).collect(Collectors.toSet());
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
                // delete any folder scoped mappings
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("EngineId"), def.getRowId());
                Table.delete(CoreSchema.getInstance().getTableInfoReportEngineMap(), filter);
                Table.delete(CoreSchema.getInstance().getTableInfoReportEngines(), def.getRowId());

                // delete the docker image config for docker engine types
                if (def.getDockerImageRowId() != null && DockerService.get() != null)
                {
                    DockerService.get().deleteDockerImage(user, def.getDockerImageRowId());
                }

                tx.addCommitTask(ENGINE_DEFINITION_CACHE::clear, DbScope.CommitTaskOption.POSTCOMMIT);
                tx.commit();
            }
        }
        else
            LOG.error("Script engine definition cannot be deleted: {}", def.getName());
    }

    @Override
    public ExternalScriptEngineDefinition saveDefinition(@NotNull User user, @NotNull ExternalScriptEngineDefinition def)
    {
        if (def.isExternal())
        {
            // Admins create Python engines from the External or Perl templates by changing the extension
            if (def instanceof ExternalScriptEngineDefinitionImpl impl && (impl.getType() == ExternalScriptEngineDefinition.Type.External || impl.getType() == ExternalScriptEngineDefinition.Type.Perl) && impl.getExtensions() != null && hasPythonExtension(Arrays.asList(impl.getExtensions())))
                impl.setType(ExternalScriptEngineDefinition.Type.Python);

            try (DbScope.Transaction tx = CoreSchema.getInstance().getScope().ensureTransaction())
            {
                tx.addCommitTask(ENGINE_DEFINITION_CACHE::clear, DbScope.CommitTaskOption.POSTCOMMIT);

                if (def.getRowId() != null)
                    Table.update(user, CoreSchema.getInstance().getTableInfoReportEngines(), def, def.getRowId());
                else
                    Table.insert(user, CoreSchema.getInstance().getTableInfoReportEngines(), def);

                if (def instanceof ExternalScriptEngineDefinitionImpl impl && impl.getDefaultInput() != null)
                    setSiteDefault(def, impl.getDefaultInput());

                tx.commit();
            }
        }
        else
        {
            // jdk 1.6 script engine implementation, create an engine meta data map but don't add an entry
            // to the external script engine table.
            String key = makeKey(def.getExtensions());

            setProp(Props.disabled.name(), String.valueOf(!def.isEnabled()), key);

            RhinoLanguageVersion version = isRhino(def) ? RhinoLanguageVersion.fromVersion(def.getLanguageVersion()) : null;
            if (version != null)
                setProp(Props.languageVersion.name(), String.valueOf(version.getVersion()), key);
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

    public static boolean isRhino(ExternalScriptEngineDefinition def)
    {
        return !def.isExternal() && RHINO_KEY.equals(makeKey(def.getExtensions()));
    }

    public static @NotNull RhinoLanguageVersion getRhinoLanguageVersion()
    {
        RhinoLanguageVersion version = RhinoLanguageVersion.fromVersion(getProp(Props.languageVersion.name(), RHINO_KEY));
        return version != null ? version : RhinoLanguageVersion.DEFAULT;
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
        ExternalScriptEngineDefinition.Type type = ExternalScriptEngineDefinition.Type.External;

        if (extensions.contains("r"))
            type = ExternalScriptEngineDefinition.Type.R;
        else if (hasPythonExtension(extensions))
            type = ExternalScriptEngineDefinition.Type.Python;
        else if (extensions.contains("pl"))
            type = ExternalScriptEngineDefinition.Type.Perl;

        String kindStr = props.get(Props.kind.name());
        EngineKind kind = kindStr != null
            ? EnumUtils.getEnumIgnoreCase(EngineKind.class, kindStr.trim())
            : ExternalScriptEngineDefinitionImpl.kindFromFlags(Boolean.parseBoolean(props.get(Props.docker.name())), Boolean.parseBoolean(props.get(Props.remote.name())), type);
        if (kind == null)
            throw new IllegalArgumentException("Unknown script engine kind '" + kindStr + "', expected one of " + Arrays.toString(EngineKind.values()));

        if ((key != null || isFromStartupProps) && name != null && extensionStr != null && (kind != EngineKind.LOCAL || exePath != null))
        {
            try
            {
                ExternalScriptEngineDefinitionImpl def = new ExternalScriptEngineDefinitionImpl();

                // BeanUtils 1.x has no enum converter
                Map<String, String> beanProps = new HashMap<>(props);
                beanProps.remove(Props.kind.name());
                BeanUtils.populate(def, beanProps);
                def.setKind(kind);
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
        WritablePropertyMap map = PropertyManager.getWritableProperties(mapName, true);
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


    @Override
    public Map<String,Object> getScriptEngineMetrics()
    {
        Map<String,Object> views = new LinkedHashMap<>();

        for (ScriptEngineFactory factory : getEngineFactories())
        {
            Map<String, Object> record = new HashMap<>();

            record.put("factoryClass", factory.getClass().getName());
            record.put("extensions", StringUtils.join(factory.getExtensions(), ','));
            record.put("external", factory instanceof ExternalScriptEngineFactory);
            record.put("enabled", isFactoryEnabled(factory));

            if (factory instanceof ExternalScriptEngineFactory externalFactory)
            {
                ExternalScriptEngineDefinition def = externalFactory.getDefinition();
                record.put("type", String.valueOf(def.getType()));
                record.put("remote", def.isRemote());
                record.put("sandboxed", def.isSandboxed());
            }
            else if (factory instanceof ScriptService)
            {
                record.put("languageVersion", getRhinoLanguageVersion().name());
            }

            try
            {
                ScriptEngine engine = factory.getScriptEngine();
                if (null != engine)
                    record.put("engineClass", engine.getClass().getName());
            }
            catch (Exception x)
            {
                // pass
            }

            // add using a class as the key, uniquify if necessary.
            String key = (String)record.get("factoryClass");
            if (null != views.putIfAbsent(key, record))
            {
                int i=1;
                while (null != views.putIfAbsent(key + "$" + i, record))
                    i++;
            }
        }
        return views;
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

        @Test
        public void testStartupKindProperty()
        {
            assertEquals(EngineKind.RSERVE, createDefinition(Map.of("name", SCRIPT_ENGINE_NAME, "extensions", "r", "kind", "rserve", "docker", "true"), true).getKind());
            assertEquals(EngineKind.DOCKER, createDefinition(Map.of("name", SCRIPT_ENGINE_NAME, "extensions", "r", "docker", "true"), true).getKind());
            assertEquals(EngineKind.LOCAL, createDefinition(Map.of("name", SCRIPT_ENGINE_NAME, "extensions", "r", "exePath", "/usr/bin/R"), true).getKind());
            assertThrows(IllegalArgumentException.class, () -> createDefinition(Map.of("name", SCRIPT_ENGINE_NAME, "extensions", "r"), true));
            assertThrows(IllegalArgumentException.class, () -> createDefinition(Map.of("name", SCRIPT_ENGINE_NAME, "extensions", "r", "kind", "bogus"), true));
        }

        @Test
        public void testStartupPythonType()
        {
            assertEquals(ExternalScriptEngineDefinition.Type.Python, createDefinition(Map.of("name", SCRIPT_ENGINE_NAME, "extensions", "PY", "exePath", "/usr/bin/python3"), true).getType());
            assertEquals(ExternalScriptEngineDefinition.Type.Python, createDefinition(Map.of("name", SCRIPT_ENGINE_NAME, "extensions", "pl,py", "exePath", "/usr/bin/python3"), true).getType());
            assertEquals(ExternalScriptEngineDefinition.Type.External, createDefinition(Map.of("name", SCRIPT_ENGINE_NAME, "extensions", "sh", "exePath", "/bin/sh"), true).getType());
        }

        @Test
        public void testProvidersCoverLegacyFlags()
        {
            for (ExternalScriptEngineDefinition.Type type : ExternalScriptEngineDefinition.Type.values())
            {
                for (boolean docker : new boolean[]{false, true})
                {
                    for (boolean remote : new boolean[]{false, true})
                    {
                        EngineKind kind = ExternalScriptEngineDefinitionImpl.kindFromFlags(docker, remote, type);
                        assertNotNull("No provider for " + type + " " + kind, ScriptEngineProviderRegistry.get().get(type, kind));
                    }
                }
            }
        }

        @Test
        public void testProviderValidation()
        {
            ExternalScriptEngineDefinitionImpl local = new ExternalScriptEngineDefinitionImpl();
            local.setType(ExternalScriptEngineDefinition.Type.Perl);
            local.setExePath("/no/such/program");
            BindException errors = new BindException(local, "form");
            Objects.requireNonNull(ScriptEngineProviderRegistry.get().get(local.getType(), local.getKind())).validate(local, errors);
            assertNotNull(errors.getFieldError("exePath"));

            ExternalScriptEngineDefinitionImpl jupyter = new ExternalScriptEngineDefinitionImpl();
            jupyter.setType(ExternalScriptEngineDefinition.Type.Jupyter);
            jupyter.setRemoteUrl("not a url");
            errors = new BindException(jupyter, "form");
            Objects.requireNonNull(ScriptEngineProviderRegistry.get().get(jupyter.getType(), jupyter.getKind())).validate(jupyter, errors);
            assertNotNull(errors.getFieldError("remoteUrl"));
            assertNull(errors.getFieldError("exePath"));
        }

        @Test
        public void testIsSameLanguage()
        {
            assertTrue(isSameLanguage(testDefinition("a", ExternalScriptEngineDefinition.Type.R, "r"), testDefinition("b", ExternalScriptEngineDefinition.Type.R, "R")));
            assertFalse(isSameLanguage(testDefinition("a", ExternalScriptEngineDefinition.Type.R, "r"), testDefinition("b", ExternalScriptEngineDefinition.Type.Python, "py")));
            assertTrue(isSameLanguage(testDefinition("a", ExternalScriptEngineDefinition.Type.External, "sh"), testDefinition("b", ExternalScriptEngineDefinition.Type.External, "bash,SH")));
            assertFalse(isSameLanguage(testDefinition("a", ExternalScriptEngineDefinition.Type.External, "sh"), testDefinition("b", ExternalScriptEngineDefinition.Type.External, "rb")));
        }

        @Test
        public void testScopesAreLanguageSpecific()
        {
            ScriptEngineManagerImpl mgr = (ScriptEngineManagerImpl) LabKeyScriptEngineManager.get();
            User user = User.getSearchUser();
            Container c = JunitUtil.getTestContainer();
            List<ExternalScriptEngineDefinition> defs = new ArrayList<>();
            try
            {
                ExternalScriptEngineDefinition r1 = mgr.saveDefinition(user, testDefinition("Scope Test R 1", ExternalScriptEngineDefinition.Type.R, "r"));
                ExternalScriptEngineDefinition r2 = mgr.saveDefinition(user, testDefinition("Scope Test R 2", ExternalScriptEngineDefinition.Type.R, "r"));
                ExternalScriptEngineDefinition py = mgr.saveDefinition(user, testDefinition("Scope Test Python", ExternalScriptEngineDefinition.Type.Python, "py"));
                defs.addAll(List.of(r1, r2, py));

                mgr.setEngineScope(c, r1, EngineContext.report);
                mgr.setEngineScope(c, py, EngineContext.report);
                assertEquals(Set.of(r1.getRowId(), py.getRowId()), scopedIds(mgr, c));

                mgr.setEngineScope(c, r2, EngineContext.report);
                assertEquals(Set.of(r2.getRowId(), py.getRowId()), scopedIds(mgr, c));

                mgr.removeEngineScope(c, r2, EngineContext.report);
                assertEquals(Set.of(py.getRowId()), scopedIds(mgr, c));
            }
            finally
            {
                defs.forEach(def -> mgr.deleteDefinition(user, def));
            }
        }

        @Test
        public void testSiteDefaultPerContext()
        {
            ScriptEngineManagerImpl mgr = (ScriptEngineManagerImpl) LabKeyScriptEngineManager.get();
            User user = User.getSearchUser();
            Container c = JunitUtil.getTestContainer();
            List<ExternalScriptEngineDefinition> defs = new ArrayList<>();
            try
            {
                ExternalScriptEngineDefinitionImpl a = testDefinition("Site Default Test A", ExternalScriptEngineDefinition.Type.External, "sdtest");
                a.setEnabled(true);
                a.setDefault(true);
                defs.add(mgr.saveDefinition(user, a));

                ExternalScriptEngineDefinitionImpl b = testDefinition("Site Default Test B", ExternalScriptEngineDefinition.Type.External, "sdtest");
                b.setEnabled(true);
                b.setDefault(true);
                defs.add(mgr.saveDefinition(user, b));

                for (EngineContext context : EngineContext.values())
                {
                    assertTrue(isSiteDefault(b, context));
                    assertFalse(isSiteDefault(a, context));
                    assertEquals(b.getRowId(), Objects.requireNonNull(mgr.getEngine(c, "sdtest", context)).getRowId());
                }
                assertTrue(mgr.isProtectedSiteDefault(b));
                assertFalse(mgr.isProtectedSiteDefault(a));

                ExternalScriptEngineDefinitionImpl last = testDefinition("Site Default Test Last", ExternalScriptEngineDefinition.Type.External, "sdlast");
                last.setEnabled(true);
                last.setDefault(true);
                defs.add(mgr.saveDefinition(user, last));
                assertFalse("The only engine of a language can be deleted", mgr.isProtectedSiteDefault(last));

                b.setDefault(false);
                mgr.saveDefinition(user, b);
                for (EngineContext context : EngineContext.values())
                {
                    assertFalse(isSiteDefault(b, context));
                    assertNotNull(mgr.getEngine(c, "sdtest", context));
                }
            }
            finally
            {
                defs.forEach(def -> mgr.deleteDefinition(user, def));
            }
        }

        @Test
        public void testFolderMappingOverridesSiteDefaultPerContext()
        {
            ScriptEngineManagerImpl mgr = (ScriptEngineManagerImpl) LabKeyScriptEngineManager.get();
            User user = User.getSearchUser();
            Container c = JunitUtil.getTestContainer();
            List<ExternalScriptEngineDefinition> defs = new ArrayList<>();
            try
            {
                ExternalScriptEngineDefinitionImpl site = testDefinition("Context Resolution Site", ExternalScriptEngineDefinition.Type.External, "sdtest");
                site.setEnabled(true);
                site.setDefault(true);
                defs.add(mgr.saveDefinition(user, site));

                ExternalScriptEngineDefinitionImpl folder = testDefinition("Context Resolution Folder", ExternalScriptEngineDefinition.Type.External, "sdtest");
                folder.setEnabled(true);
                defs.add(mgr.saveDefinition(user, folder));

                mgr.setEngineScope(c, folder, EngineContext.transform);
                assertEquals(folder.getRowId(), Objects.requireNonNull(mgr.getEngine(c, "sdtest", EngineContext.transform)).getRowId());
                assertEquals(site.getRowId(), Objects.requireNonNull(mgr.getEngine(c, "sdtest", EngineContext.report)).getRowId());
                assertEquals(site.getRowId(), Objects.requireNonNull(mgr.getEngine(c, "sdtest", EngineContext.pipeline)).getRowId());
            }
            finally
            {
                defs.forEach(def -> mgr.deleteDefinition(user, def));
            }
        }

        @Test
        public void testSaveConvertsPythonExtensionToPythonType()
        {
            ScriptEngineManagerImpl mgr = (ScriptEngineManagerImpl) LabKeyScriptEngineManager.get();
            User user = User.getSearchUser();
            List<ExternalScriptEngineDefinition> defs = new ArrayList<>();
            try
            {
                for (ExternalScriptEngineDefinition.Type type : List.of(ExternalScriptEngineDefinition.Type.External, ExternalScriptEngineDefinition.Type.Perl))
                {
                    ExternalScriptEngineDefinitionImpl def = testDefinition("Python Conversion " + type, type, "PY");
                    def.setEnabled(false);
                    defs.add(mgr.saveDefinition(user, def));
                    assertNotNull(type + " with a py extension should save as Python", mgr.getEngineDefinition(def.getRowId(), ExternalScriptEngineDefinition.Type.Python));
                }

                ExternalScriptEngineDefinitionImpl other = testDefinition("Python Conversion Other", ExternalScriptEngineDefinition.Type.External, "sdtest");
                other.setEnabled(false);
                defs.add(mgr.saveDefinition(user, other));
                assertEquals(ExternalScriptEngineDefinition.Type.External, other.getType());
            }
            finally
            {
                defs.forEach(def -> mgr.deleteDefinition(user, def));
            }
        }

        @Test
        public void testUnsupportedContextRejected()
        {
            ScriptEngineManagerImpl mgr = (ScriptEngineManagerImpl) LabKeyScriptEngineManager.get();
            User user = User.getSearchUser();
            Container c = JunitUtil.getTestContainer();
            List<ExternalScriptEngineDefinition> defs = new ArrayList<>();
            try
            {
                ExternalScriptEngineDefinitionImpl copying = testDefinition("Context Test Rserve", ExternalScriptEngineDefinition.Type.R, "r");
                copying.setKind(EngineKind.RSERVE);
                defs.add(mgr.saveDefinition(user, copying));

                assertTrue(supportsContext(copying, EngineContext.report));
                assertFalse(supportsContext(copying, EngineContext.pipeline));
                assertFalse(supportsContext(copying, EngineContext.transform));

                mgr.setEngineScope(c, copying, EngineContext.report);
                for (EngineContext context : List.of(EngineContext.pipeline, EngineContext.transform))
                {
                    try
                    {
                        mgr.setEngineScope(c, copying, context);
                        fail("Expected a mapping for an unsupported context to be rejected: " + context);
                    }
                    catch (IllegalArgumentException expected)
                    {
                    }
                }

                ExternalScriptEngineDefinitionImpl sharing = testDefinition("Context Test Rserve Share", ExternalScriptEngineDefinition.Type.R, "r");
                sharing.setKind(EngineKind.RSERVE);
                sharing.setFileExchange(RserveScriptEngine.ModusOperandi.FileShare.name());
                defs.add(mgr.saveDefinition(user, sharing));
                for (EngineContext context : EngineContext.values())
                {
                    assertTrue(supportsContext(sharing, context));
                    mgr.setEngineScope(c, sharing, context);
                }
            }
            finally
            {
                for (EngineContext context : EngineContext.values())
                    defs.forEach(def -> mgr.removeEngineScope(c, def, context));
                defs.forEach(def -> mgr.deleteDefinition(user, def));
            }
        }

        @Test
        public void testDockerEngineLeavesDefinitionPathMapAlone()
        {
            ExternalScriptEngineDefinitionImpl def = testDefinition("Docker Path Map Test", ExternalScriptEngineDefinition.Type.R, "r");
            def.setKind(EngineKind.DOCKER);
            new RDockerScriptEngine(def, DockerService.get(), null);
            assertNull(def.getPathMap());
        }

        private static Set<Integer> scopedIds(ScriptEngineManagerImpl mgr, Container c)
        {
            return mgr.getScopedEngines(c, EngineContext.report).stream().map(ExternalScriptEngineDefinition::getRowId).collect(Collectors.toSet());
        }

        private static ExternalScriptEngineDefinitionImpl testDefinition(String name, ExternalScriptEngineDefinition.Type type, String extensions)
        {
            ExternalScriptEngineDefinitionImpl def = new ExternalScriptEngineDefinitionImpl();
            def.setName(name);
            def.setType(type);
            def.setExtensions(extensions);
            def.setExternal(true);
            def.setExePath(".");
            return def;
        }
    }
}
