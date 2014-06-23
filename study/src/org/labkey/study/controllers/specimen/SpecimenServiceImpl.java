/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

package org.labkey.study.controllers.specimen;

import gwt.client.org.labkey.specimen.client.SpecimenService;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.query.SpecimenTablesProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public List<String> checkRollups(
            List<GWTPropertyDescriptor> eventFields,
            List<GWTPropertyDescriptor> vialFields,
            List<GWTPropertyDescriptor> specimenFields
    )
    {
        List<String> errors = new ArrayList<>();

        // Check for rollups and report any errors
        List<PropertyDescriptor> eventProps = new ArrayList<>();
        List<PropertyDescriptor> vialProps = new ArrayList<>();
        List<PropertyDescriptor> specimenProps = new ArrayList<>();

        for (GWTPropertyDescriptor gwtProp : eventFields)
            eventProps.add(getPropFromGwtProp(gwtProp));

        for (GWTPropertyDescriptor gwtProp : vialFields)
            vialProps.add(getPropFromGwtProp(gwtProp));

        for (GWTPropertyDescriptor gwtProp : specimenFields)
            specimenProps.add(getPropFromGwtProp(gwtProp));

        Map<String, String> vialToEventNameMap = SpecimenImporter.getVialToEventNameMap(vialProps, eventProps);
        for (PropertyDescriptor prop : vialProps)
            if (!vialToEventNameMap.containsKey(prop.getName().toLowerCase()))
                errors.add("Vial field '" + prop.getName() + "' has no SpecimenEvent field that will rollup to it.");

        Map<String, String> specimenToVialNameMap = SpecimenImporter.getSpecimenToVialNameMap(specimenProps, vialProps);
        for (PropertyDescriptor prop : specimenProps)
            if (!specimenToVialNameMap.containsKey(prop.getName().toLowerCase()))
                errors.add("Specimen field '" + prop.getName() + "' has no Vial field that will rollup to it.");

        return errors;
    }

    private PropertyDescriptor getPropFromGwtProp(GWTPropertyDescriptor gwtProp)
    {
        PropertyDescriptor pd = new PropertyDescriptor();
        pd.setRangeURI(gwtProp.getRangeURI());
        pd.setConceptURI(gwtProp.getConceptURI());
        pd.setName(gwtProp.getName());
        return pd;
    }

    public boolean canUpdateSpecimenDomains()
    {
        Container c = getContainer();
        User u = getUser();
        SecurityPolicy policy = c.getPolicy();
        return policy.hasPermission(u, AdminPermission.class);
    }
}
