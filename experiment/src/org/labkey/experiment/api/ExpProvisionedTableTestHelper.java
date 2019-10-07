package org.labkey.experiment.api;

import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.List;

public class ExpProvisionedTableTestHelper
{
    public Domain createMockDomain(String domainName, String domainDescription, List<GWTPropertyDescriptor> gwtProps, User user, Container c) throws ValidationException
    {
        GWTDomain mockDomain = new GWTDomain();
        mockDomain.setName(domainName);
        mockDomain.setDescription(domainDescription);
        mockDomain.setFields(gwtProps);

        return DomainUtil.createDomain("Vocabulary", mockDomain, null, c, user, domainName, null);
    }
}
