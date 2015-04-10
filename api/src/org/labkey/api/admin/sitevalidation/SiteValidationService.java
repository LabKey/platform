package org.labkey.api.admin.sitevalidation;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 *
 * Service for registering/running validators for site configuration, schemas, data heuristics, etc.
 *
 * Validators may declare themselves to either apply to site wide scope (e.g., a schema integrity check),
 * or to container level scope (e.g., business rules for the data visible within that container).
 *
 */
public class SiteValidationService
{
    static private Interface instance;

    public static final String MODULE_NAME = "Core";

    @NotNull
    static public Interface get()
    {
        if (instance == null)
            throw new IllegalStateException("Service has not been set.");
        return instance;
    }

    static public void setInstance(Interface impl)
    {
        if (instance != null)
            throw new IllegalStateException("Service has already been set.");
        instance = impl;
    }

    public interface Interface
    {
        void registerProvider(SiteValidationProvider provider);

        // TODO: Allow module specification?

        @NotNull
        List<SiteValidationResult> runSiteScopeValidators(User u);
        @NotNull
        Map<Container, List<SiteValidationResult>> runContainerScopeValidators(Container topLevel, User u);

    }


}
