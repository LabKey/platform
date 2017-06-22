/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.core.admin.sitevalidation;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.admin.sitevalidation.SiteValidationService;
import org.labkey.api.admin.sitevalidation.SiteValidatorDescriptor;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 */
public class SiteValidationServiceImpl implements SiteValidationService
{
    private final Map<String, Set<SiteValidationProvider>> siteValidators = new ConcurrentSkipListMap<>();
    private final Map<String, Set<SiteValidationProvider>> containerValidators = new ConcurrentSkipListMap<>();

    @Override
    public void registerProvider(String module, SiteValidationProvider provider)
    {
        if (provider.isSiteScope())
        {
            Set<SiteValidationProvider> moduleSet = siteValidators.get(module);
            if (null == moduleSet)
            {
                moduleSet = new TreeSet<>();
                siteValidators.put(module, moduleSet);
            }
            moduleSet.add(provider);
        }
        else
        {
            Set<SiteValidationProvider> moduleSet = containerValidators.get(module);
            if (null == moduleSet)
            {
                moduleSet = new TreeSet<>();
                containerValidators.put(module, moduleSet);
            }
            moduleSet.add(provider);
        }
    }

    @NotNull
    @Override
    public Map<String, Map<SiteValidatorDescriptor, SiteValidationResultList>> runSiteScopeValidators(User u)
    {
        Map<String, Map<SiteValidatorDescriptor, SiteValidationResultList>> siteResults = new LinkedHashMap<>();
        Container root = ContainerManager.getRoot();
        for (Map.Entry<String, Set<SiteValidationProvider>> moduleValidators : siteValidators.entrySet())
        {
            Map<SiteValidatorDescriptor, SiteValidationResultList> moduleResults = new TreeMap<>();
            siteResults.put(moduleValidators.getKey(), moduleResults);

            for (SiteValidationProvider validator : moduleValidators.getValue())
            {
                SiteValidatorDescriptor descriptor = new SiteValidatorDescriptorImpl(validator.getName(), validator.getDescription());
                moduleResults.put(descriptor, validator.runValidation(root, u));
            }
        }
        return siteResults;
    }

    @NotNull
    @Override
    public Map<String, Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>>> runContainerScopeValidators(Container topLevel, User u)
    {
        Map<String, Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>>> allContainerResults = new LinkedHashMap<>();
        List<Container> parentList;
        if (topLevel.isRoot())
        {
            parentList = ContainerManager.getProjects();
        }
        else
        {
            parentList = Collections.singletonList(topLevel);
        }
        for (Map.Entry<String, Set<SiteValidationProvider>> moduleValidators : containerValidators.entrySet())
        {
            Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>> moduleResults = new TreeMap<>();
            allContainerResults.put(moduleValidators.getKey(), moduleResults);

            // Initialize maps for each provider
            for (SiteValidationProvider validator : moduleValidators.getValue())
            {
                SiteValidatorDescriptor descriptor = new SiteValidatorDescriptorImpl(validator.getName(), validator.getDescription());
                moduleResults.put(descriptor, new TreeMap<>());
            }

            for (Container project : parentList)
            {
                List<Container> allChildren = ContainerManager.getAllChildren(project, u);
                for (Container c : allChildren)
                {
                    moduleValidators.getValue().stream().filter(validator -> validator.shouldRun(c, u)).forEach(validator -> {
                        SiteValidationResultList resultList = validator.runValidation(c, u);
                        if (null != resultList && !resultList.getResults().isEmpty())
                        {
                            SiteValidatorDescriptor descriptor = new SiteValidatorDescriptorImpl(validator.getName(), validator.getDescription());
                            Map<String, Map<String, SiteValidationResultList>> providerResults = moduleResults.get(descriptor);
                            String projectPath = null == project.getProject() ? project.getName() : StringUtils.substringAfter(project.getProject().getPath(), "/");
                            Map<String, SiteValidationResultList> projectResults = providerResults.get(projectPath);
                            if (null == projectResults)
                            {
                                projectResults = new TreeMap<>();
                                providerResults.put(projectPath, projectResults);
                            }
                            projectResults.put(StringUtils.substringAfter(c.getPath(), "/"), resultList);
                        }
                    });
                }
            }
        }

        return allContainerResults;
    }
}
