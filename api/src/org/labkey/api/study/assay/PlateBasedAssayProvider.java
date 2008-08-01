/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.InsertView;

import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Sep 27, 2007
 * Time: 3:26:44 PM
 */
public abstract class PlateBasedAssayProvider extends AbstractAssayProvider
{
    public static final String ASSAY_DOMAIN_SAMPLE_WELLGROUP = ExpProtocol.ASSAY_DOMAIN_PREFIX + "SampleWellGroup";

    public PlateBasedAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, DataType dataType)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, dataType);
    }

    public void setPlateTemplate(Container container, ExpProtocol protocol, PlateTemplate template)
    {
        if (!isPlateBased())
            throw new IllegalStateException("Only plate-based assays may store a plate template.");
        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(protocol.retrieveObjectProperties());
        ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer().getId(),
                protocol.getLSID() + "#PlateTemplate", template.getName());
        props.put(prop.getPropertyURI(), prop);
        protocol.storeObjectProperties(props);
    }

    public PlateTemplate getPlateTemplate(Container container, ExpProtocol protocol)
    {
        ObjectProperty prop = protocol.retrieveObjectProperties().get(protocol.getLSID() + "#PlateTemplate");
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

    protected Domain createSampleWellGroupDomain(Container c, User user)
    {
        String domainLsid = getPresubstitutionLsid(ASSAY_DOMAIN_SAMPLE_WELLGROUP);
        Domain sampleWellGroupDomain = PropertyService.get().createDomain(c, domainLsid, "Sample Fields");
        sampleWellGroupDomain.setDescription("The user will be prompted to enter these properties for each of the sample well groups in their chosen plate template.");
        return sampleWellGroupDomain;
    }

    public List<Domain> createDefaultDomains(Container c, User user)
    {
        List<Domain> result = super.createDefaultDomains(c, user);
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
        Map<WellGroupTemplate, Map<PropertyDescriptor, String>> materialProperties = helper.getSampleProperties(context.getRequest());
        for (Map.Entry<WellGroupTemplate, Map<PropertyDescriptor, String>> entry : materialProperties.entrySet())
        {
            Map<PropertyDescriptor, String> properties = entry.getValue();
            String specimenID = null;
            String participantID = null;
            Double visitID = null;
            Date date = null;
            PropertyDescriptor participantProperty = null;
            PropertyDescriptor visitProperty = null;
            PropertyDescriptor specimenIDProperty = null;
            for (Map.Entry<PropertyDescriptor, String> property : properties.entrySet())
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
                        // this shouldn't ever happen if form validation is working properly
                    }
                }
                else if (SPECIMENID_PROPERTY_NAME.equals(property.getKey().getName()))
                {
                    specimenIDProperty = property.getKey();
                    specimenID = property.getValue();
                }
                else if (DATE_PROPERTY_NAME.equals(property.getKey().getName()))
                {
                    try
                    {
                        date = (Date) ConvertUtils.convert(property.getValue(), Date.class);
                    }
                    catch (ConversionException x)
                    {

                    }

                }
            }

            WellGroupTemplate wellgroup = entry.getKey();
            ExpMaterial originalMaterial = null;
            if (resolver != null)
            {
                ParticipantVisit pv = resolver.resolve(specimenID, participantID, visitID, date);
                if (pv != null)
                {
                    originalMaterial = pv.getMaterial();
                    Map<PropertyDescriptor, String> wellgroupProperties = materialProperties.get(entry.getKey());
                    if (specimenIDProperty != null)
                        wellgroupProperties.put(specimenIDProperty, pv.getSpecimenID());
                    if (participantProperty != null)
                        wellgroupProperties.put(participantProperty, pv.getParticipantID());
                    if (visitProperty != null)
                        wellgroupProperties.put(visitProperty, pv.getVisitID() != null ? "" + pv.getVisitID() : null);
                }
            }
            PropertyDescriptor targetStudyPD = getRunTargetStudyColumn(context.getProtocol());
            String targetStudyID = context.getUploadSetProperties().get(targetStudyPD);
            if (targetStudyID == null || targetStudyID.length() == 0)
                targetStudyID = context.getRunProperties().get(targetStudyPD);
            Container targetStudyContainer = null;
            if (targetStudyID != null && targetStudyID.length() > 0)
                targetStudyContainer = ContainerManager.getForId(targetStudyID);
            if (originalMaterial == null)
                originalMaterial = AbstractParticipantVisitResolver.createDummyMaterial(context.getContainer(), targetStudyContainer, specimenID, participantID, visitID);
            originalMaterials.put(wellgroup, originalMaterial);
        }
        Map<ExpMaterial, String> newMaterials = createDerivedMaterials(context, originalMaterials, materialProperties);
        inputMaterials.putAll(newMaterials);
    }

    private Map<ExpMaterial, String> createDerivedMaterials(AssayRunUploadContext context, Map<WellGroupTemplate, ExpMaterial> originalMaterials,
                                        Map<WellGroupTemplate, Map<PropertyDescriptor, String>> materialProperties) throws ExperimentException
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

                Map<PropertyDescriptor, String> properties = materialProperties.get(wellgroup);

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
                    sampleSet.insert(context.getUser());
                }

                Lsid derivedLsid = new Lsid(sampleSet.getMaterialLSIDPrefix() + "OBJECT");
                derivedLsid.setObjectId(derivedLsid.getObjectId() + "-" + wellgroup.getName() + "-" + ms);
                int index = 0;

                while(ExperimentService.get().getExpMaterial(derivedLsid.toString()) != null)
                    derivedLsid.setObjectId(derivedLsid.getObjectId() + "-" + ++index);
                ExpMaterial derivedMaterial = ExperimentService.get().createExpMaterial(context.getContainer(), derivedLsid.toString(), wellgroup.getName());
                derivedMaterial.setCpasType(sampleSet.getLSID());
                Map<ExpMaterial, String> originalMaterialSet = Collections.singletonMap(originalMaterial, null);
                Map<ExpMaterial, String> derivedMaterialSet = Collections.singletonMap(derivedMaterial, "PreparedMaterial");
                derivedMaterials.put(derivedMaterial, wellgroup.getName());
                ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
                ExperimentService.get().deriveSamples(originalMaterialSet, derivedMaterialSet, info, null);
                for (Map.Entry<PropertyDescriptor, String> propertyEntry : properties.entrySet())
                    derivedMaterial.setProperty(context.getUser(), propertyEntry.getKey(), propertyEntry.getValue());
            }
        }
        catch (SQLException e)
        {
            throw new ExperimentException(e);
        }
        return derivedMaterials;
    }


    protected void addInputMaterials(AssayRunUploadContext context, Map<ExpMaterial, String> inputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        // no-op; we'll add our materials during 'resolveExtraRunData'.
    }

    public PropertyDescriptor[] getSampleWellGroupColumns(ExpProtocol protocol)
    {
        return getPropertiesForDomainPrefix(protocol, ASSAY_DOMAIN_SAMPLE_WELLGROUP);
    }

    public PlateSamplePropertyHelper createSamplePropertyHelper(AssayRunUploadContext context, ExpProtocol protocol, ParticipantVisitResolverType filterInputsForType)
    {
        PlateTemplate template = getPlateTemplate(context.getContainer(), protocol);
        PropertyDescriptor[] allSampleProperties = getSampleWellGroupColumns(protocol);
        PropertyDescriptor[] selectedSampleProperties = allSampleProperties;
        if (filterInputsForType != null)
        {
            List<PropertyDescriptor> selected = new ArrayList<PropertyDescriptor>();
            for (PropertyDescriptor possible : allSampleProperties)
            {
                if (filterInputsForType.collectPropertyOnUpload(possible.getName(), context))
                    selected.add(possible);
            }
            selectedSampleProperties = selected.toArray(new PropertyDescriptor[selected.size()]);
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

        public boolean collectPropertyOnUpload(String propertyName, AssayRunUploadContext uploadContext)
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

        public void addHiddenFormFields(InsertView view, AssayRunUploadForm form)
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

        public boolean collectPropertyOnUpload(String propertyName, AssayRunUploadContext uploadContext)
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

        public boolean collectPropertyOnUpload(String propertyName, AssayRunUploadContext uploadContext)
        {
            return !(propertyName.equals(AbstractAssayProvider.SPECIMENID_PROPERTY_NAME) ||
                    propertyName.equals(AbstractAssayProvider.VISITID_PROPERTY_NAME));
        }
    }
}
