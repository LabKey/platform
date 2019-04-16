package org.labkey.api.view.template;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.clientLibrary.xml.DependenciesType;
import org.labkey.clientLibrary.xml.DependencyType;
import org.labkey.clientLibrary.xml.LibrariesDocument;
import org.labkey.clientLibrary.xml.LibraryType;
import org.labkey.clientLibrary.xml.ModeTypeEnum;
import org.labkey.clientLibrary.xml.RequiredModuleType;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class LibClientDependency extends FilePathClientDependency
{
    private static final Logger _log = Logger.getLogger(LibClientDependency.class);

    private final Resource _resource;
    private final LinkedHashSet<ClientDependency> _children = new LinkedHashSet<>();

    public LibClientDependency(Path filePath, ModeTypeEnum.Enum mode, Resource r)
    {
        super(filePath, mode, TYPE.lib);
        _resource = r;
    }

    @Override
    protected Set<ClientDependency> getUniqueDependencySet(Container c)
    {
        return _children;
    }

    @Override
    protected void handleScript(Path filePath)
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
                if (libDoc.getLibraries().isSetRequiredModuleContext())
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

                LibraryType library = libDoc.getLibraries().getLibrary();

                // <library> is an optional parameter
                if (library != null)
                {
                    boolean compileInProductionMode = library.isSetCompileInProductionMode();

                    for (DependencyType s : library.getScriptArray())
                    {
                        ModeTypeEnum.Enum mode = s.isSetMode() ? s.getMode() :
                                compileInProductionMode ? ModeTypeEnum.DEV : ModeTypeEnum.BOTH;
                        ClientDependency cr = fromPath(s.getPath(), mode);

                        if (!TYPE.lib.equals(cr.getPrimaryType()))
                            _children.add(cr);
                        else
                            _log.warn("Libraries cannot include other libraries: " + _filePath);

                        if (compileInProductionMode && mode != ModeTypeEnum.PRODUCTION)
                        {
                            if (TYPE.js.equals(cr.getPrimaryType()))
                                hasJsToCompile = true;
                            if (TYPE.css.equals(cr.getPrimaryType()))
                                hasCssToCompile = true;
                        }
                    }
                }

                //add paths to the compiled scripts we expect to have created in the build.  these are production mode only
                if (hasJsToCompile)
                {
                    String path = filePath.toString().replaceAll(TYPE.lib.getExtension() + "$", ".min" + TYPE.js.getExtension());
                    _children.add(fromCache(path, ModeTypeEnum.PRODUCTION));
                }

                if (hasCssToCompile)
                {
                    String path = filePath.toString().replaceAll(TYPE.lib.getExtension() + "$", ".min" + TYPE.css.getExtension());
                    _children.add(fromCache(path, ModeTypeEnum.PRODUCTION));
                }
            }
        }
        catch (Exception e)
        {
            _log.error("Invalid client library XML file: " + _filePath + ". " + e.getMessage());
        }
    }
}
