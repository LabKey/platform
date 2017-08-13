package org.labkey.api.query;

import org.labkey.api.data.Container;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by marty on 8/2/2017.
 */
public abstract class ExtendedTableDomainKind extends SimpleTableDomainKind
{
    protected abstract String getSchemaName();
    protected abstract String getNamespacePrefix();

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public Domain createDomain(GWTDomain gwtDomain, Map<String, Object> arguments, Container container, User user, TemplateInfo templateInfo)
    {
        if (gwtDomain.getName() == null)
            throw new IllegalArgumentException("table name is required");

        String domainURI = generateDomainURI(getSchemaName(), gwtDomain.getName(), container, user);
        Domain domain = PropertyService.get().getDomain(container, domainURI);

        if( null != domain )
        {
            GWTDomain existingDomain = DomainUtil.getDomainDescriptor(user, domainURI, container);
            GWTDomain updatedDomain = new GWTDomain(existingDomain);
            updatedDomain.setFields(gwtDomain.getFields());

            List<String> errors = updateDomain(existingDomain, updatedDomain, container, user);
            if(errors.size() > 0)
            {
                throw new RuntimeException(errors.get(0));
            }
        }
        else
        {
            List<GWTPropertyDescriptor> properties = gwtDomain.getFields();
            domain = PropertyService.get().createDomain(container, domainURI, gwtDomain.getName(), templateInfo);

            Set<String> propertyUris = new HashSet<>();
            Map<DomainProperty, Object> defaultValues = new HashMap<>();
            try
            {
                for (GWTPropertyDescriptor pd : properties)
                {
                    DomainUtil.addProperty(domain, pd, defaultValues, propertyUris, null);
                }
                domain.save(user);
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw new RuntimeException(e);
            }
        }
        return domain;
    }

    @Override
    public Handler.Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(getNamespacePrefix()) ? Handler.Priority.MEDIUM : null;
    }
}
