package org.labkey.api.exp.property;

import org.labkey.api.data.Container;

public class PropertyService
{
    static private Interface instance;

    static public Interface get()
    {
        return instance;
    }

    static public void setInstance(Interface impl)
    {
        instance = impl;
    }

    public interface Interface
    {
        IPropertyType getType(Container container, String domainURI);
        Domain getDomain(Container container, String domainURI);
        Domain getDomain(int domainId);
        Domain[] getDomains(Container container);
        Domain createDomain(Container container, String typeURI, String name);
        DomainKind getDomainKind(String typeURI);
        void registerDomainKind(DomainKind type);
    }
}
