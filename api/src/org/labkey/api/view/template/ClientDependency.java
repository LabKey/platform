/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.webdav.WebdavService;
import org.labkey.clientLibrary.xml.DependencyType;
import org.labkey.clientLibrary.xml.ModeTypeEnum;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;


/**
 * User: bbimber
 * Date: 6/13/12
 * Time: 5:25 PM
 */
public class ClientDependency
{
    private static final Logger _log = Logger.getLogger(ClientDependency.class);

    public enum TYPE
    {
        js(".js"),
        css(".css"),
        sass(".sass"),
        jsb2(".jsb2"),
        context(".context"),
        lib(".lib.xml");

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

    private String _prodModePath;
    private String _devModePath;

    protected ModeTypeEnum.Enum _mode = ModeTypeEnum.BOTH;
    protected Path _filePath;

    protected ClientDependency(TYPE primaryType)
    {
        _primaryType = primaryType;
    }

    // Allows for a ClientDependency that exists externally
    protected ClientDependency(String uri, TYPE primaryType)
    {
        this(primaryType);
        _devModePath = _prodModePath = uri;
    }

    protected ClientDependency(Path filePath, ModeTypeEnum.Enum mode, TYPE primaryType)
    {
        this(primaryType);
        if (mode != null)
            _mode = mode;

        _filePath = filePath;
    }

    // TODO: Make abstract and move impl to subclass
    protected void init()
    {
        processScript(_filePath);
    }

    private static void logError(String message)
    {
        URLHelper url = null;
        ViewContext ctx = HttpView.getRootContext();

        if (null != ctx)
            url = HttpView.getContextURLHelper();

        _log.error(message + (null != url ? " URL: " + url.getLocalURIString() : ""));
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
        String key = getCacheKey("moduleContext|" + m.getName(), ModeTypeEnum.BOTH);
        if (!AppProps.getInstance().isDevMode())
        {
            ClientDependency cached = (ClientDependency)CacheManager.getSharedCache().get(key);
            if (cached != null)
                return cached;
        }

        ClientDependency cd = new ContextClientDependency(m);
        if (!AppProps.getInstance().isDevMode())
            CacheManager.getSharedCache().put(key, cd);
        return cd;
    }

    // converts a semi-colon delimited list of dependencies into a set of
    // appropriate ClientDependency objects
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


    @Deprecated
    public static ClientDependency fromFilePath(String path)
    {
        return ClientDependency.fromPath(path);
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

    public static ClientDependency fromPath(String path, ModeTypeEnum.Enum mode)
    {
        ClientDependency cd;

        if (isExternalDependency(path))
            cd = new ExternalClientDependency(path);
        else
            cd = fromFilePath(path, mode);

        return cd;
    }

    protected static @Nullable ClientDependency fromFilePath(String path, ModeTypeEnum.Enum mode)
    {
        path = path.replaceAll("^/", "");

        //as a convenience, if no extension provided, assume it's a library
        if (StringUtils.isEmpty(FileUtil.getExtension(path)))
            path = path + TYPE.lib.getExtension();

        Path filePath = Path.parse(path).normalize();

        if (filePath == null)
        {
            _log.warn("Invalid client dependency path: " + path);
            return null;
        }

        String key = getCacheKey(filePath.toString(), mode);
        if (!AppProps.getInstance().isDevMode())
        {
            ClientDependency cached = (ClientDependency)CacheManager.getSharedCache().get(key);
            if (cached != null)
                return cached;
        }

        TYPE primaryType = TYPE.fromPath(filePath);

        if (primaryType == null)
        {
            _log.warn("Client dependency type not recognized: " + filePath);
            return null;
        }

        ClientDependency cr;

        if (TYPE.context == primaryType)
        {
            String moduleName = FileUtil.getBaseName(filePath.getName());
            Module m = ModuleLoader.getInstance().getModule(moduleName);

            if (m == null)
            {
                logError("Module \"" + moduleName + "\" not found, skipping script file \"" + filePath + "\".");
                return null;
            }

            cr = new ContextClientDependency(m);
        }
        else
        {
            Resource r = WebdavService.get().getRootResolver().lookup(filePath);
            //TODO: can we connect this resource back to a module, and load that module's context by default?--

            if (r == null || !r.exists())
            {
                // Allows you to run in dev mode without having the concatenated scripts built
                if (!AppProps.getInstance().isDevMode() || !mode.equals(ModeTypeEnum.PRODUCTION))
                {
                    logError("Script file \"" + filePath + "\" not found, skipping.");
                    return null;
                }
            }

            if (TYPE.lib == primaryType)
                cr = new LibClientDependency(filePath, mode, r);
            else
                cr = new ClientDependency(filePath, mode, primaryType);
        }

        cr.init();

        if (!AppProps.getInstance().isDevMode())
            CacheManager.getSharedCache().put(key, cr);

        return cr;
    }

    protected static String getCacheKey(String identifier, ModeTypeEnum.Enum mode)
    {
        return ClientDependency.class.getName() + "|" + identifier.toLowerCase() + "|" + mode.toString();
    }

    protected String getUniqueKey()
    {
        assert _filePath != null;
        return getCacheKey(_filePath.toString(), _mode);
    }

    private void processScript(Path filePath)
    {
        TYPE type = TYPE.fromPath(filePath);

        if (type == null)
        {
            _log.warn("Invalid file type for resource: " + filePath);
            return;
        }

        handleScript(filePath);
    }

    protected void handleScript(Path filePath)
    {
        if (!_mode.equals(ModeTypeEnum.PRODUCTION))
            _devModePath = filePath.toString();

        if (!_mode.equals(ModeTypeEnum.DEV))
            _prodModePath = filePath.toString();
    }

    @Nullable
    public TYPE getPrimaryType()
    {
        return _primaryType;
    }

    protected Set<ClientDependency> getUniqueDependencySet(Container c)
    {
        return Collections.emptySet();
    }

    private Set<String> getProductionScripts(Container c, TYPE type)
    {
        Set<String> scripts = new LinkedHashSet<>();
        if (_primaryType != null && _primaryType == type && _prodModePath != null)
            scripts.add(_prodModePath);

        Set<ClientDependency> cd = getUniqueDependencySet(c);
        for (ClientDependency r : cd)
            scripts.addAll(r.getProductionScripts(c, type));

        return scripts;
    }

    private Set<String> getDevModeScripts(Container c, TYPE type)
    {
        Set<String> scripts = new LinkedHashSet<>();
        if (_primaryType != null && _primaryType == type && _devModePath != null)
            scripts.add(_devModePath);

        Set<ClientDependency> cd = getUniqueDependencySet(c);
        for (ClientDependency r : cd)
            scripts.addAll(r.getDevModeScripts(c, type));

        return scripts;
    }

    public Set<String> getCssPaths(Container c)
    {
        return getCssPaths(c, AppProps.getInstance().isDevMode());
    }

    public Set<String> getCssPaths(Container c, boolean devMode)
    {
        if (devMode)
            return getDevModeScripts(c, TYPE.css);
        else
            return getProductionScripts(c, TYPE.css);
    }

    public Set<String> getJsPaths(Container c)
    {
        return getJsPaths(c, AppProps.getInstance().isDevMode());
    }

    public Set<String> getJsPaths(Container c, boolean devMode)
    {
        if (devMode)
            return getDevModeScripts(c, TYPE.js);
        else
            return getProductionScripts(c, TYPE.js);
    }

    public Set<Module> getRequiredModuleContexts(Container c)
    {
        Set<Module> modules = new HashSet<>();

        for (ClientDependency r : getUniqueDependencySet(c))
            modules.addAll(r.getRequiredModuleContexts(c));

        return modules;
    }

    public ModeTypeEnum.Enum getMode()
    {
        return _mode;
    }

    public void setMode(ModeTypeEnum.Enum mode)
    {
        _mode = mode;
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
    public String getScriptString()
    {
        if (_filePath != null)
            return _filePath.toString();
        else
            return null;
    }
}
