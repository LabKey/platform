/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.actions.PlateUploadForm;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Oct 12, 2011
 */
public class PlateBasedRunCreator<ProviderType extends AbstractPlateBasedAssayProvider> extends DefaultAssayRunCreator<ProviderType>
{
    public static final AssayDataType SAMPLE_METADATA_FILE_TYPE = new AssayDataType("SampleMetadataFile", new FileType(Arrays.asList(".xls", ".xlsx"), ".xls"));

    public PlateBasedRunCreator(ProviderType provider)
    {
        super(provider);
    }

    protected void resolveExtraRunData(ParticipantVisitResolver resolver,
                                  AssayRunUploadContext context,
                                  Map<ExpMaterial, String> inputMaterials,
                                  Map<ExpData, String> inputDatas,
                                  Map<ExpMaterial, String> outputMaterials,
                                  Map<ExpData, String> outputDatas) throws ExperimentException
    {
        Map<String, ExpMaterial> originalMaterials = new HashMap<>();
        PlateSamplePropertyHelper helper = getProvider().getSamplePropertyHelper((PlateUploadForm) context, null);
        Map<String, Map<DomainProperty, String>> materialProperties = helper.getSampleProperties(context.getRequest());
        StringBuilder resolverErrors = new StringBuilder();

        for (Map.Entry<String, Map<DomainProperty, String>> entry : materialProperties.entrySet())
        {
            Map<DomainProperty, String> properties = entry.getValue();
            String specimenID = null;
            String participantID = null;
            Double visitID = null;
            Date date = null;
            Container targetStudy = null;
            DomainProperty participantProperty = null;
            DomainProperty visitProperty = null;
            DomainProperty dateProperty = null;
            DomainProperty specimenIDProperty = null;
            DomainProperty targetStudyProperty = null;
            for (Map.Entry<DomainProperty, String> property : properties.entrySet())
            {
                if (AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(property.getKey().getName()))
                {
                    participantID = property.getValue();
                    participantProperty = property.getKey();
                }
                else if (AbstractAssayProvider.VISITID_PROPERTY_NAME.equals(property.getKey().getName()))
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
                else if (AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(property.getKey().getName()))
                {
                    specimenIDProperty = property.getKey();
                    specimenID = property.getValue();
                }
                else if (AbstractAssayProvider.DATE_PROPERTY_NAME.equals(property.getKey().getName()))
                {
                    dateProperty = property.getKey();
                    try
                    {
                        date = (Date) ConvertUtils.convert(property.getValue(), Date.class);
                    }
                    catch (ConversionException ignored) {}                }
                else if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(property.getKey().getName()))
                {
                    targetStudyProperty = property.getKey();
                    Set<Study> studies = StudyService.get().findStudy(property.getValue(), context.getUser());
                    if (!studies.isEmpty())
                    {
                        Study study = studies.iterator().next();
                        targetStudy = study != null ? study.getContainer() : null;
                    }
                }
            }

            String key = entry.getKey();
            ExpMaterial originalMaterial = null;
            if (resolver != null)
            {
                try
                {
                    ParticipantVisit pv = resolver.resolve(specimenID, participantID, visitID, date, targetStudy);
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
                    if (targetStudyProperty != null)
                        wellgroupProperties.put(targetStudyProperty, pv.getStudyContainer() != null ? "" + pv.getStudyContainer() : null);
                }
                catch (ThawListResolverException e)
                {
                    // Marshall these so we can report on all the rows which did not resolve rather than halting on first error.
                    resolverErrors.append(e.getMessage() + "\n");
                    continue;
                }
            }
            originalMaterials.put(key, originalMaterial);
        }
        if (resolverErrors.length() > 0)
            throw new ExperimentException(resolverErrors.toString());

        Map<ExpMaterial, String> newMaterials = createDerivedMaterials(context, originalMaterials, materialProperties);
        inputMaterials.putAll(newMaterials);
    }

    private Map<ExpMaterial, String> createDerivedMaterials(AssayRunUploadContext<?> context, Map<String, ExpMaterial> originalMaterials,
                                        Map<String, Map<DomainProperty, String>> materialProperties) throws ExperimentException
    {
        Map<ExpMaterial, String> derivedMaterials = new HashMap<>();
        long ms = System.currentTimeMillis();
        try
        {
            ExpData sampleMetadataFile = null;
            if (getProvider().getMetadataInputFormat(context.getProtocol()) == SampleMetadataInputFormat.FILE_BASED)
            {
                PlateSampleFilePropertyHelper helper = (PlateSampleFilePropertyHelper) getProvider().getSamplePropertyHelper((PlateUploadForm) context, null);
                File metadataFile = helper.getMetadataFile();
                sampleMetadataFile = ExperimentService.get().createData(context.getContainer(), SAMPLE_METADATA_FILE_TYPE);
                sampleMetadataFile.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(metadataFile).toURI());
                sampleMetadataFile.setName(metadataFile.getName());
                sampleMetadataFile.save(context.getUser());
            }

            Map<String, ExpMaterial> originalLsidToMaterial = new HashMap<>();
            for (Map.Entry<String, ExpMaterial> entry : originalMaterials.entrySet())
            {
                String key = entry.getKey();

                // we may need to insert multiple derived specimens with the same original specimen;
                // we use a map to allows us to reuse the obects based on lsid.
                ExpMaterial originalMaterial = entry.getValue();
                if (originalLsidToMaterial.containsKey(originalMaterial.getLSID()))
                    originalMaterial = originalLsidToMaterial.get(originalMaterial.getLSID());
                else
                    originalLsidToMaterial.put(originalMaterial.getLSID(), originalMaterial);

                Map<DomainProperty, String> properties = materialProperties.get(key);

                String domainURI = AbstractAssayProvider.getDomainURIForPrefix(context.getProtocol(), AbstractPlateBasedAssayProvider.ASSAY_DOMAIN_SAMPLE_WELLGROUP);
                ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(domainURI);
                if (sampleSet == null)
                {
                    sampleSet = ExperimentService.get().createSampleSet();
                    sampleSet.setContainer(context.getProtocol().getContainer());
                    sampleSet.setName("Input Samples: " + context.getProtocol().getName());
                    sampleSet.setLSID(domainURI);

                    Lsid.LsidBuilder sampleSetLSID = new Lsid.LsidBuilder(domainURI);
                    sampleSetLSID.setNamespacePrefix("Sample");
                    sampleSetLSID.setNamespaceSuffix(context.getProtocol().getContainer().getRowId() + "." + context.getProtocol().getName());
                    sampleSetLSID.setObjectId("");
                    String prefix = sampleSetLSID.toString();

                    sampleSet.setMaterialLSIDPrefix(prefix);
                    sampleSet.save(context.getUser());
                }

                Lsid.LsidBuilder derivedLsid = new Lsid.LsidBuilder(sampleSet.getMaterialLSIDPrefix() + "OBJECT");
                derivedLsid.setObjectId(derivedLsid.getObjectId() + "-" + key + "-" + ms);
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
                ExpMaterial derivedMaterial = ExperimentService.get().createExpMaterial(context.getContainer(), derivedLsid.build());
                derivedMaterial.setCpasType(sampleSet.getLSID());
                Map<ExpMaterial, String> originalMaterialSet = Collections.singletonMap(originalMaterial, null);
                Map<ExpMaterial, String> derivedMaterialSet = Collections.singletonMap(derivedMaterial, "PreparedMaterial");
                derivedMaterials.put(derivedMaterial, key);
                ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
                ExpRun derivationRun = ExperimentService.get().deriveSamples(originalMaterialSet, derivedMaterialSet, info, null);
                if (sampleMetadataFile != null)
                {
                    List<? extends ExpProtocolApplication> applications = derivationRun.getProtocolApplications();
                    assert applications.size() == 3 : "Expected three protocol applications in each sample derivation run.";
                    ExpProtocolApplication firstApplication = applications.get(0);
                    assert firstApplication.getApplicationType() == ExpProtocol.ApplicationType.ExperimentRun :
                            "Expected first protocol application to be of type ExperimentRun.";
                    firstApplication.addDataInput(context.getUser(), sampleMetadataFile, AbstractPlateBasedAssayProvider.SAMPLE_METADATA_INPUT_ROLE);
                    firstApplication.save(context.getUser());
                }
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
}
