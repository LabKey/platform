/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.assay.dilution;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.dilution.query.DilutionProviderSchema;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.module.Module;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AbstractPlateBasedAssayProvider;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.ThawListResolverType;
import org.labkey.api.util.Pair;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 5/1/13
 */
public abstract class AbstractDilutionAssayProvider<FormType extends DilutionRunUploadForm> extends AbstractPlateBasedAssayProvider implements DilutionAssayProvider<FormType>
{
    public AbstractDilutionAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, AssayDataType dataType, Module declaringModule)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, dataType, declaringModule);
    }

    @NotNull
    @Override
    public AssayTableMetadata getTableMetadata(@NotNull ExpProtocol protocol)
    {
        return new AssayTableMetadata(
                this,
                protocol,
                FieldKey.fromParts(DilutionDataHandler.DILUTION_INPUT_MATERIAL_DATA_PROPERTY, "Property"),
                FieldKey.fromParts("Run"),
                FieldKey.fromParts("RowId"));
    }

    public Pair<Domain, Map<DomainProperty, Object>> createRunDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = super.createRunDomain(c, user);
        Domain runDomain = result.getKey();
        boolean first = true;
        for (int i = 0; i < CUTOFF_PROPERTIES.length; i++)
        {
            DomainProperty cutoff = addProperty(runDomain, CUTOFF_PROPERTIES[i], "Cutoff Percentage (" + (i + 1) + ")",
                    PropertyType.INTEGER);
            if (first)
            {
                cutoff.setRequired(true);
                first = false;
            }
            cutoff.setShownInUpdateView(false);
            cutoff.setFormat("0.0##");
        }

        addPassThroughRunProperties(runDomain);

        Container lookupContainer = c.getProject();
        DomainProperty method = addProperty(runDomain, CURVE_FIT_METHOD_PROPERTY_NAME, CURVE_FIT_METHOD_PROPERTY_CAPTION, PropertyType.STRING);
        method.setLookup(new Lookup(lookupContainer, AssaySchema.NAME + "." + getResourceName(), DilutionProviderSchema.CURVE_FIT_METHOD_TABLE_NAME));
        method.setRequired(true);
        method.setShownInUpdateView(false);
        return result;
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createSampleWellGroupDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = super.createSampleWellGroupDomain(c, user);
        Domain sampleWellGroupDomain = result.getKey();
        addPassThroughSampleWellGroupProperties(c, sampleWellGroupDomain);

        return result;
    }

    protected abstract void addPassThroughRunProperties(Domain runDomain);
    protected abstract void addPassThroughSampleWellGroupProperties(Container c, Domain domain);

    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<String, Set<String>> domainMap = super.getRequiredDomainProperties();
        Set<String> sampleProperties = domainMap.get(ASSAY_DOMAIN_SAMPLE_WELLGROUP);
        if (sampleProperties == null)
        {
            sampleProperties = new HashSet<>();
            domainMap.put(ASSAY_DOMAIN_SAMPLE_WELLGROUP, sampleProperties);
        }
        sampleProperties.add(SAMPLE_INITIAL_DILUTION_PROPERTY_NAME);
        sampleProperties.add(SAMPLE_DILUTION_FACTOR_PROPERTY_NAME);
        sampleProperties.add(SAMPLE_METHOD_PROPERTY_NAME);

        Set<String> runProperties = domainMap.get(ExpProtocol.ASSAY_DOMAIN_RUN);
        if (runProperties == null)
        {
            runProperties = new HashSet<>();
            domainMap.put(ExpProtocol.ASSAY_DOMAIN_RUN, runProperties);
        }
        runProperties.add(CURVE_FIT_METHOD_PROPERTY_NAME);
        runProperties.add(CUTOFF_PROPERTIES[0]);

        return domainMap;
    }

    public Domain getResultsDomain(ExpProtocol protocol)
    {
        return null;
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Arrays.asList(new ParticipantVisitLookupResolverType(), new SpecimenIDLookupResolverType(), new ParticipantDateLookupResolverType(), new ThawListResolverType());
    }

    public boolean hasUsefulDetailsPage()
    {
        return true;
    }


    @Override
    public DataExchangeHandler createDataExchangeHandler()
    {
        return new DilutionDataExchangeHandler();
    }
}
