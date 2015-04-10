package org.labkey.core.admin.sitevalidation;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationResult;
import org.labkey.api.admin.sitevalidation.SiteValidationService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.HashMap;
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
        Map<Container, List<SiteValidationResult>> containerResults = new HashMap<>();
        if (containerValidators.isEmpty())
        {
            List<SiteValidationResult> noValidators = new ArrayList<>();
            noValidators.add(new SiteValidationResult(SiteValidationResult.Level.INFO, "No folder validators have been registered."));
            containerResults.put(ContainerManager.getRoot(), noValidators);
        }
        else
        {
            for (Container c : ContainerManager.getAllChildren(topLevel, u))
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
