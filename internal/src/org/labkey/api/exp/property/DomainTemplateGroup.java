/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.exp.property;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCacheHandler;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ResourceRootProvider;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: kevink
 * Date: 1/6/16
 */
public class DomainTemplateGroup
{
    private static final Logger LOG = Logger.getLogger(DomainTemplateGroup.class);
    private static final String DIR_NAME = "domain-templates";
    private static final String SUFFIX = ".template.xml";
    private static final ModuleResourceCache<Map<String, DomainTemplateGroup>> CACHE = ModuleResourceCaches.create("Domain templates", new DomainTemplateGroupCacheHandler(), ResourceRootProvider.getStandard(new Path(DIR_NAME)));

    private static class DomainTemplateGroupCacheHandler implements ModuleResourceCacheHandler<Map<String, DomainTemplateGroup>>
    {
        @Override
        public Map<String, DomainTemplateGroup> load(Stream<? extends Resource> resources, Module module)
        {
            Map<String, DomainTemplateGroup> map = new HashMap<>();

            resources
                .filter(getFilter(SUFFIX))
                .forEach(resource -> {
                    String groupName = getGroupName(resource);
                    String key = getGroupId(module, groupName);
                    DomainTemplateGroup domainTemplateGroup = getDomainTemplateGroup(module, resource, groupName);
                    if (null != domainTemplateGroup)
                        map.put(key, domainTemplateGroup);
                });

            return unmodifiable(map);
        }

        private String getGroupName(Resource resource)
        {
            String name = resource.getName();
            return name.substring(0, name.length() - SUFFIX.length());
        }

        private String getGroupId(Module module, String groupName)
        {
            return ModuleResourceCaches.createCacheKey(module, groupName);
        }

        private @Nullable DomainTemplateGroup getDomainTemplateGroup(Module module, Resource resource, String groupName)
        {
            try (InputStream xmlStream = resource.getInputStream())
            {
                if (null != xmlStream)
                {
                    XmlOptions opts = XmlBeansUtil.getDefaultParseOptions();
                    TemplatesDocument doc = TemplatesDocument.Factory.parse(xmlStream, opts);
                    XmlBeansUtil.validateXmlDocument(doc, null);
                    DomainTemplateGroup group = parse(module.getName(), groupName, doc);
                    if (group.hasErrors())
                    {
                        LOG.warn("Error parsing domain template '" + groupName + "' in module '" + module.getName() + "'");
                        group.getErrors().forEach(LOG::warn);
                    }
                    return group;
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
                    t.getErrors().forEach(error -> errors.add(t.getTemplateName() + ": " + error));
                }
            }

            return new DomainTemplateGroup(moduleName, groupName, Collections.unmodifiableList(templates), Collections.unmodifiableList(errors));
        }
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
        String key = ModuleResourceCaches.createCacheKey(module, templateGroupName);
        return CACHE.getResourceMap(module).get(key);
    }

    public static Map<String, DomainTemplateGroup> getAllGroups(Container c)
    {
        return CACHE.streamResourceMaps(c)     // Stream of Map<String, DomainTemplateGroup>
            .map(Map::entrySet)                  // Stream of Set<Entry<String, DomainTemplateGroup>>
            .flatMap(Collection::stream)         // Stream of Entry<String, DomainTemplateGroup>
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    public static Map<String, DomainTemplate> getAllTemplates(Container c)
    {
        return CACHE.streamResourceMaps(c)     // Stream of Map<String, DomainTemplateGroup>
            .map(Map::values)                    // Stream of Collection<DomainTemplateGroup>
            .flatMap(Collection::stream)         // Stream of DomainTemplateGroup
            .map(group -> group._templates)      // Stream of Collection<DomainTemplate>
            .flatMap(Collection::stream)         // Stream of DomainTemplate
            .collect(Collectors.toMap(DomainTemplate::getTemplateKey, Function.identity()));
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


    /**
     * Get the <b>first</b> template that matches the template name, regardless of the intended domain kind.
     * If the domain template group has two templates of the same name, but of different kinds, use
     * the {@link #getTemplate(String, String, boolean)} method instead.
     */
    @Nullable
    public DomainTemplate getTemplate(@NotNull String templateName)
    {
        return getTemplate(templateName, null, false);
    }

    /**
     * Get the template that matches the template name and kind.  If a template has a parse error it
     * won't be returned unless <code>includeErrors</code> is true.
     */
    @Nullable
    public DomainTemplate getTemplate(@NotNull String templateName, @Nullable String kind, boolean includeErrors)
    {
        return _templates.stream()
            .filter(t -> includeErrors || !t.hasErrors())
            .filter(t -> kind == null || kind.equalsIgnoreCase(t.getDomainKind()))
            .filter(t -> templateName.equals(t.getTemplateName()))
            .findFirst()
            .orElse(null);
    }

    public Map<String, DomainTemplate> getTemplates()
    {
        Map<String, DomainTemplate> templates = new LinkedHashMap<>();

        Module m = ModuleLoader.getInstance().getModule(_moduleName);
        String key = ModuleResourceCaches.createCacheKey(m, _groupName);
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

    public static class TestCase extends Assert
    {
        @Test
        public void testDomainTemplateCache()
        {
            // Load all the DomainTemplateGroups to ensure no exceptions and get a count
            int templateCount = CACHE.streamAllResourceMaps()
                .mapToInt(Map::size)
                .sum();

            LOG.info(templateCount + " domain templates defined in all modules");

            // Make sure the cache retrieves the expected number of domain templates in the simpletest module, if present

            Module simpleTest = ModuleLoader.getInstance().getModule("simpletest");

            if (null != simpleTest)
                assertEquals("Domain templates from the simpletest module", 2, CACHE.getResourceMap(simpleTest).size());
        }
    }
}
