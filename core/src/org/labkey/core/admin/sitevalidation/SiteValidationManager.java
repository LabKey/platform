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
import org.labkey.api.admin.sitevalidation.SiteValidationResult;
import org.labkey.api.admin.sitevalidation.SiteValidationService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 */
public class SiteValidationManager implements SiteValidationService.Interface
{
    private List<SiteValidationProvider> siteValidators = new ArrayList<>();
    private List<SiteValidationProvider> containerValidators = new ArrayList<>();

    @Override
    public void registerProvider(SiteValidationProvider provider)
    {
        if (provider.isSiteScope())
            siteValidators.add(provider);
        else
            containerValidators.add(provider);
    }

    @NotNull
    @Override
    public List<SiteValidationResult> runSiteScopeValidators(User u)
    {
        List<SiteValidationResult> siteResults = new ArrayList<>();
        if (siteValidators.isEmpty())
            siteResults.add(new SiteValidationResult(SiteValidationResult.Level.INFO, "No site-wide validators have been registered."));
        else
        {
            for (SiteValidationProvider validator : siteValidators)
                siteResults.addAll(validator.runValidation(ContainerManager.getRoot(), u));
        }

        return siteResults;
    }

    @NotNull
    @Override
    public Map<Container, List<SiteValidationResult>> runContainerScopeValidators(Container topLevel, User u)
    {
        Map<Container, List<SiteValidationResult>> containerResults = new LinkedHashMap<>();
        if (containerValidators.isEmpty())
        {
            List<SiteValidationResult> noValidators = new ArrayList<>();
            noValidators.add(new SiteValidationResult(SiteValidationResult.Level.INFO, "No folder validators have been registered."));
            containerResults.put(ContainerManager.getRoot(), noValidators);
        }
        else
        {
            List<Container> allChildren = ContainerManager.getAllChildren(topLevel, u);
            Collections.sort(allChildren);
            for (Container c : allChildren)
            {
                for (SiteValidationProvider validator : containerValidators)
                {
                    if (validator.shouldRun(c, u))
                        containerResults.put(c, validator.runValidation(c, u));
                }
            }
        }
        return containerResults;
    }

}
