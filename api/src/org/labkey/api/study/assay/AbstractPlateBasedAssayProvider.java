/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.ViewBackgroundInfo;

import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Sep 27, 2007
 * Time: 3:26:44 PM
 */
public abstract class AbstractPlateBasedAssayProvider extends AbstractAssayProvider implements PlateBasedAssayProvider
{
    public static final String ASSAY_DOMAIN_SAMPLE_WELLGROUP = ExpProtocol.ASSAY_DOMAIN_PREFIX + "SampleWellGroup";

    public AbstractPlateBasedAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, AssayDataType dataType, AssayTableMetadata tableMetadata)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, dataType, tableMetadata);
    }

    public void setPlateTemplate(Container container, ExpProtocol protocol, PlateTemplate template)
    {
        if (!isPlateBased())
            throw new IllegalStateException("Only plate-based assays may store a plate template.");
        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(protocol.getObjectProperties());
        ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(),
                protocol.getLSID() + "#PlateTemplate", template.getName());
        props.put(prop.getPropertyURI(), prop);
        protocol.setObjectProperties(props);
    }

    public PlateTemplate getPlateTemplate(Container container, ExpProtocol protocol)
    {
        ObjectProperty prop = protocol.getObjectProperties().get(protocol.getLSID() + "#PlateTemplate");
        try
        {
            return prop != null ? PlateService.get().getPlateTemplate(protocol.getContainer(), prop.getStringValue()) : null;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public boolean isPlateBased()
    {
        return true;
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createSampleWellGroupDomain(Container c, User user)
    {
        String domainLsid = getPresubstitutionLsid(ASSAY_DOMAIN_SAMPLE_WELLGROUP);
        Domain sampleWellGroupDomain = PropertyService.get().createDomain(c, domainLsid, "Sample Fields");
        sampleWellGroupDomain.setDescription("The user will be prompted to enter these properties for each of the sample well groups in their chosen plate template.");
        return new Pair<Domain, Map<DomainProperty, Object>>(sampleWellGroupDomain, Collections.<DomainProperty, Object>emptyMap());
    }

    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = super.createDefaultDomains(c, user);
        result.add(createSampleWellGroupDomain(c, user));
        return result;
    }

    protected void resolveExtraRunData(ParticipantVisitResolver resolver,
                                  AssayRunUploadContext context,
                                  Map<ExpMaterial, String> inputMaterials,
                                  Map<ExpData, String> inputDatas,
                                  Map<ExpMaterial, String> outputMaterials,
                                  Map<ExpData, String> outputDatas) throws ExperimentException
    {
        Map<WellGroupTemplate, ExpMaterial> originalMaterials = new HashMap<WellGroupTemplate, ExpMaterial>();
        PlateSamplePropertyHelper helper = createSamplePropertyHelper(context, context.getProtocol(), null);
        Map<WellGroupTemplate, Map<DomainProperty, String>> materialProperties = helper.getSampleProperties(context.getRequest());
        for (Map.Entry<WellGroupTemplate, Map<DomainProperty, String>> entry : materialProperties.entrySet())
        {
            Map<DomainProperty, String> properties = entry.getValue();
            String specimenID = null;
            String participantID = null;
            Double visitID = null;
            Date date = null;
            DomainProperty participantProperty = null;
            DomainProperty visitProperty = null;
            DomainProperty dateProperty = null;
            DomainProperty specimenIDProperty = null;
            for (Map.Entry<DomainProperty, String> property : properties.entrySet())
            {
                if (PARTICIPANTID_PROPERTY_NAME.equals(property.getKey().getName()))
                {
                    participantID = property.getValue();
                    participantProperty = property.getKey();
                }
                else if (VISITID_PROPERTY_NAME.equals(property.getKey().getName()))
                {
                    visitProperty = property.getKey();
                    try
                    {
                        if (property.getValue() != null && property.getValue().length() > 0)
                            visitID = Double.parseDouble(property.getValue());
                    }
                    catch (NumberFormatException e)
                    {
                        assert false : "this shouldn't ever happen if form validation is working properly";
                    }
                }
                else if (SPECIMENID_PROPERTY_NAME.equals(property.getKey().getName()))
                {
                    specimenIDProperty = property.getKey();
                    specimenID = property.getValue();
                }
                else if (DATE_PROPERTY_NAME.equals(property.getKey().getName()))
                {
                    dateProperty = property.getKey();
                    try
                    {
                        date = (Date) ConvertUtils.convert(property.getValue(), Date.class);
                    }
                    catch (ConversionException x) {}
                }
            }

            WellGroupTemplate wellgroup = entry.getKey();
            ExpMaterial originalMaterial = null;
            if (resolver != null)
            {
                ParticipantVisit pv = resolver.resolve(specimenID, participantID, visitID, date);
                originalMaterial = pv.getMaterial();
                Map<DomainProperty, String> wellgroupProperties = materialProperties.get(entry.getKey());
                if (specimenIDProperty != null)
                    wellgroupProperties.put(specimenIDProperty, pv.getSpecimenID());
                if (participantProperty != null)
                    wellgroupProperties.put(participantProperty, pv.getParticipantID());
                if (visitProperty != null)
                    wellgroupProperties.put(visitProperty, pv.getVisitID() != null ? "" + pv.getVisitID() : null);
                if (dateProperty != null)
                    wellgroupProperties.put(dateProperty, pv.getDate() != null ? "" + pv.getDate() : null);
            }
            originalMaterials.put(wellgroup, originalMaterial);
        }
        Map<ExpMaterial, String> newMaterials = createDerivedMaterials(context, originalMaterials, materialProperties);
        inputMaterials.putAll(newMaterials);
    }

    private Map<ExpMaterial, String> createDerivedMaterials(AssayRunUploadContext context, Map<WellGroupTemplate, ExpMaterial> originalMaterials,
                                        Map<WellGroupTemplate, Map<DomainProperty, String>> materialProperties) throws ExperimentException
    {
        Map<ExpMaterial, String> derivedMaterials = new HashMap<ExpMaterial, String>();
        long ms = System.currentTimeMillis();
        try
        {
            Map<String, ExpMaterial> originalLsidToMaterial = new HashMap<String, ExpMaterial>();
            for (Map.Entry<WellGroupTemplate, ExpMaterial> entry : originalMaterials.entrySet())
            {
                WellGroupTemplate wellgroup = entry.getKey();

                // we may need to insert multiple derived specimens with the same original specimen;
                // we use a map to allows us to reuse the obects based on lsid.
                ExpMaterial originalMaterial = entry.getValue();
                if (originalLsidToMaterial.containsKey(originalMaterial.getLSID()))
                    originalMaterial = originalLsidToMaterial.get(originalMaterial.getLSID());
                else
                    originalLsidToMaterial.put(originalMaterial.getLSID(), originalMaterial);

                Map<DomainProperty, String> properties = materialProperties.get(wellgroup);

                String domainURI = getDomainURIForPrefix(context.getProtocol(), ASSAY_DOMAIN_SAMPLE_WELLGROUP);
                ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(domainURI);
                if (sampleSet == null)
                {
                    sampleSet = ExperimentService.get().createSampleSet();
                    sampleSet.setContainer(context.getProtocol().getContainer());
                    sampleSet.setName("Input Samples: " + context.getProtocol().getName());
                    sampleSet.setLSID(domainURI);

                    Lsid sampleSetLSID = new Lsid(domainURI);
                    sampleSetLSID.setNamespacePrefix("Sample");
                    sampleSetLSID.setNamespaceSuffix(context.getProtocol().getContainer().getRowId() + "." + context.getProtocol().getName());
                    sampleSetLSID.setObjectId("");
                    String prefix = sampleSetLSID.toString();

                    sampleSet.setMaterialLSIDPrefix(prefix);
                    sampleSet.save(context.getUser());
                }

                Lsid derivedLsid = new Lsid(sampleSet.getMaterialLSIDPrefix() + "OBJECT");
                derivedLsid.setObjectId(derivedLsid.getObjectId() + "-" + wellgroup.getName() + "-" + ms);
                int index = 0;

                if (!Lsid.isLsid(derivedLsid.toString()))
                {
                    // See bug 10643.  If we somehow end up with an invalid LSID (because we didn't properly encode a character, etc.),
                    // the call to setObjectId in the loop below will no-op, causing an infinite loop.
                    throw new ExperimentException("Unable to generate valid LSID for derived sample: " + derivedLsid.toString());
                }
                String baseObjectId = derivedLsid.getObjectId();
                while(ExperimentService.get().getExpMaterial(derivedLsid.toString()) != null)
                    derivedLsid.setObjectId(baseObjectId + "-" + ++index);
                ExpMaterial derivedMaterial = ExperimentService.get().createExpMaterial(context.getContainer(), derivedLsid.toString(), wellgroup.getName());
                derivedMaterial.setCpasType(sampleSet.getLSID());
                Map<ExpMaterial, String> originalMaterialSet = Collections.singletonMap(originalMaterial, null);
                Map<ExpMaterial, String> derivedMaterialSet = Collections.singletonMap(derivedMaterial, "PreparedMaterial");
                derivedMaterials.put(derivedMaterial, wellgroup.getName());
                ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
                ExperimentService.get().deriveSamples(originalMaterialSet, derivedMaterialSet, info, null);
                for (Map.Entry<DomainProperty, String> propertyEntry : properties.entrySet())
                    derivedMaterial.setProperty(context.getUser(), propertyEntry.getKey().getPropertyDescriptor(), propertyEntry.getValue());
            }
        }
        catch (ValidationException e)
        {
            throw new ExperimentException(e);
        }
        return derivedMaterials;
    }


    public Domain getSampleWellGroupDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ASSAY_DOMAIN_SAMPLE_WELLGROUP);
    }

    public PlateSamplePropertyHelper createSamplePropertyHelper(AssayRunUploadContext context, ExpProtocol protocol, ParticipantVisitResolverType filterInputsForType)
    {
        PlateTemplate template = getPlateTemplate(context.getContainer(), protocol);
        Domain sampleDomain = getSampleWellGroupDomain(protocol);
        DomainProperty[] allSampleProperties = sampleDomain.getProperties();
        DomainProperty[] selectedSampleProperties = allSampleProperties;
        if (filterInputsForType != null)
        {
            List<DomainProperty> selected = new ArrayList<DomainProperty>();
            for (DomainProperty possible : allSampleProperties)
            {
                if (filterInputsForType.collectPropertyOnUpload(context, possible.getName()))
                    selected.add(possible);
            }
            selectedSampleProperties = selected.toArray(new DomainProperty[selected.size()]);
        }
        return new PlateSamplePropertyHelper(selectedSampleProperties, template);
    }

    public static class SpecimenIDLookupResolverType extends StudyParticipantVisitResolverType
    {
        // null means we haven't checked the request yet to know whether
        // or not to include the data
        private Boolean includeParticipantAndVisit = null;

        private static final String INCLUDE_PARTICIPANT_AND_VISIT = "includeParticipantAndVisit";

        public String getName()
        {
            return "SpecimenID";
        }

        public String getDescription()
        {
            return "Specimen/sample id.";
        }

        public void render(RenderContext ctx) throws Exception
        {
            HtmlView view = new HtmlView(
                    "<input type=\"checkbox\" name=\"" +
                            INCLUDE_PARTICIPANT_AND_VISIT +
                            "\">I will also provide participant id and visit id");
            view.render(ctx.getRequest(), ctx.getViewContext().getResponse());
        }

        public boolean collectPropertyOnUpload(AssayRunUploadContext uploadContext, String propertyName)
        {
            if (propertyName.equals(AbstractAssayProvider.DATE_PROPERTY_NAME))
                return false;

            if (propertyName.equals(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME) ||
                propertyName.equals(AbstractAssayProvider.VISITID_PROPERTY_NAME))
            {
                if (includeParticipantAndVisit == null)
                {
                    // Need to initialize it
                    String param = uploadContext.getRequest().getParameter(INCLUDE_PARTICIPANT_AND_VISIT);
                    if ("on".equals(param))
                        includeParticipantAndVisit = Boolean.TRUE;
                    else
                        includeParticipantAndVisit = Boolean.FALSE;
                }
                return includeParticipantAndVisit.booleanValue();
            }
            return true;
        }

        public void addHiddenFormFields(AssayRunUploadContext form, InsertView view)
        {
            view.getDataRegion().addHiddenFormField(INCLUDE_PARTICIPANT_AND_VISIT,
                    form.getRequest().getParameter(INCLUDE_PARTICIPANT_AND_VISIT));
        }
    }

    public static class ParticipantVisitLookupResolverType extends StudyParticipantVisitResolverType
    {
        public String getName()
        {
            return "ParticipantVisit";
        }

        public String getDescription()
        {
            return "Participant id and visit id.";
        }

        public boolean collectPropertyOnUpload(AssayRunUploadContext uploadContext, String propertyName)
        {
            return !(propertyName.equals(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME) ||
                    propertyName.equals(AbstractAssayProvider.DATE_PROPERTY_NAME));
        }
    }

    public static class ParticipantDateLookupResolverType extends StudyParticipantVisitResolverType
    {
        public String getName()
        {
            return "ParticipantDate";
        }

        public String getDescription()
        {
            return "Participant id and date.";
        }

        public boolean collectPropertyOnUpload(AssayRunUploadContext uploadContext, String propertyName)
        {
            return !(propertyName.equals(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME) ||
                    propertyName.equals(AbstractAssayProvider.VISITID_PROPERTY_NAME));
        }
    }
}
