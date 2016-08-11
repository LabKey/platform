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
        String domainURI = domainKind.generateDomainURI(IssuesSchema.SCHEMA_NAME, getName(), getDomainContainer(), null);
        return PropertyService.get().getDomain(getDomainContainer(), domainURI);
    }

    static Container getDomainContainer()
    {
        return ContainerManager.getSharedContainer();
    }

    @Nullable
    DomainKind getDomainKind();
}
