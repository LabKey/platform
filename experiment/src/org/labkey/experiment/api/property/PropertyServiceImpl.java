package org.labkey.experiment.api.property;

import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.IPropertyType;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.data.Container;

import java.util.List;
import java.util.ArrayList;
import java.sql.SQLException;

public class PropertyServiceImpl implements PropertyService.Interface
{
    List<DomainKind> _domainTypes = new ArrayList<DomainKind>();


    public IPropertyType getType(Container container, String typeURI)
    {
        Domain domain = getDomain(container, typeURI);
        if (domain != null)
        {
            return domain;
        }
        return new PrimitiveType(PropertyType.getFromURI(null, typeURI));
    }

    public Domain getDomain(Container container, String domainURI)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainURI, container);
        if (dd == null)
            return null;
        return new DomainImpl(dd);
    }

    public Domain getDomain(int domainId)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainId);
        if (dd == null)
            return null;
        return new DomainImpl(dd);
    }

    public Domain createDomain(Container container, String typeURI, String name)
    {
        return new DomainImpl(container, typeURI, name);
    }

    public DomainKind getDomainKind(String typeURI)
    {
        for (DomainKind type : _domainTypes)
        {
            if (type.isDomainType(typeURI))
                return type;
        }
        return null;
    }

    public void registerDomainKind(DomainKind type)
    {
        _domainTypes.add(type);
    }

    public Domain[] getDomains(Container container)
    {
        try
        {
            DomainDescriptor[] dds = OntologyManager.getDomainDescriptors(container);
            Domain[] ret = new Domain[dds.length];
            for (int i = 0; i < dds.length; i ++)
            {
                ret[i] = new DomainImpl(dds[i]);
            }
            return ret;
        }
        catch(SQLException e)
        {
            return new Domain[0];
        }
    }
}
