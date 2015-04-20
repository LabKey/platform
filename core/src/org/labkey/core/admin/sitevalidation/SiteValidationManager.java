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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.admin.sitevalidation.SiteValidationService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 */
public class SiteValidationManager implements SiteValidationService.Interface
{
    private Map<String, List<SiteValidationProvider>> siteValidators = new TreeMap<>();
    private Map<String, List<SiteValidationProvider>> containerValidators = new TreeMap<>();
    private List<SiteValidationProvider> _allContainerValidators;

    @Override
    public void registerProvider(String module, SiteValidationProvider provider)
    {
        addProvider(module, provider, provider.isSiteScope() ? siteValidators : containerValidators);
    }

    private void addProvider(String module, SiteValidationProvider provider, Map<String, List<SiteValidationProvider>> validators)
    {
        List<SiteValidationProvider> providerList = validators.get(module);
        if (null == providerList)
        {
            providerList = new ArrayList<>();
            validators.put(module, providerList);
        }
        providerList.add(provider);
    }

    @NotNull
    @Override
    public Map<String, SiteValidationResultList> runSiteScopeValidators(User u)
    {
        Map<String, SiteValidationResultList> siteResults = new LinkedHashMap<>();
        if (siteValidators.isEmpty())
        {
            SiteValidationResultList noValidators = new SiteValidationResultList();
            noValidators.addInfo("No site-wide validators have been registered.");
            siteResults.put("", noValidators);
        }
        else
        {
            for (Map.Entry<String, List<SiteValidationProvider>> entry : siteValidators.entrySet())
            {
                SiteValidationResultList moduleResults = new SiteValidationResultList();
                siteResults.put(entry.getKey(), moduleResults);
                for (SiteValidationProvider validator : entry.getValue())
                    moduleResults.addAll(validator.runValidation(ContainerManager.getRoot(), u));
            }
        }
        return siteResults;
    }

    @NotNull
    @Override
    public Map<Container, SiteValidationResultList> runContainerScopeValidators(Container topLevel, User u)
    {
        Map<Container, SiteValidationResultList> containerResults = new LinkedHashMap<>();
        if (catContainerValidatorLists().isEmpty())
        {
            SiteValidationResultList noValidators = new SiteValidationResultList();
            noValidators.addInfo("No folder validators have been registered.");
            containerResults.put(ContainerManager.getRoot(), noValidators);
        }
        else
        {
            List<Container> allChildren = ContainerManager.getAllChildren(topLevel, u);
            Collections.sort(allChildren);
            for (Container c : allChildren)
            {
                for (SiteValidationProvider validator : catContainerValidatorLists())
                {
                    if (validator.shouldRun(c, u))
                        containerResults.put(c, validator.runValidation(c, u));
                }
            }
        }
        return containerResults;
    }

    private List<SiteValidationProvider> catContainerValidatorLists()
    {
        if (null == _allContainerValidators)
        {
            _allContainerValidators = new ArrayList<>();
            for (List<SiteValidationProvider> validators : containerValidators.values())
                _allContainerValidators.addAll(validators);
        }
        return _allContainerValidators;
    }

}
