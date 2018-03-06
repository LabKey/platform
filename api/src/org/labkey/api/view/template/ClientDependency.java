/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.apache.xmlbeans.XmlOptions;
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
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.clientLibrary.xml.DependenciesType;
import org.labkey.clientLibrary.xml.DependencyType;
import org.labkey.clientLibrary.xml.LibrariesDocument;
import org.labkey.clientLibrary.xml.LibraryType;
import org.labkey.clientLibrary.xml.ModeTypeEnum;
import org.labkey.clientLibrary.xml.RequiredModuleType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
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

    private final LinkedHashSet<ClientDependency> _children = new LinkedHashSet<>();
    private final TYPE _primaryType;

    private boolean _compileInProductionMode = true;
    private Module _module;
    private String _prodModePath;
    private String _devModePath;
    private String _uri;
    private Path _filePath;
    private Resource _resource;
    private ModeTypeEnum.Enum _mode = ModeTypeEnum.BOTH;

    // Allows for a ClientDependency that exists externally
    private ClientDependency(String uri)
    {
        _uri = uri;
        _primaryType = TYPE.fromString(_uri);
        if (_primaryType == null)
        {
            _log.warn("External client dependency type not recognized: " + uri);
        }
        _devModePath = _prodModePath = _uri;

        _module = null; // not related to a module
    }

    private ClientDependency(Path filePath, ModeTypeEnum.Enum mode)
    {
        if (mode != null)
            _mode = mode;

        _filePath = filePath;
        _primaryType = TYPE.fromPath(_filePath);

        if (_primaryType == null)
        {
            _log.warn("Client dependency type not recognized: " + filePath);
            return;
        }

        if (TYPE.context.equals(_primaryType))
        {
            String moduleName = FileUtil.getBaseName(_filePath.getName());
            Module m = ModuleLoader.getInstance().getModule(moduleName);
            if (m == null)
            {
                //TODO: throw exception??
                logError("Module \"" + moduleName + "\" not found, skipping script file \"" + filePath + "\".");
            }
            else
                _module = m;
        }
        else
        {
            WebdavResource r = WebdavService.get().getRootResolver().lookup(_filePath);
            //TODO: can we connect this resource back to a module, and load that module's context by default?--

            if (r == null || !r.exists())
            {
                // Allows you to run in dev mode without having the concatenated scripts built
                if (!AppProps.getInstance().isDevMode() || !_mode.equals(ModeTypeEnum.PRODUCTION))
                {
                    logError("Script file \"" + filePath + "\" not found, skipping.");
                }
            }
            else
            {
                _resource = r;
                _filePath = r.getPath(); //use canonical, case-sensitive name
                processScript(_filePath);
            }
        }
    }

    private void logError(String message)
    {
        URLHelper url = null;
        ViewContext ctx = HttpView.getRootContext();

        if (null != ctx)
            url = HttpView.getContextURLHelper();

        _log.error(message + (null != url ? " URL: " + url.getLocalURIString() : ""));
    }

    private ClientDependency(Module m)
    {
        _module = m;
        _primaryType = TYPE.context;
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

        ClientDependency cd = new ClientDependency(m);
        CacheManager.getSharedCache().put(key, cd);
        return cd;
    }

    // converts a semi-colon delimited list of dependencies into a set of
    // appropriate ClientDependency objects
    public static LinkedHashSet<ClientDependency> fromList(String dependencies)
    {
        LinkedHashSet<ClientDependency> set = new LinkedHashSet<>();
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
            cd = new ClientDependency(path);
        else
            cd = ClientDependency.fromFilePath(path, mode);

        return cd;
    }

    private static ClientDependency fromFilePath(String path, ModeTypeEnum.Enum mode)
    {
        path = path.replaceAll("^/", "");

        //as a convenience, if no extension provided, assume it's a library
        if (StringUtils.isEmpty(FileUtil.getExtension(path)))
            path = path + TYPE.lib.getExtension();

        Path filePath = Path.parse(path).normalize();

        String key = getCacheKey(filePath.toString(), mode);
        if (!AppProps.getInstance().isDevMode())
        {
            ClientDependency cached = (ClientDependency)CacheManager.getSharedCache().get(key);
            if (cached != null)
                return cached;
        }

        ClientDependency cr = new ClientDependency(filePath, mode);
        CacheManager.getSharedCache().put(key, cr);
        return cr;
    }

    private static String getCacheKey(String identifier, ModeTypeEnum.Enum mode)
    {
        return ClientDependency.class.getName() + "|" + identifier.toLowerCase() + "|" + mode.toString();
    }

    private String getUniqueKey()
    {
        if (TYPE.context.equals(_primaryType))
            return getCacheKey("moduleContext|" + _module.toString(), _mode);
        else if (_filePath != null)
            return getCacheKey(_filePath.toString(), _mode);

        return getCacheKey(_uri, _mode);
    }

    private void processScript(Path filePath)
    {
        TYPE type = TYPE.fromPath(filePath);
        if (type == null)
        {
            _log.warn("Invalid file type for resource: " + filePath);
            return;
        }

        if (TYPE.lib.equals(type))
        {
            processLib(filePath);
        }
        else
        {
            if(!_mode.equals(ModeTypeEnum.PRODUCTION))
                _devModePath = filePath.toString();

            if(!_mode.equals(ModeTypeEnum.DEV))
                _prodModePath = filePath.toString();
        }
    }

    private void processLib(Path filePath)
    {
        try
        {
            XmlOptions xmlOptions = new XmlOptions();
            Map<String,String> namespaceMap = new HashMap<>();
            namespaceMap.put("", "http://labkey.org/clientLibrary/xml/");
            xmlOptions.setLoadSubstituteNamespaces(namespaceMap);

            LibrariesDocument libDoc = LibrariesDocument.Factory.parse(_resource.getInputStream(), xmlOptions);
            boolean hasJsToCompile = false;
            boolean hasCssToCompile = false;
            if (libDoc != null && libDoc.getLibraries() != null)
            {
                LibraryType l = libDoc.getLibraries().getLibrary();

                if(l.isSetCompileInProductionMode())
                    _compileInProductionMode = l.getCompileInProductionMode();

                //dependencies first
                DependenciesType dependencies = libDoc.getLibraries().getDependencies();
                if (dependencies != null)
                {
                    for (DependencyType s : dependencies.getDependencyArray())
                    {
                        ClientDependency cd = fromXML(s);
                        if (cd != null)
                            _children.add(cd);
                        else
                            _log.error("Unable to load <dependencies> in library " + _filePath.getName());
                    }
                }

                //module contexts
                if (libDoc.getLibraries() != null && libDoc.getLibraries().isSetRequiredModuleContext())
                {
                    for (RequiredModuleType mt : libDoc.getLibraries().getRequiredModuleContext().getModuleArray())
                    {
                        Module m = ModuleLoader.getInstance().getModule(mt.getName());
                        if (m == null)
                            _log.error("Unable to find module: '" + mt.getName() + "' in library " + _filePath.getName());
                        else
                            _children.add(ClientDependency.fromModule(m));
                    }
                }

                for (DependencyType s : l.getScriptArray())
                {
                    ModeTypeEnum.Enum mode = s.isSetMode() ? s.getMode() :
                        _compileInProductionMode ? ModeTypeEnum.DEV : ModeTypeEnum.BOTH;
                    ClientDependency cr = fromPath(s.getPath(), mode);

                    if (!TYPE.lib.equals(cr.getPrimaryType()))
                        _children.add(cr);
                    else
                        _log.warn("Libraries cannot include other libraries: " + _filePath);

                    if (_compileInProductionMode && mode != ModeTypeEnum.PRODUCTION)
                    {
                        if(TYPE.js.equals(cr.getPrimaryType()))
                            hasJsToCompile = true;
                        if(TYPE.css.equals(cr.getPrimaryType()))
                            hasCssToCompile = true;
                    }
                }

                //add paths to the compiled scripts we expect to have created in the build.  these are production mode only
                if (hasJsToCompile)
                {
                    String path = filePath.toString().replaceAll(TYPE.lib.getExtension() + "$", ".min" + TYPE.js.getExtension());
                    _children.add(fromFilePath(path, ModeTypeEnum.PRODUCTION));
                }

                if (hasCssToCompile)
                {
                    String path = filePath.toString().replaceAll(TYPE.lib.getExtension() + "$", ".min" + TYPE.css.getExtension());
                    _children.add(fromFilePath(path, ModeTypeEnum.PRODUCTION));
                }
            }
        }
        catch (Exception e)
        {
            _log.error("Invalid client library XML file: " + _filePath + ". " + e.getMessage());
        }
    }

    @Nullable
    public TYPE getPrimaryType()
    {
        return _primaryType;
    }

    private LinkedHashSet<ClientDependency> getUniqueDependencySet(Container c)
    {
        LinkedHashSet<ClientDependency> cd = new LinkedHashSet<>();

        if (_children != null)
            cd.addAll(_children);

        if (TYPE.context.equals(_primaryType))
        {
            if (_module != null)
                cd.addAll(_module.getClientDependencies(c));
        }

        return cd;
    }

    private LinkedHashSet<String> getProductionScripts(Container c, TYPE type)
    {
        LinkedHashSet<String> scripts = new LinkedHashSet<>();
        if (_primaryType != null && _primaryType.equals(type) && _prodModePath != null)
            scripts.add(_prodModePath);

        LinkedHashSet<ClientDependency> cd = getUniqueDependencySet(c);
        for (ClientDependency r : cd)
            scripts.addAll(r.getProductionScripts(c, type));

        return scripts;
    }

    private LinkedHashSet<String> getDevModeScripts(Container c, TYPE type)
    {
        LinkedHashSet<String> scripts = new LinkedHashSet<>();
        if (_primaryType != null && _primaryType.equals(type) && _devModePath != null)
            scripts.add(_devModePath);

        LinkedHashSet<ClientDependency> cd = getUniqueDependencySet(c);
        for (ClientDependency r : cd)
            scripts.addAll(r.getDevModeScripts(c, type));

        return scripts;
    }

    public LinkedHashSet<String> getCssPaths(Container c)
    {
        return getCssPaths(c, AppProps.getInstance().isDevMode());
    }

    public LinkedHashSet<String> getCssPaths(Container c, boolean devMode)
    {
        if (devMode)
            return getDevModeScripts(c, TYPE.css);
        else
            return getProductionScripts(c, TYPE.css);
    }

    public LinkedHashSet<String> getJsPaths(Container c)
    {
        return getJsPaths(c, AppProps.getInstance().isDevMode());
    }

    public LinkedHashSet<String> getJsPaths(Container c, boolean devMode)
    {
        if (devMode)
            return getDevModeScripts(c, TYPE.js);
        else
            return getProductionScripts(c, TYPE.js);
    }

    public Set<Module> getRequiredModuleContexts(Container c)
    {
        HashSet<Module> modules = new HashSet<>();
        if(_module != null)
            modules.add(_module);

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
        if (o == null || !(o instanceof ClientDependency))
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
        if (_uri != null)
            return _uri;
        else if (_module != null)
            return _module.getName() + "." + _primaryType.name();
        else
            return null;
    }
}
