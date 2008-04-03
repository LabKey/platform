package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.exp.OntologyManager;


public class DomainForeignKey extends PropertyForeignKey
{
    public DomainForeignKey(Container container, String domainURI, QuerySchema schema)
    {
        super(OntologyManager.getPropertiesForType(domainURI, container), schema);
    }
}
