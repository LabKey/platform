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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.clientLibrary.xml.DependencyType;
import org.labkey.clientLibrary.xml.ModeTypeEnum;
import org.labkey.clientLibrary.xml.RequiredModuleType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;


/**
 * Base class for handling client-side dependencies, such as JavaScript and CSS files. May be referenced as individual
 * files or as a library of multiple related files.
 *
 * ClientDependencies should not be cached directly, as they may become stale if their contents change on disk.
 * Instead, use the Supplier variants, which will resolve the latest version of the resource when a page is being
 * rendered. See issue 40118 for more details.
 *
 * User: bbimber
 * Date: 6/13/12
 */
public abstract class ClientDependency
{
    private static final Logger LOG = LogManager.getLogger(ClientDependency.class);
    static final Cache<Pair<Path, ModeTypeEnum.Enum>, ClientDependency> CACHE = CacheManager.getBlockingCache(10000, CacheManager.MONTH, "Client dependencies", new ClientDependencyCacheLoader());

    static
    {
        ContextListener.addModuleChangeListener(m -> CACHE.clear());
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

    @Nullable
    public static ClientDependency fromModuleName(String mn)
    {
        Module m = getModule(mn);

        return ClientDependency.fromModule(m);
    }

    @NotNull
    public static Supplier<ClientDependency> supplierFromModuleName(String mn)
    {
        Module m = getModule(mn);

        return ClientDependency.supplierFromModule(m);
    }

    @NotNull
    private static Module getModule(String mn)
    {
        Module m = ModuleLoader.getInstance().getModule(mn);

        if (m == null)
        {
            throw new IllegalArgumentException("Module '" + mn + "' not found, unable to create client resource");
        }
        return m;
    }

    @NotNull
    public static Supplier<ClientDependency> supplierFromModule(Module m)
    {
        return supplierFromPath(m.getName() + TYPE.context.getExtension(), ModeTypeEnum.BOTH);
    }

    @Nullable
    public static ClientDependency fromModule(Module m)
    {
        //noinspection
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

    @NotNull
    public static Supplier<ClientDependency> supplierFromXML(DependencyType type)
    {
        if (null == type || null == type.getPath())
            return () -> null;

        if (type.isSetMode())
            return supplierFromPath(type.getPath(), type.getMode());

        return supplierFromPath(type.getPath(), ModeTypeEnum.BOTH);
    }

    /**
     * Map an array of DependencyTypes (likely coming from a lib, html view, report, or module property XML file) to a set
     * of client dependency suppliers. Client dependencies can change, so cached and other long-lived objects should hold
     * onto suppliers not resolved ClientDependency objects. #40118
     * @param dependencyTypes An array of DependencyTypes
     * @param name Name of the source of the module types, to provide useful error messages
     * @return An ordered set of client dependency suppliers
     */
    public static List<Supplier<ClientDependency>> getSuppliers(DependencyType[] dependencyTypes, String name)
    {
        return Arrays.stream(dependencyTypes)
            .map(dt->{
                var supplier = supplierFromXML(dt);
                return (Supplier<ClientDependency>) ()->{
                    var cd = supplier.get();
                    if (null == cd)
                        LOG.error("Unable to load <dependency> in " + name);
                    return cd;
                };
            })
            .collect(Collectors.toList());
    }

    /**
     * Map an array of RequiredModuleTypes (likely coming from a lib, html view, or module property XML file) to a set of
     * client dependency suppliers. Client dependencies can change, so cached and other long-lived objects should hold
     * onto suppliers not resolved ClientDependency objects. #40118
     * @param moduleTypes An array of RequireModuleTypes
     * @param name Name of the source of the module types, to provide useful error messages
     * @param moduleNameFilter A Predicate that allows for validation and filtering out unwanted modules
     * @return An ordered set of client dependency suppliers
     */
    public static List<Supplier<ClientDependency>> getSuppliers(RequiredModuleType[] moduleTypes, String name, Predicate<String> moduleNameFilter)
    {
        return Arrays.stream(moduleTypes)
            .map(RequiredModuleType::getName)
            .filter(moduleNameFilter)
            .map(moduleName-> (Supplier<ClientDependency>) ()->{
                Module m = ModuleLoader.getInstance().getModule(moduleName);
                if (m != null)
                    return ClientDependency.fromModule(m);

                LOG.error("Unable to find module: '" + moduleName + "' referenced in " + name);
                return null;
            })
            .collect(Collectors.toList());
    }

    /**
     * Standard method for resolving a set of client dependency suppliers into a set of ClientDependency objects.
     * Invoke this at the point the dependencies are going to be rendered into a page.
     * @param suppliers A set of client dependency suppliers
     * @return A LinkedHashSet of ClientDependency objects
     */
    public static LinkedHashSet<ClientDependency> getClientDependencySet(List<Supplier<ClientDependency>> suppliers)
    {
        return suppliers.stream()
            .map(Supplier::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static ClientDependency fromPath(String path)
    {
        return fromPath(path, ModeTypeEnum.BOTH);
    }

    public static Supplier<ClientDependency> supplierFromPath(String path)
    {
        return supplierFromPath(path, ModeTypeEnum.BOTH);
    }

    public static Supplier<ClientDependency> supplierFromPath(String path, @NotNull ModeTypeEnum.Enum mode)
    {
        Supplier<ClientDependency> supplier;

        if (isExternalDependency(path))
        {
            var dependency = new ExternalClientDependency(path, mode);
            supplier = () -> dependency;
        }
        else
        {
            var pair = getPair(path, mode);
            supplier = () -> fromCache(pair);
        }

        return supplier;
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

    protected static @Nullable Pair<Path, ModeTypeEnum.Enum> getPair(String requestedPath, @NotNull ModeTypeEnum.Enum mode)
    {
        requestedPath = requestedPath.replaceAll("^/", "");

        // As a convenience, if no extension provided, assume it's a library
        if (StringUtils.isEmpty(FileUtil.getExtension(requestedPath)))
            requestedPath = requestedPath + TYPE.lib.getExtension();

        Path path = Path.parse(requestedPath).normalize();

        if (path == null)
        {
            LOG.error("Invalid client dependency path: " + requestedPath);
            return null;
        }

        return Pair.of(path, mode);
    }

    protected static @Nullable ClientDependency fromCache(String requestedPath, @NotNull ModeTypeEnum.Enum mode)
    {
        return fromCache(getPair(requestedPath, mode));
    }

    protected static @Nullable ClientDependency fromCache(@Nullable Pair<Path, ModeTypeEnum.Enum> pair)
    {
        return null != pair ? CACHE.get(pair) : null;
    }

    protected static String getUniqueKey(@NotNull String identifier, @NotNull ModeTypeEnum.Enum mode)
    {
        return identifier.toLowerCase() + "|" + mode.toString();
    }

    protected abstract String getUniqueKey();

    @Nullable
    public TYPE getPrimaryType()
    {
        return _primaryType;
    }

    protected @NotNull List<Supplier<ClientDependency>> getDependencySuppliers(Container c)
    {
        return Collections.emptyList();
    }

    private @NotNull Set<String> getProductionScripts(Container c, TYPE type)
    {
        return getScripts(c, type, _prodModePath, cd -> cd.get().getProductionScripts(c, type));
    }

    private @NotNull Set<String> getDevModeScripts(Container c, TYPE type)
    {
        return getScripts(c, type, _devModePath, cd -> cd.get().getDevModeScripts(c, type));
    }

    private @NotNull Set<String> getScripts(Container c, TYPE type, String path, Function<Supplier<ClientDependency>, Set<String>> function)
    {
        Set<String> scripts = new LinkedHashSet<>();
        if (_primaryType != null && _primaryType == type && path != null)
            scripts.add(path);

        getDependencySuppliers(c)
            .stream()
            .map(function)
            .forEach(scripts::addAll);

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
        return getDependencySuppliers(c)
            .stream()
            .map(Supplier::get)
            .filter(Objects::nonNull)
            .map(cd->cd.getRequiredModuleContexts(c))
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
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
