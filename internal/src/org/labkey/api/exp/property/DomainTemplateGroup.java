package org.labkey.api.exp.property;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.data.xml.domainTemplate.DomainTemplateType;
import org.labkey.data.xml.domainTemplate.TemplatesDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 1/6/16
 */
public class DomainTemplateGroup
{
    private static final Logger LOG = Logger.getLogger(DomainTemplateGroup.class);

    private static final String DIR_NAME = "domain-templates";
    private static final String SUFFIX = ".template.xml";

    private static final ModuleResourceCache<DomainTemplateGroup> CACHE = ModuleResourceCaches.create(new Path(DIR_NAME), "Domain templates", new DomainTemplateGroupCacheHandler());

    private static class DomainTemplateGroupCacheHandler implements ModuleResourceCacheHandler<String, DomainTemplateGroup>
    {
        @Override
        public boolean isResourceFile(String filename)
        {
            return filename.endsWith(SUFFIX) && filename.length() > SUFFIX.length();
        }

        @Override
        public String getResourceName(Module module, String resourceName)
        {
            String groupName = resourceName.substring(0, resourceName.length() - SUFFIX.length());
            return groupName;
        }

        @Override
        public String createCacheKey(Module module, String resourceName)
        {
            return ModuleResourceCache.createCacheKey(module, resourceName);
        }

        @Override
        public CacheLoader<String, DomainTemplateGroup> getResourceLoader()
        {
            return (key, argument) -> {
                ModuleResourceCache.CacheId id = ModuleResourceCache.parseCacheKey(key);
                Module module = id.getModule();
                String groupName = id.getName();
                Path path = new Path(DIR_NAME, groupName + SUFFIX);
                Resource resource  = module.getModuleResolver().lookup(path);
                if (resource == null)
                    return null;

                try (InputStream xmlStream = resource.getInputStream())
                {
                    if (null != xmlStream)
                    {
                        XmlOptions opts = XmlBeansUtil.getDefaultParseOptions();
                        TemplatesDocument doc = TemplatesDocument.Factory.parse(xmlStream, opts);
                        XmlBeansUtil.validateXmlDocument(doc, null);
                        return parse(module.getName(), groupName, doc);
                    }
                }
                catch (IOException e)
                {
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
                catch (XmlValidationException e)
                {
                    LOG.warn("Error parsing domain template '" + resource + "'", e);
                    return new DomainTemplateGroup(module.getName(), groupName, Collections.emptyList(), Arrays.asList(e.getMessage(), e.getDetails()));
                }
                catch (XmlException | IllegalArgumentException e)
                {
                    LOG.warn("Error parsing domain template '" + resource + "'", e);
                    return new DomainTemplateGroup(module.getName(), groupName, Collections.emptyList(), Arrays.asList(e.getMessage()));
                }

                return null;
            };
        }

        @Nullable
        @Override
        public FileSystemDirectoryListener createChainedDirectoryListener(Module module)
        {
            return null;
        }
    }

    private static DomainTemplateGroup parse(String moduleName, String groupName, TemplatesDocument doc)
    {
        List<String> errors = new ArrayList<>();
        List<DomainTemplate> templates = new ArrayList<>();
        TemplatesDocument.Templates templateGroup = doc.getTemplates();
        for (DomainTemplateType template : templateGroup.getTemplateArray())
        {
            DomainTemplate t = DomainTemplate.parse(moduleName, groupName, template);
            if (t != null)
            {
                templates.add(t);
                errors.addAll(t.getErrors());
            }
        }

        return new DomainTemplateGroup(moduleName, groupName, Collections.unmodifiableList(templates), Collections.unmodifiableList(errors));
    }

    public static DomainTemplateGroup get(Container c, String templateGroupName)
    {
        Set<Module> modules = c.getActiveModules();
        for (Module m : modules)
        {
            DomainTemplateGroup group = get(m, templateGroupName);
            if (group != null)
                return group;
        }

        return null;
    }

    public static DomainTemplateGroup get(Module module, String templateGroupName)
    {
        return CACHE.getResource(ModuleResourceCache.createCacheKey(module, templateGroupName));
    }

    public static Map<String, DomainTemplateGroup> getAllGroups(Container c)
    {
        Map<String, DomainTemplateGroup> ret = new LinkedHashMap<>();
        for (Module m : c.getActiveModules())
        {
            Collection<DomainTemplateGroup> groups = CACHE.getResources(m);
            for (DomainTemplateGroup group : groups)
            {
                String key = ModuleResourceCache.createCacheKey(m, group._groupName);
                assert group == CACHE.getResource(key);
                ret.put(key, group);
            }
        }

        return ret;
    }

    public static Map<String, DomainTemplate> getAllTemplates(Container c)
    {
        Map<String, DomainTemplate> templates = new LinkedHashMap<>();
        for (Module m : c.getActiveModules())
        {
            Collection<DomainTemplateGroup> groups = CACHE.getResources(m);
            for (DomainTemplateGroup group : groups)
            {
                String key = ModuleResourceCache.createCacheKey(m, group._groupName);
                assert group == CACHE.getResource(key);
                for (DomainTemplate t : group._templates)
                {
                    String templateKey = t.getTemplateKey();
                    assert templateKey.startsWith(key);
                    templates.put(templateKey, t);
                }
            }
        }

        return templates;
    }

    private final String _moduleName;
    private final String _groupName;
    private final List<DomainTemplate> _templates;
    private final List<String> _errors;

    private DomainTemplateGroup(String moduleName, String groupName, List<DomainTemplate> templates, List<String> errors)
    {
        _moduleName = moduleName;
        _groupName = groupName;
        _templates = templates;
        _errors = errors;
    }

    public boolean hasErrors()
    {
        return _errors != null && !_errors.isEmpty();
    }

    public List<String> getErrors()
    {
        return _errors != null ? _errors : Collections.emptyList();
    }

    public void throwErrors() throws BatchValidationException
    {
        if (_errors != null && !_errors.isEmpty())
            throw new BatchValidationException(new ValidationException(_errors.get(0)));
    }

    public List<Domain> createAndImport(Container c, User u, boolean createDomain, boolean importData) throws BatchValidationException
    {
        throwErrors();

        List<Domain> domains = new ArrayList<>(_templates.size());

        // First, create all the domains
        try (DbScope.Transaction tx = ExperimentService.get().getSchema().getScope().ensureTransaction())
        {
            for (DomainTemplate template : _templates)
            {
                Domain d = template.createAndImport(c, u, null, createDomain, false);
                domains.add(d);
            }
            tx.commit();
        }

        // Now, import all the data if present
        if (importData)
        {
            try (DbScope.Transaction tx = ExperimentService.get().getSchema().getScope().ensureTransaction())
            {
                for (int i = 0; i < _templates.size(); i++)
                {
                    DomainTemplate template = _templates.get(i);
                    Domain d = domains.get(i);
                    template.importData(d.getName(), c, u);
                }
                tx.commit();
            }
        }

        return domains;
    }


    @Nullable
    public DomainTemplate getTemplate(@NotNull String templateName)
    {
        return _templates.stream()
                .filter(t -> !t.hasErrors())
                .filter(t -> templateName.equals(t.getTemplateName()))
                .findFirst()
                .orElse(null);
    }

    public Map<String, DomainTemplate> getTemplates()
    {
        Map<String, DomainTemplate> templates = new LinkedHashMap<>();

        Module m = ModuleLoader.getInstance().getModule(_moduleName);
        String key = ModuleResourceCache.createCacheKey(m, _groupName);
        for (DomainTemplate t : _templates)
        {
            if (t.hasErrors())
                continue;

            String templateKey = t.getTemplateKey();
            assert templateKey.startsWith(key);
            templates.put(templateKey, t);
        }

        return templates;
    }
}
