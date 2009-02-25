/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.*;
import org.labkey.api.util.GUID;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: Peter@labkey.com
 * Date: Oct 17, 2008
 * Time: 5:54:45 PM
 */


public class XarAssayProvider extends AbstractAssayProvider
{
    public static final String PROTOCOL_LSID_NAMESPACE_PREFIX = "MsBaseProtocol";
    public static final String NAME = "MS Basic";
    public static final String DATA_LSID_PREFIX = "MZXMLData";
    public static final DataType MS_ASSAY_DATA_TYPE = new DataType(DATA_LSID_PREFIX);
    private static final Logger LOG = Logger.getLogger(XarAssayProvider.class);
    public static final String PROTOCOL_LSID_OBJECTID_PREFIX = "FileType.mzXML";
    public static final String RUN_LSID_NAMESPACE_PREFIX = "ExperimentRun";
    public static final String RUN_LSID_OBJECT_ID_PREFIX = "MS2PreSearch";
    public static final String SAMPLE_PROPERTY_NAME = "SampleId";
    public static final String SAMPLE_PROPERTY_LABEL = "Source Sample";
    public static final String SAMPLE_LIST_NAME = "Samples";
    protected static String _pipelineMzXMLExt = ".mzXML";

    public XarAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, DataType dataType)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, dataType);
    }

    public XarAssayProvider()
    {
        super(PROTOCOL_LSID_NAMESPACE_PREFIX, RUN_LSID_NAMESPACE_PREFIX, MS_ASSAY_DATA_TYPE);
    }
    @Override
    protected Domain createUploadSetDomain(Container c, User user)
    {
        // don't call the standard upload set create because we don't want the target study or participant data resolver
        Domain domain = PropertyService.get().createDomain(c, getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_UPLOAD_SET), "Batch Fields");
        domain.setDescription("The user is prompted for batch properties once for each set of runs they import. The run " +
                "set is a convenience to let users set properties that seldom change in one place and import many runs " +
                "using them. This is the first step of the import process.");

        return domain;
    }


    @Override
    protected Domain createRunDomain(Container c, User user)
    {
        ExpSampleSet sourceSampleSet = null;

        try { sourceSampleSet = ExperimentService.get().ensureActiveSampleSet(c);}
        catch (SQLException e){ }

        Domain runDomain = super.createRunDomain(c, user);
        DomainProperty startingSampleProperty = addProperty(runDomain, SAMPLE_PROPERTY_NAME, SAMPLE_PROPERTY_LABEL, PropertyType.INTEGER);
        startingSampleProperty.setRequired(true);
        if(null!=sourceSampleSet)
            startingSampleProperty.setLookup(new Lookup(sourceSampleSet.getContainer(), SAMPLE_LIST_NAME, sourceSampleSet.getName()));

        return runDomain;
    }

    public List<Domain> createDefaultDomains(Container c, User user)
    {
        List<Domain> result = super.createDefaultDomains(c, user);

        // remove data properties since we don't same vthem
        String lsidName = "Data Properties";
        for (Domain d : result)
        {
            if (d.getName().equals(lsidName))
            {
                result.remove(d);
                break;
            }
        }

        return result;
    }

    public ExpRun saveExperimentRun(AssayRunUploadContext context) throws ExperimentException
    {
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

        DbScope scope = ExperimentService.get().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        try {
            // non-fraction case
            addSampleInput(context, inputMaterials, allProperties);
            List<File> files = new ArrayList<File>();
            files.add(form.getUploadedData().get(form.getCurrentFileName()));
            addMzxmlOutputs(form, outputDatas, files);

            String fName = form.getCurrentFileName();
            String runName = form.getName();
            if (null == runName)
                runName = form.getProtocol().getName() + " (" + fName.substring(0, fName.lastIndexOf('.')) + ") ";

            if (transactionOwner)
                scope.beginTransaction();

            run = createSingleExpRun(form, inputMaterials, inputDatas,  outputMaterials, outputDatas
                    , runProperties , uploadSetProperties, runName, pipeRoot);

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
    public void validateUpload(XarAssayForm form) throws ExperimentException
    {
        if ((null == form.getNumFilesRemaining()) || (form.getNumFilesRemaining().equals(0)))
            throw new ExperimentException("No more files left to describe");
    }
    protected PipeRoot getPipelineRoot (AssayRunUploadContext context) throws ExperimentException
    {
        PipeRoot pipeRoot = null;
            pipeRoot = PipelineService.get().findPipelineRoot(context.getContainer());
        if (pipeRoot == null || !NetworkDrive.exists(pipeRoot.getRootPath()))
            throw new ExperimentException("The target container must have a valid pipeline root");
        return pipeRoot;
    }



    public String getName()
    {
        return NAME;
    }

    public List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles)
    {
        List<AssayDataCollector> result = new ArrayList<AssayDataCollector>();
        if (uploadedFiles == null)
            uploadedFiles = Collections.<String, File>emptyMap();

        result.add(new XarAssayDataCollector(uploadedFiles));
        return result;
    }

    public ExpData getDataForDataRow(Object dataRowId)
    {
        throw new UnsupportedOperationException("Whoa how did i get here");

    }

    public ActionURL getUploadWizardURL(Container container, ExpProtocol protocol)
    {
        ActionURL url = new ActionURL(XarAssayUploadAction.class, container);
        url.addParameter("rowId", protocol.getRowId());

        return url;
    }

    public TableInfo createDataTable(UserSchema schema, String alias, ExpProtocol protocol)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Set<FieldKey> getParticipantIDDataKeys()
    {
        return null;
    }

    public Set<FieldKey> getVisitIDDataKeys()
    {
        return null;
    }

    public String getRunDataTableName(ExpProtocol protocol)
    {
        // use the Runs list table here so that there is a way to order columns in a run upload form
        return protocol.getName() + " Runs";
    }

    public ActionURL publish(User user, ExpProtocol protocol, Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors)
    {
        throw new UnsupportedOperationException();
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return null;
    }

    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return null;
    }

    public boolean canPublish()
    {
        return false;
    }

    public ActionURL publish(User user, ExpProtocol protocol, Container study, Set<AssayPublishKey> dataKeys, List<String> errors)
    {
        throw new UnsupportedOperationException("Publish not implemented for assay type " + getName());
    }


    protected ExpRun createSingleExpRun(XarAssayForm form
            , Map<ExpMaterial, String> inputMaterials
            , Map<ExpData, String> inputDatas
            , Map<ExpMaterial, String> outputMaterials
            , Map<ExpData, String> outputDatas
            , Map<PropertyDescriptor, String> runProperties
            , Map<PropertyDescriptor,String> uploadSetProperties
            , String runName, PipeRoot pipeRoot)  throws SQLException, ExperimentException
    {

        // user inputs from the form
        ExpRun run = ExperimentService.get().createExperimentRun(form.getContainer(), runName);
        run.setProtocol(form.getProtocol());
        run.setFilePathRoot(pipeRoot.getRootPath());
        String entityId = GUID.makeGUID();
        Lsid lsid = new Lsid(getRunLsidNamespacePrefix(), "Folder-" + form.getContainer().getRowId(),
                getRunLsidObjectIdPrefix() + "." + entityId);
        run.setLSID(lsid.toString());
        run.setComments(form.getComments());

        savePropertyObject(run.getLSID(), runProperties, form.getContainer());
        savePropertyObject(run.getLSID(), uploadSetProperties, form.getContainer());

        run = ExperimentService.get().insertSimpleExperimentRun(run,
                inputMaterials,
                inputDatas,
                outputMaterials,
                outputDatas,
                new ViewBackgroundInfo(form.getContainer(),
                        form.getUser(), form.getActionURL()), LOG);
        return run;

    }

    protected void addSampleInput(AssayRunUploadContext context
            , Map<ExpMaterial, String> inputMaterials
            , Map<PropertyDescriptor, String> allProperties) throws ExperimentException
    {
        Integer sampleRowId = null;
        for (Map.Entry<PropertyDescriptor, String> entry : allProperties.entrySet())
        {
            if (entry.getKey().getName().equals(SAMPLE_PROPERTY_NAME))
            {
                try {sampleRowId = Integer.parseInt(entry.getValue());}
                catch(Exception e) {}
            }
        }
        if (null == sampleRowId)
            throw new ExperimentException("No sample selected.");

        String roleName = SAMPLE_PROPERTY_LABEL ;
        ExpMaterial material = ExperimentService.get().getExpMaterial(sampleRowId);
        if (material == null)
            throw new ExperimentException("Unable to find selected sample.");
        inputMaterials.put(material, roleName);
        return;
    }

    protected void addMzxmlOutputs(XarAssayForm form, Map<ExpData, String> outputDatas, List<File> files) throws ExperimentException
    {

        ExpData data;
        for (File f : files)
        {
            try
            {
                data = ExperimentService.get().getExpDataByURL(f, form.getContainer());
            }
            catch (IOException e)
            {
                throw new ExperimentException(e);
            }
            if (null == data)
                data = createData(form.getContainer(), f, new DataType(_dataLSIDPrefix));

            outputDatas.put(data, "mzXML");
        }
    }// todo:  should these all be static
    public String getProtocolLsidNamespacePrefix()
    {
        return PROTOCOL_LSID_NAMESPACE_PREFIX;
    }

    public String getProtocolLsidObjectidPrefix()
    {
        return PROTOCOL_LSID_OBJECTID_PREFIX;
    }

    public String getRunLsidNamespacePrefix()
    {
        return RUN_LSID_NAMESPACE_PREFIX;
    }

    public String getRunLsidObjectIdPrefix()
    {
        return RUN_LSID_OBJECT_ID_PREFIX;
    }

    public static boolean isMzXMLFile(File file)
    {
        return file.getName().endsWith(_pipelineMzXMLExt);
    }

    public static class AnalyzeFileFilter extends PipelineProvider.FileEntryFilter
    {
        public boolean accept(File file)
        {
            // Show all mzXML files.
            if (isMzXMLFile(file))
                return true;

            return false;
        }
    }
    public String getSamplePropertyName()
    {
        return SAMPLE_PROPERTY_NAME;
    }

    public TableInfo createDataTable(QuerySchema schema, String alias, ExpProtocol protocol)
    {
        //return MsAssaySchema.getDataRowTable(schema,protocol,alias );
        return new ExpSchema(schema.getUser(), schema.getContainer()).createDatasTable(alias);
    }

    public FieldKey getParticipantIDFieldKey()
    {
        return null;
    }

    public FieldKey getVisitIDFieldKey(Container targetStudy)
    {
        return null;
    }

    public FieldKey getSpecimenIDFieldKey()
    {
        return null;
    }

    public FieldKey getRunIdFieldKeyFromDataRow()
    {
        return FieldKey.fromParts("RowId");
    }

    public FieldKey getDataRowIdFieldKey()
    {
        return FieldKey.fromParts("RowId");
    }
    protected static Map<String, XarAssayProvider> getMsBaseAssayProviders()
    {
        List<AssayProvider> ap = AssayService.get().getAssayProviders();
        Map<String, XarAssayProvider> map = new HashMap<String, XarAssayProvider>();
        for (AssayProvider ax : ap)
        {
            if (ax instanceof XarAssayProvider)
            {
                XarAssayProvider xa = (XarAssayProvider) ax;
                map.put(xa.getProtocolLsidNamespacePrefix(), xa);
            }
        }
        return map;
    }

}
