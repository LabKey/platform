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

package org.labkey.xarassay;

import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.*;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: peterhus
 * Date: Oct 11, 2007
 * Time: 11:29:58 PM
 */
public class MsFractionAssayProvider extends XarAssayProvider
{
    public static final String PROTOCOL_LSID_NAMESPACE_PREFIX = "MsFractionProtocol";
    public static final String NAME = "MS Fractions";
    public static final String FRACTION_DOMAIN_PREFIX = ExpProtocol.ASSAY_DOMAIN_PREFIX + "Fractions";
    public static final String FRACTION_SET_NAME = "FractionProperties";
    public static final String FRACTION_SET_LABEL = "If fraction properties are defined in this group, all mzXML files in the derctory will be described by fractions derived from the selected sample. ";
    public static final String FRACTION_SET_DATA_FILE_URL = "DataFile";
    public static final String FRACTION_SET_SAMPLE_LSID = "SampleLsid";

    public MsFractionAssayProvider()
    {
        super(PROTOCOL_LSID_NAMESPACE_PREFIX, RUN_LSID_NAMESPACE_PREFIX, MS_ASSAY_DATA_TYPE);
    }


    public MsFractionAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, MS_ASSAY_DATA_TYPE);
    }


    public MsFractionAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, DataType dataType)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, dataType);    //To change body of overridden methods use File | Settings | File Templates.
    }
    public String getProtocolLsidNamespacePrefix()
    {
        return PROTOCOL_LSID_NAMESPACE_PREFIX;
    }

    public String getName()
    {
        return NAME;
    }


    protected Domain createRunDomain(Container c, User user)
    {
        Domain runDomain = super.createRunDomain(c, user);
        return runDomain;
    }

    public List<Domain> createDefaultDomains(Container c, User user)
    {
        List<Domain> result = super.createDefaultDomains(c, user);
        result.add(createFractionDomain(c,user));
        return result;
    }

    @Override
    public ExpRun saveExperimentRun(AssayRunUploadContext context) throws ExperimentException
    {
        // check first if there are any per-fraction properties.  if not treat as non-fraction case
        // check the domain only to avoid making a sample set.
        String domainURI = getDomainURIForPrefix(context.getProtocol(), FRACTION_DOMAIN_PREFIX);
        PropertyDescriptor[] fractionProps = OntologyManager.getPropertiesForType(domainURI, context.getContainer());
        if (fractionProps.length==0)
        {
            return super.saveExperimentRun(context);
        }
        Container c = context.getContainer();
        ExpRun run = null;
        XarAssayForm form = (XarAssayForm) context;

        validateUpload(form);

        PipeRoot pipeRoot = getPipelineRoot(context);

        Map<ExpMaterial, String> inputMaterials = new HashMap<ExpMaterial, String>();
        Map<ExpData, String> inputDatas = new HashMap<ExpData, String>();
        Map<ExpMaterial, String> outputMaterials = new HashMap<ExpMaterial, String>();
        Map<ExpData, String> outputDatas = new HashMap<ExpData, String>();

        Map<PropertyDescriptor, String> runProperties = context.getRunProperties();
        Map<PropertyDescriptor, String> uploadSetProperties = context.getUploadSetProperties();

        Map<PropertyDescriptor, String> allProperties = new HashMap<PropertyDescriptor, String>();
        allProperties.putAll(runProperties);
        allProperties.putAll(uploadSetProperties);

        Map<String, File> mapData = form.getUploadedData();
        ArrayList<File> files = new ArrayList<File>(mapData.values());

        DbScope scope = ExperimentService.get().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        try {
            ExpSampleSet fractionSet = getFractionSampleSet(context);
            MsFractionPropertyHelper helper = new MsFractionPropertyHelper(fractionSet, files, context.getContainer(), context.getUser());

            Map<File, Map<PropertyDescriptor, String>> mapFilesToFractionProperties = helper.getSampleProperties(context.getRequest());

            if (form.getNumFilesRemaining().intValue() != mapData.size())
                throw new ExperimentException("Some data files in the directory are already described by AssayRuns.  You musst delete all existing AssayRuns in order to describe the runs as fractions of a single sample." );

            // create the fractionation run first, then tie each resulting sample to one of the mzxml files
            if (transactionOwner)
                scope.beginTransaction();

            addSampleInput(context, inputMaterials, allProperties);
            Map<File,Map<ExpMaterial,String>> mapFilesToFractions = createFractions(form, inputMaterials, mapFilesToFractionProperties);

            for (Map.Entry<File, Map<ExpMaterial, String>> fractionEntry : mapFilesToFractions.entrySet())
            {
                ArrayList<File> mzxmlFiles = new ArrayList<File>();
                mzxmlFiles.add(fractionEntry.getKey());

                Map<ExpMaterial, String> oneFraction = fractionEntry.getValue();
                outputDatas.clear();
                addMzxmlOutputs(form, outputDatas, mzxmlFiles);
                String fName = fractionEntry.getKey().getName();
                String runName = form.getName() + " (" + fName.substring(0, fName.lastIndexOf('.')) + ") ";

                // put the fraction properties on the run
                Map<PropertyDescriptor, String> fractionProperties = mapFilesToFractionProperties.get(fractionEntry.getKey());
                runProperties.putAll(fractionProperties);

                ExpRun runFraction = createSingleExpRun(form, oneFraction, inputDatas, outputMaterials, outputDatas
                        , runProperties, uploadSetProperties, runName, pipeRoot);

                //save off the first assay run generated
                if (null==run)
                    run=runFraction;
            }
            if (transactionOwner)
                scope.commitTransaction();
            return run;
        }
        catch (SQLException e)
        {
            if (transactionOwner)
                scope.rollbackTransaction();

            throw new RuntimeException(e);
        }
        finally
        {
            if (transactionOwner)
                scope.closeConnection();
        }
    }

    private Map<File,Map<ExpMaterial, String>> createFractions(XarAssayForm form
            , Map<ExpMaterial, String> inputMaterials
            , Map<File,Map<PropertyDescriptor, String>> mapFilesToFractionProperties
    ) throws ExperimentException
    {
        Map<File, Map<ExpMaterial, String>> outputFileToFractions = new HashMap<File, Map<ExpMaterial, String>>();
        Map<ExpMaterial, String> fractionMaterialSet = new HashMap<ExpMaterial, String>();

        long ms = System.currentTimeMillis();
        ExpSampleSet fractionSet = getFractionSampleSet(form);
        try
        {
            for (Map.Entry<File,Map<PropertyDescriptor, String>> entry : mapFilesToFractionProperties.entrySet())
            {
                // generate unique lsids for the derived samples
                File mzxmlFile = entry.getKey();
                String fileNameBase = mzxmlFile.getName().substring(0, (mzxmlFile.getName().lastIndexOf('.')));
                Map<PropertyDescriptor, String> properties = entry.getValue();
                Lsid derivedLsid = new Lsid(fractionSet.getMaterialLSIDPrefix() + "OBJECT");
                derivedLsid.setObjectId(derivedLsid.getObjectId() + "-" + fileNameBase + "_" + ms);
                int index = 0;
                while(ExperimentService.get().getExpMaterial(derivedLsid.toString()) != null)
                    derivedLsid.setObjectId(derivedLsid.getObjectId() + "-" + ++index);

                ExpMaterial derivedMaterial = ExperimentService.get().createExpMaterial(form.getContainer()
                        , derivedLsid.toString(), "Fraction - " + fileNameBase);
                derivedMaterial.setCpasType(fractionSet.getLSID());
                // could put the fraction properties on the fraction material object or on the run.  decided to do the run

                for (Map.Entry<PropertyDescriptor,String> property : properties.entrySet())
                {
                    String value = property.getValue();
                    derivedMaterial.setProperty(form.getUser(),property.getKey(), value);
                }

                fractionMaterialSet.put(derivedMaterial, "Fraction");
                outputFileToFractions.put(mzxmlFile, Collections.singletonMap(derivedMaterial, "Fraction"));
            }
            ViewBackgroundInfo info = new ViewBackgroundInfo(form.getContainer(), form.getUser(), form.getActionURL());
            ExpRun deriveFractionsRun = ExperimentService.get().deriveSamples(inputMaterials, fractionMaterialSet, info, null);
        }
        catch (SQLException e)
        {
            throw new ExperimentException(e);
        }
        return outputFileToFractions;
    }

    protected ExpSampleSet getFractionSampleSet(AssayRunUploadContext context) throws ExperimentException
    {
        Domain domainFractionSet = null;
        String domainURI = getDomainURIForPrefix(context.getProtocol(), FRACTION_DOMAIN_PREFIX);
        ExpSampleSet sampleSet=null;
        if (null != domainURI)
            sampleSet = ExperimentService.get().getSampleSet(domainURI);

        if (sampleSet == null)
        {
            domainFractionSet = createFractionDomain(context.getContainer(), context.getUser());
            sampleSet = ExperimentService.get().createSampleSet();
            sampleSet.setContainer(context.getProtocol().getContainer());
            sampleSet.setName("Fractions: " + context.getProtocol().getName());
            sampleSet.setLSID(domainURI);

            Lsid sampleSetLSID = new Lsid(domainURI);
            sampleSetLSID.setNamespacePrefix("Sample");
            sampleSetLSID.setNamespaceSuffix(context.getProtocol().getContainer().getRowId() + "." + context.getProtocol().getName());
            sampleSetLSID.setObjectId("");
            String prefix = sampleSetLSID.toString();

            sampleSet.setMaterialLSIDPrefix(prefix);
            sampleSet.insert(context.getUser());
        }
        return sampleSet;
    }

    public MsFractionPropertyHelper createSamplePropertyHelper(AssayRunUploadContext context, ExpProtocol protocol, ParticipantVisitResolverType filterInputsForType) throws ExperimentException
    {
        try
        {
            ExpSampleSet sampleSet = getFractionSampleSet(context);
            ArrayList<File> files = new ArrayList<File>(context.getUploadedData().values());
            MsFractionPropertyHelper helper = new MsFractionPropertyHelper(sampleSet, files, context.getContainer(), context.getUser());
            return helper;
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    protected Domain createFractionDomain(Container c, User user)
    {
        String domainLsid = getPresubstitutionLsid(FRACTION_DOMAIN_PREFIX);
        Domain fractionDomain = PropertyService.get().createDomain(c, domainLsid, FRACTION_SET_NAME);
        fractionDomain.setDescription(FRACTION_SET_LABEL);
        return fractionDomain;
    }


}
