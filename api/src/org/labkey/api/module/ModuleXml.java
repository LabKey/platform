package org.labkey.api.module;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.data.xml.PermissionType;
import org.labkey.moduleProperties.xml.ModuleDocument;
import org.labkey.moduleProperties.xml.ModuleType;
import org.labkey.moduleProperties.xml.OptionsListType;
import org.labkey.moduleProperties.xml.PropertyType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Parsed version of a module.xml file, as defined by module.xsd. Declares module properties, module dependencies, and
 * the requiresSitePermissions flag.
 */
public class ModuleXml
{
    private static final Logger LOG = Logger.getLogger(ModuleXml.class);
    private static final String XML_FILENAME = "module.xml";

    private final Map<String, ModuleProperty> _moduleProperties = new LinkedHashMap<>();
    private final List<Supplier<ClientDependency>> _clientDependencySuppliers = new LinkedList<>();

    private boolean _requireSitePermission = false;

    public ModuleXml()
    {
    }

    public ModuleXml(Module module, Resource r)
    {
        try
        {
            XmlOptions xmlOptions = new XmlOptions();
            Map<String,String> namespaceMap = new HashMap<>();
            namespaceMap.put("", "http://labkey.org/moduleProperties/xml/");
            xmlOptions.setLoadSubstituteNamespaces(namespaceMap);

            ModuleDocument moduleDoc = ModuleDocument.Factory.parse(r.getInputStream(), xmlOptions);
            if (AppProps.getInstance().isDevMode())
            {
                try
                {
                    XmlBeansUtil.validateXmlDocument(moduleDoc, module.getName());
                }
                catch (XmlValidationException e)
                {
                    LOG.error("Module XML file failed validation for module: " + module.getName() + ". Error: " + e.getDetails());
                }
            }

            ModuleType mt = moduleDoc.getModule();
            assert null != mt : "\"module\" element is required";
            if (mt.isSetProperties())
            {
                for (PropertyType pt : mt.getProperties().getPropertyDescriptorArray())
                {
                    ModuleProperty mp;
                    if (pt.isSetName())
                        mp = new ModuleProperty(module, pt.getName());
                    else
                        continue;

                    if (pt.isSetLabel())
                        mp.setLabel(pt.getLabel());
                    if (pt.isSetCanSetPerContainer())
                        mp.setCanSetPerContainer(pt.getCanSetPerContainer());
                    if (pt.isSetExcludeFromClientContext())
                        mp.setExcludeFromClientContext(pt.getExcludeFromClientContext());
                    if (pt.isSetDefaultValue())
                        mp.setDefaultValue(pt.getDefaultValue());
                    if (pt.isSetDescription())
                        mp.setDescription(pt.getDescription());
                    if (pt.isSetShowDescriptionInline())
                        mp.setShowDescriptionInline(pt.getShowDescriptionInline());
                    if (pt.isSetInputFieldWidth())
                        mp.setInputFieldWidth(pt.getInputFieldWidth());
                    if (pt.isSetInputType())
                        mp.setInputType(ModuleProperty.InputType.valueOf(pt.getInputType().toString()));
                    if (pt.isSetOptions())
                    {
                        List<ModuleProperty.Option> options = new ArrayList<>();
                        for (OptionsListType.Option option : pt.getOptions().getOptionArray())
                        {
                            options.add(new ModuleProperty.Option(option.getDisplay(), option.getValue()));
                        }
                        mp.setOptions(options);
                    }
                    if (pt.isSetEditPermissions() && pt.getEditPermissions() != null && pt.getEditPermissions().getPermissionArray() != null)
                    {
                        List<Class<? extends Permission>> editPermissions = new ArrayList<>();
                        for (PermissionType.Enum permEntry : pt.getEditPermissions().getPermissionArray())
                        {
                            SecurityManager.PermissionTypes perm = SecurityManager.PermissionTypes.valueOf(permEntry.toString());
                            Class<? extends Permission> permClass = perm.getPermission();
                            if (permClass != null)
                                editPermissions.add(permClass);
                        }

                        if (editPermissions.size() > 0)
                            mp.setEditPermissions(editPermissions);
                    }

                    if (mp.getName() != null)
                        _moduleProperties.put(mp.getName(), mp);
                }
            }

            if (mt.isSetClientDependencies())
                _clientDependencySuppliers.addAll(ClientDependency.getSuppliers(mt.getClientDependencies().getDependencyArray(), "module.xml of " + module.getName()));

            if (mt.isSetRequiredModuleContext())
                _clientDependencySuppliers.addAll(ClientDependency.getSuppliers(mt.getRequiredModuleContext().getRequiredModuleArray(), "module.xml of " + module.getName(),
                    moduleName -> {
                        if (module.getName().equalsIgnoreCase(moduleName))
                        {
                            LOG.error("Module " + module.getName() + " lists itself as a dependency in its module.xml!");
                            return false;
                        }

                        return true;
                    }));

            if (mt.getEnableOptions() != null && mt.getEnableOptions().isSetRequireSitePermission())
            {
                _requireSitePermission = true;
            }
        }
        catch(Exception e)
        {
            LOG.error("Error trying to read and parse the metadata XML for module " + module.getName() + " from " + r.getPath(), e);
        }
    }

    public Map<String, ModuleProperty> getModuleProperties()
    {
        return _moduleProperties;
    }

    public List<Supplier<ClientDependency>> getClientDependencySuppliers()
    {
        return _clientDependencySuppliers;
    }

    public boolean getRequireSitePermission()
    {
        return _requireSitePermission;
    }

    public static class ModuleXmlCacheHandler implements ModuleResourceCacheHandler<ModuleXml>
    {
        @Override
        public ModuleXml load(Stream<? extends Resource> resources, Module module)
        {
            return resources
                .filter(r->XML_FILENAME.equalsIgnoreCase(r.getName()))
                .findFirst()
                .map(r->new ModuleXml(module, r))
                .orElseGet(ModuleXml::new);
        }
    }
}
