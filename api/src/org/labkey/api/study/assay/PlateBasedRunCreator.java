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
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.study.actions.PlateUploadForm;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Oct 12, 2011
 */
public class PlateBasedRunCreator extends DefaultAssayRunCreator<AbstractPlateBasedAssayProvider>
{
    public static final AssayDataType SAMPLE_METADATA_FILE_TYPE = new AssayDataType("SampleMetadataFile", new FileType(Arrays.asList(".xls", ".xlsx"), ".xls"));

    public PlateBasedRunCreator(AbstractPlateBasedAssayProvider provider)
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
        Map<WellGroupTemplate, ExpMaterial> originalMaterials = new HashMap<WellGroupTemplate, ExpMaterial>();
        PlateSamplePropertyHelper helper = getProvider().getSamplePropertyHelper((PlateUploadForm) context, null);
        Map<WellGroupTemplate, Map<DomainProperty, String>> materialProperties = helper.getSampleProperties(context.getRequest());
        for (Map.Entry<WellGroupTemplate, Map<DomainProperty, String>> entry : materialProperties.entrySet())
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

            WellGroupTemplate wellgroup = entry.getKey();
            ExpMaterial originalMaterial = null;
            if (resolver != null)
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
            ExpData sampleMetadataFile = null;
            if (getProvider().isSampleMetadataFileBased())
            {
                PlateSampleFilePropertyHelper helper = (PlateSampleFilePropertyHelper) getProvider().getSamplePropertyHelper((PlateUploadForm) context, null);
                File metadataFile = helper.getMetadataFile();
                sampleMetadataFile = ExperimentService.get().createData(context.getContainer(), SAMPLE_METADATA_FILE_TYPE);
                sampleMetadataFile.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(metadataFile).toURI());
                sampleMetadataFile.setName(metadataFile.getName());
                sampleMetadataFile.save(context.getUser());
            }

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

                String domainURI = AbstractAssayProvider.getDomainURIForPrefix(context.getProtocol(), AbstractPlateBasedAssayProvider.ASSAY_DOMAIN_SAMPLE_WELLGROUP);
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
                ExpRun derivationRun = ExperimentService.get().deriveSamples(originalMaterialSet, derivedMaterialSet, info, null);
                if (sampleMetadataFile != null)
                {
                    ExpProtocolApplication[] applications = derivationRun.getProtocolApplications();
                    assert applications.length == 3 : "Expected three protocol applications in each sample derivation run.";
                    ExpProtocolApplication firstApplication = applications[0];
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
