package org.labkey.api.issues;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;

/**
 * Created by davebradlee on 8/3/16.
 */
public interface IssuesListDefProvider
{
    String getName();
    String getLabel();
    String getDescription();
    default Domain getDomain()
    {
        DomainKind domainKind = getDomainKind();
        if (null != domainKind)
            return PropertyService.get().getDomain(getDomainContainer(), domainKind.generateDomainURI(IssuesSchema.SCHEMA_NAME, getName(), getDomainContainer(), null));
        return null;
    }

    static Container getDomainContainer()
    {
        return ContainerManager.getSharedContainer();
    }

    @Nullable
    DomainKind getDomainKind();

    default boolean isEnabled(Container container)
    {
        return true;
    }
}
