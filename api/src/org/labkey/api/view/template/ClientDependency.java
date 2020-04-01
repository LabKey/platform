/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.api.view.template;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ExperimentalFeatureService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.clientLibrary.xml.DependencyType;
import org.labkey.clientLibrary.xml.ModeTypeEnum;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;


/**
 * Base class for handling client dependencies
 *
 * User: bbimber
 * Date: 6/13/12
 * Time: 5:25 PM
 */
public abstract class ClientDependency
{
    private static final Logger LOG = Logger.getLogger(ClientDependency.class);
    private static final Cache<String, ClientDependency> CACHE = CacheManager.getBlockingStringKeyCache(10000, CacheManager.MONTH, "Client dependencies", null);

    static
    {
        ExperimentalFeatureService.get().addFeatureListener(AppProps.EXPERIMENTAL_JAVASCRIPT_API, (feature, enabled) -> CACHE.clear());
    }

    public enum TYPE
    {
        js(".js"),
        css(".css"),
        sass(".sass"),
        jsb2(".jsb2"),
        context(".context"),
        lib(".lib.xml"),
        manifest(".webmanifest");

        TYPE(String extension)
        {
            _extension = extension;
            _fileType = new FileType(extension);
        }

        public static TYPE fromPath(Path p)
        {
            return fromString(p.toString());
        }

        public static TYPE fromString(String s)
        {
            for (TYPE t : TYPE.values())
            {
                if (t._fileType.isType(s))
                    return t;
            }
            return null;
        }

        public String getExtension()
        {
            return _extension;
        }

        private final String _extension;
        private final FileType _fileType;
    }

    protected final TYPE _primaryType;
    protected final ModeTypeEnum.Enum _mode;

    protected String _prodModePath;
    protected String _devModePath;

    protected ClientDependency(TYPE primaryType, ModeTypeEnum.Enum mode)
    {
        _primaryType = primaryType;
        _mode = mode;
    }

    protected abstract void init();

    static void logError(String message)
    {
        URLHelper url = null;
        ViewContext ctx = HttpView.getRootContext();

        if (null != ctx)
            url = HttpView.getContextURLHelper();

        LOG.error(message + (null != url ? " URL: " + url.getLocalURIString() : ""));
    }

    public static boolean isExternalDependency(String path)
    {
        return path != null && (path.startsWith("http://") || path.startsWith("https://"));
    }

    @NotNull
    public static ClientDependency fromModuleName(String mn)
    {
        Module m = ModuleLoader.getInstance().getModule(mn);

        if (m == null)
        {
            throw new IllegalArgumentException("Module '" + mn + "' not found, unable to create client resource");
        }

        return ClientDependency.fromModule(m);
    }

    @NotNull
    public static ClientDependency fromModule(Module m)
    {
        //noinspection ConstantConditions
        return fromCache(m.getName() + TYPE.context.getExtension(), ModeTypeEnum.BOTH);
    }

    // converts a semi-colon delimited list of dependencies into a set of appropriate ClientDependency objects
    public static Set<ClientDependency> fromList(String dependencies)
    {
        Set<ClientDependency> set = new LinkedHashSet<>();

        if (null != dependencies)
        {
            String [] list = dependencies.split(";");
            for (String d : list)
            {
                if (StringUtils.isNotBlank(d))
                    set.add(fromPath(d.trim()));
            }
        }

        return set;
    }

    @Nullable
    public static ClientDependency fromXML(DependencyType type)
    {
        if (null == type || null == type.getPath())
            return null;

        if (type.isSetMode())
            return fromPath(type.getPath(), type.getMode());

        return fromPath(type.getPath());
    }

    public static ClientDependency fromPath(String path)
    {
        return fromPath(path, ModeTypeEnum.BOTH);
    }

    public static ClientDependency fromPath(String path, @NotNull ModeTypeEnum.Enum mode)
    {
        ClientDependency cd;

        if (isExternalDependency(path))
            cd = new ExternalClientDependency(path, mode);
        else
            cd = fromCache(path, mode);

        return cd;
    }

    protected static @Nullable ClientDependency fromCache(String requestedPath, @NotNull ModeTypeEnum.Enum mode)
    {
        requestedPath = requestedPath.replaceAll("^/", "");

        //as a convenience, if no extension provided, assume it's a library
        if (StringUtils.isEmpty(FileUtil.getExtension(requestedPath)))
            requestedPath = requestedPath + TYPE.lib.getExtension();

        // When experimental @labkey/api flag is enabled replace requests for clientapi_core with labkey_api_js.
        // The results are cached so this can only take effect upon a cleared cache.
        if ("clientapi_core.lib.xml".equalsIgnoreCase(requestedPath) &&
            ExperimentalFeatureService.get().isFeatureEnabled(AppProps.EXPERIMENTAL_JAVASCRIPT_API))
        {
            requestedPath = "labkey_api_js.lib.xml";
        }

        Path path = Path.parse(requestedPath).normalize();

        if (path == null)
        {
            LOG.warn("Invalid client dependency path: " + requestedPath);
            return null;
        }

        String key = getCacheKey(path.toString(), mode);

        return CACHE.get(key, null, new ClientDependencyCacheLoader(CACHE, path, mode));
    }

    protected static String getCacheKey(@NotNull String identifier, @NotNull ModeTypeEnum.Enum mode)
    {
        return identifier.toLowerCase() + "|" + mode.toString();
    }

    protected abstract String getUniqueKey();

    @Nullable
    public TYPE getPrimaryType()
    {
        return _primaryType;
    }

    protected @NotNull Set<ClientDependency> getUniqueDependencySet(Container c)
    {
        return Collections.emptySet();
    }

    private @NotNull Set<String> getProductionScripts(Container c, TYPE type)
    {
        return getScripts(c, type, _prodModePath, cd -> cd.getProductionScripts(c, type));
    }

    private @NotNull Set<String> getDevModeScripts(Container c, TYPE type)
    {
        return getScripts(c, type, _devModePath, cd -> cd.getDevModeScripts(c, type));
    }

    private @NotNull Set<String> getScripts(Container c, TYPE type, String path, Function<ClientDependency, Set<String>> function)
    {
        Set<String> scripts = new LinkedHashSet<>();
        if (_primaryType != null && _primaryType == type && path != null)
            scripts.add(path);

        Set<ClientDependency> cd = getUniqueDependencySet(c);
        for (ClientDependency r : cd)
            scripts.addAll(function.apply(r));

        return scripts;
    }

    public @NotNull Set<String> getManifestPaths(Container c)
    {
        return getManifestPaths(c, AppProps.getInstance().isDevMode());
    }

    public @NotNull Set<String> getManifestPaths(Container c, boolean devMode)
    {
        if (devMode)
            return getDevModeScripts(c, TYPE.manifest);
        else
            return getProductionScripts(c, TYPE.manifest);
    }

    public @NotNull Set<String> getCssPaths(Container c)
    {
        return getCssPaths(c, AppProps.getInstance().isDevMode());
    }

    public @NotNull Set<String> getCssPaths(Container c, boolean devMode)
    {
        if (devMode)
            return getDevModeScripts(c, TYPE.css);
        else
            return getProductionScripts(c, TYPE.css);
    }

    public @NotNull Set<String> getJsPaths(Container c)
    {
        return getJsPaths(c, AppProps.getInstance().isDevMode());
    }

    public @NotNull Set<String> getJsPaths(Container c, boolean devMode)
    {
        if (devMode)
            return getDevModeScripts(c, TYPE.js);
        else
            return getProductionScripts(c, TYPE.js);
    }

    public @NotNull Set<Module> getRequiredModuleContexts(Container c)
    {
        Set<Module> modules = new HashSet<>();

        for (ClientDependency r : getUniqueDependencySet(c))
            modules.addAll(r.getRequiredModuleContexts(c));

        return modules;
    }

    @Override
    public int hashCode()
    {
        return getUniqueKey().hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof ClientDependency))
            return false;

        return ((ClientDependency)o).getUniqueKey().equals(getUniqueKey());
    }

    /**
     * @return The string representation of this ClientDependency, as would appear in an XML or other config file
     */
    public abstract String getScriptString();
}
