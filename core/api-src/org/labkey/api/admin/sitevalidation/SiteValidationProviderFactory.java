package org.labkey.api.admin.sitevalidation;

/**
 * We register a factory that creates a new validation provider on each run. This gives the provider an opportunity
 * to initialize state before being called on every container. The provider could, for example, execute a single
 * cross-container query instead of one query per container.
 */
public interface SiteValidationProviderFactory extends SiteValidatorDescriptor
{
    /**
     * Return true to indicate this is a site-wide validator.
     * False to indicate the validator should only run at container scope
     */
    default boolean isSiteScope()
    {
        return false;
    }

    SiteValidationProvider getSiteValidationProvider();
}
