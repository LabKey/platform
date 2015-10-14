/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.SpecimenImporter.EventVialRollup;
import org.labkey.study.importer.SpecimenImporter.RollupInstance;
import org.labkey.study.importer.SpecimenImporter.VialSpecimenRollup;
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
            errors.addAll(list);
            list = super.updateDomainDescriptor(domains.get(1), updateVial);
            errors.addAll(list);
            list = super.updateDomainDescriptor(domains.get(2), updateSpecimen);
            errors.addAll(list);

            if (errors.isEmpty())
            {
                tx.commit();
                return errors;
            }
        }

        return errors;
    }

    public List<List<String>> checkRollups(
            List<GWTPropertyDescriptor> eventFields,            // all of these are nonBase properties
            List<GWTPropertyDescriptor> vialFields,             // all of these are nonBase properties
            List<GWTPropertyDescriptor> specimenFields          // all of these are nonBase properties
    )
    {
        // Resulting list has errors, followed by a null String followed by warnings
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<PropertyDescriptor> eventProps = new ArrayList<>();
        List<PropertyDescriptor> vialProps = new ArrayList<>();
        List<PropertyDescriptor> specimenProps = new ArrayList<>();

        for (GWTPropertyDescriptor gwtProp : eventFields)
            eventProps.add(getPropFromGwtProp(gwtProp));

        for (GWTPropertyDescriptor gwtProp : vialFields)
            vialProps.add(getPropFromGwtProp(gwtProp));

        for (GWTPropertyDescriptor gwtProp : specimenFields)
            specimenProps.add(getPropFromGwtProp(gwtProp));

        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(getContainer(), getUser(), null);
        Domain eventDomain = specimenTablesProvider.getDomain("SpecimenEvent", false);
        if (null != eventDomain)
        {   // Consider that rollups can come from base properties
            for (DomainProperty domainProperty : eventDomain.getBaseProperties())
                eventProps.add(domainProperty.getPropertyDescriptor());
        }
        CaseInsensitiveHashSet eventFieldNamesDisallowedForRollups = SpecimenImporter.getEventFieldNamesDisallowedForRollups();
        Map<String, Pair<String, RollupInstance<EventVialRollup>>> vialToEventNameMap = SpecimenImporter.getVialToEventNameMap(vialProps, eventProps);     // includes rollups with type mismatches
        for (PropertyDescriptor prop : vialProps)
        {
            Pair<String, RollupInstance<EventVialRollup>> eventPair = vialToEventNameMap.get(prop.getName().toLowerCase());
            if (null != eventPair)
            {
                String eventFieldName = eventPair.first;
                if (eventFieldNamesDisallowedForRollups.contains(eventFieldName))
                    errors.add("You may not rollup from SpecimenEvent field '" + eventFieldName + "'.");
                else if (!eventPair.second.isTypeConstraintMet())
                    errors.add("SpecimenEvent field '" + eventFieldName + "' would rollup to '" + prop.getName() + "' except the type constraint is not met.");
            }
            else
                warnings.add("Vial field '" + prop.getName() + "' has no SpecimenEvent field that will rollup to it.");
        }

        Domain vialDomain = specimenTablesProvider.getDomain("Vial", false);
        if (null != vialDomain)
        {   // Consider that rollups can come from base properties
            for (DomainProperty domainProperty : vialDomain.getBaseProperties())
                vialProps.add(domainProperty.getPropertyDescriptor());
        }
        CaseInsensitiveHashSet vialFieldNamesDisallowedForRollups = SpecimenImporter.getVialFieldNamesDisallowedForRollups();
        Map<String, Pair<String, RollupInstance<VialSpecimenRollup>>> specimenToVialNameMap = SpecimenImporter.getSpecimenToVialNameMap(specimenProps, vialProps);     // includes rollups with type mismatches
        for (PropertyDescriptor prop : specimenProps)
        {
            Pair<String, RollupInstance<VialSpecimenRollup>> vialPair = specimenToVialNameMap.get(prop.getName().toLowerCase());
            if (null != vialPair)
            {
                String vialFieldName = vialPair.first;
                if (vialFieldNamesDisallowedForRollups.contains(vialFieldName))
                    errors.add("You may not rollup from Vial field '" + vialFieldName + "'.");
                else if (!vialPair.second.isTypeConstraintMet())
                    errors.add("Vial field '" + vialFieldName + "' would rollup to '" + prop.getName() + "' except the type constraint is not met.");
            }
            else
                warnings.add("Specimen field '" + prop.getName() + "' has no Vial field that will rollup to it.");
        }

        List<List<String>> result = new ArrayList<>();
        result.add(errors);
        result.add(warnings);
        return result;
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
