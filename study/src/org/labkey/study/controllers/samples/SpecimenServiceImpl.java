/*
 * Copyright (c) 2007-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.controllers.samples;

import gwt.client.org.labkey.specimen.client.SpecimenService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.query.SpecimenTablesProvider;

import java.util.ArrayList;
import java.util.List;

public class SpecimenServiceImpl extends DomainEditorServiceBase implements SpecimenService
{
    public SpecimenServiceImpl(ViewContext context)
    {

        super(context);
    }


    @Override
    public List<GWTDomain<GWTPropertyDescriptor>> getDomainDescriptors()
    {
        SpecimenTablesProvider stp = new SpecimenTablesProvider(getContainer(), getUser(), null);
        Domain domainEvent = stp.getDomain("specimenevent",false);
        Domain domainVial = stp.getDomain("vial",false);
        Domain domainSpecimen = stp.getDomain("specimen",false);

        if (null == domainEvent || null == domainSpecimen || null == domainVial)
            throw new IllegalStateException("Expected domains to already exist.");

        List<GWTDomain<GWTPropertyDescriptor>> ret = new ArrayList<>();
        ret.add(super.getDomainDescriptor(domainEvent.getTypeURI()));
        ret.add(super.getDomainDescriptor(domainVial.getTypeURI()));
        ret.add(super.getDomainDescriptor(domainSpecimen.getTypeURI()));
        return ret;
    }


    @Override
    public List<String> updateDomainDescriptors(GWTDomain<GWTPropertyDescriptor> updateEvent, GWTDomain<GWTPropertyDescriptor> updateVial, GWTDomain<GWTPropertyDescriptor> updateSpecimen)
    {
        List<GWTDomain<GWTPropertyDescriptor>> domains = getDomainDescriptors();

        ArrayList<String> errors = new ArrayList<>();
        if (!canUpdateSpecimenDomains())
            errors.add("No permissions to edit specimen tables");
        if (!domains.get(0).getDomainURI().equals(updateEvent.getDomainURI()))
            errors.add("Domains do not match. expected: " + domains.get(0).getDomainURI());
        if (!domains.get(1).getDomainURI().equals(updateVial.getDomainURI()))
            errors.add("Domains do not match.  expected: " + domains.get(1).getDomainURI());
        if (!domains.get(2).getDomainURI().equals(updateSpecimen.getDomainURI()))
            errors.add("Domains do not match.  expected: " + domains.get(2).getDomainURI());
        if (!errors.isEmpty())
            return errors;

        try (DbScope.Transaction tx = StudySchema.getInstance().getScope().ensureTransaction())
        {
            List<String> list = super.updateDomainDescriptor(domains.get(0), updateEvent);
            if (null != list)
                errors.addAll(list);
            list = super.updateDomainDescriptor(domains.get(1), updateVial);
            if (null != list)
                errors.addAll(list);
            list = super.updateDomainDescriptor(domains.get(2), updateSpecimen);
            if (null != list)
                errors.addAll(list);

            if (errors.isEmpty())
            {
                tx.commit();
                return null;
            }
        }

        return errors;
    }


    public boolean canUpdateSpecimenDomains()
    {
        Container c = getContainer();
        User u = getUser();
        SecurityPolicy policy = c.getPolicy();
        return policy.hasPermission(u, AdminPermission.class);
    }
}
