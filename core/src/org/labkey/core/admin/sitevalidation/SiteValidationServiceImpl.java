/*
 * Copyright (c) 2015 LabKey Corporation
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
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.admin.sitevalidation.SiteValidationService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 */
public class SiteValidationServiceImpl implements SiteValidationService
{
    private final Set<Pair<String, SiteValidationProvider>> siteValidators = new ConcurrentSkipListSet<>();
    private final Set<Pair<String, SiteValidationProvider>> containerValidators = new ConcurrentSkipListSet<>();

    @Override
    public void registerProvider(String module, SiteValidationProvider provider)
    {
        if (provider.isSiteScope())
            siteValidators.add(Pair.of(module, provider));
        else
            containerValidators.add(Pair.of(module, provider));
    }

    @NotNull
    @Override
    public Map<String, SiteValidationResultList> runSiteScopeValidators(User u)
    {
        Map<String, SiteValidationResultList> siteResults = new LinkedHashMap<>();
        if (hasSiteValidators())
        {
            for (Pair<String, SiteValidationProvider> pair : siteValidators)
            {
                SiteValidationResultList moduleResults = siteResults.get(pair.getKey());
                if (null == moduleResults)
                {
                    moduleResults = new SiteValidationResultList();
                    siteResults.put(pair.getKey(), moduleResults);
                }
                moduleResults.addAll(pair.getValue().runValidation(ContainerManager.getRoot(), u));
            }
        }
        return siteResults;
    }

    @NotNull
    @Override
    public Map<String, Map<String, SiteValidationResultList>> runContainerScopeValidators(Container topLevel, User u)
    {
        Map<String, Map<String, SiteValidationResultList>> containerResults = new LinkedHashMap<>();
        if (hasContainerValidators())
        {
            if (null == topLevel.getProject()) // it's root, validate all projects
            {
                List<Container> projects = ContainerManager.getProjects();
                for (Container project : projects)
                {
                    Map<String, SiteValidationResultList> projectResults = validateSubtree(project, u);
                    if (!projectResults.isEmpty())
                        containerResults.put(StringUtils.substringAfter(project.getPath(), "/"), projectResults);
                }
            }
            else
            {
                Map<String, SiteValidationResultList> subtreeResults = validateSubtree(topLevel, u);
                if (!subtreeResults.isEmpty())
                    containerResults.put(StringUtils.substringAfter(topLevel.getProject().getPath(), "/"), subtreeResults);
            }
        }
        return containerResults;
    }

    private Map<String, SiteValidationResultList> validateSubtree(Container topLevel, User u)
    {
        Map<String, SiteValidationResultList> subtreeResults = new LinkedHashMap<>();
        List<Container> allChildren = ContainerManager.getAllChildren(topLevel, u);
        Collections.sort(allChildren);
        for (Container c : allChildren)
        {
            containerValidators.stream().filter(pair -> pair.getValue().shouldRun(c, u)).forEach(pair -> {

                SiteValidationResultList resultList = pair.getValue().runValidation(c, u);
                if (null != resultList)
                    subtreeResults.put(StringUtils.substringAfter(c.getPath(), "/"), resultList);
            });
        }
        return subtreeResults;
    }

    @Override
    public boolean hasSiteValidators()
    {
        return !siteValidators.isEmpty();
    }

    @Override
    public boolean hasContainerValidators()
    {
        return !containerValidators.isEmpty();
    }
}
