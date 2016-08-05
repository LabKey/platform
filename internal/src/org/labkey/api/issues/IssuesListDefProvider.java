package org.labkey.api.issues;

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
    String SCHEMA_NAME = "issues";
    String getName();
    String getLabel();
    String getDescription();
    default Domain getDomain()
    {
        DomainKind domainKind = getDomainKind();
        String domainURI = domainKind.generateDomainURI(SCHEMA_NAME, getName(), getDomainContainer(), null);
        return PropertyService.get().getDomain(getDomainContainer(), domainURI);
    }

    static Container getDomainContainer()
    {
        return ContainerManager.getSharedContainer();
    }

    AbstractIssuesListDefDomainKind getDomainKind();

}
