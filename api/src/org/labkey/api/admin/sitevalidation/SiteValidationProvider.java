package org.labkey.api.admin.sitevalidation;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 */
public interface SiteValidationProvider
{
    String getName();
    String getDescription();

    /**
     *
     * Return false to indicate the validator shouldn't be run for that container.
     * Useful if we know in advance the validator isn't applicable; e.g., the
     * validator is module-dependent and that module isn't enabled in this container.
     *
     */
    boolean shouldRun(Container c, User u);
    boolean isSiteScope();
    List<SiteValidationResult> runValidation(Container c, User u);
}
