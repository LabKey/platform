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

package org.labkey.api.study.assay;

import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.actions.DesignerAction;
import org.labkey.api.study.actions.AssayRunDetailsAction;
import org.labkey.api.study.actions.AssayResultDetailsAction;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.reports.ExternalScriptEngine;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.common.util.Pair;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.Bindings;
import javax.script.ScriptContext;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URI;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

/**
 * User: jeckels
 * Date: Sep 14, 2007
 */
public abstract class AbstractAssayProvider implements AssayProvider
{
    private static final Logger LOG = Logger.getLogger(AbstractAssayProvider.class);

    public static final String ASSAY_NAME_SUBSTITUTION = "${AssayName}";
    public static final String TARGET_STUDY_PROPERTY_NAME = "TargetStudy";
    public static final String TARGET_STUDY_PROPERTY_CAPTION = "Target Study";

    public static final String PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME = "ParticipantVisitResolver";
    public static final String PARTICIPANT_VISIT_RESOLVER_PROPERTY_CAPTION = "Participant Visit Resolver";

    public static final String PARTICIPANTID_PROPERTY_NAME = "ParticipantID";
    public static final String VISITID_PROPERTY_NAME = "VisitID";
    public static final String PARTICIPANTID_PROPERTY_CAPTION = "Participant ID";
    public static final String VISITID_PROPERTY_CAPTION = "Visit ID";
    public static final String SPECIMENID_PROPERTY_NAME = "SpecimenID";
    public static final String SPECIMENID_PROPERTY_CAPTION = "Specimen ID";
    public static final String DATE_PROPERTY_NAME = "Date";
    public static final String DATE_PROPERTY_CAPTION = "Date";

    public static final String IMPORT_DATA_LINK_NAME = "Import Data";

    public static final FieldKey BATCH_ROWID_FROM_RUN = FieldKey.fromParts("Batch", "RowId");

    protected final String _protocolLSIDPrefix;
    protected final String _runLSIDPrefix;
    protected final DataType _dataType;

    public AbstractAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, DataType dataType)
    {
        _dataType = dataType;
        _protocolLSIDPrefix = protocolLSIDPrefix;
        _runLSIDPrefix = runLSIDPrefix;
        registerLsidHandler();
    }

    private void setStandardPropertyAttributes(TableInfo runTable, String runTableColName, PropertyDescriptor studyPd)
    {
        ColumnInfo runColumn = runTable.getColumn(runTableColName);
        studyPd.setLabel(runColumn.getCaption());
        studyPd.setDescription(runColumn.getDescription());
    }

    protected void addStandardRunPublishProperties(User user, Container study, Collection<PropertyDescriptor> types, Map<String, Object> dataMap, ExpRun run,
                                                   CopyToStudyContext context) throws SQLException
    {
        UserSchema schema = AssayService.get().createSchema(user, run.getContainer());
        TableInfo runTable = schema.getTable(AssayService.get().getRunsTableName(run.getProtocol()), null);

        PropertyDescriptor pd = addProperty(study, "RunName", run.getName(), dataMap, types);
        setStandardPropertyAttributes(runTable, "Name", pd);
        pd = addProperty(study, "RunComments", run.getComments(), dataMap, types);
        setStandardPropertyAttributes(runTable, "Comments", pd);
        pd = addProperty(study, "RunCreatedOn", run.getCreated(), dataMap, types);
        setStandardPropertyAttributes(runTable, "Created", pd);
        User createdBy = run.getCreatedBy();
        pd = addProperty(study, "RunCreatedBy", createdBy == null ? null : createdBy.getDisplayName(HttpView.currentContext()), dataMap, types);
        setStandardPropertyAttributes(runTable, "CreatedBy", pd);

        Map<String, Object> runProperties = context.getProperties(run);
        for (PropertyDescriptor runPD : context.getRunPDs())
        {
            if (!TARGET_STUDY_PROPERTY_NAME.equals(runPD.getName()) &&
                    !PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equals(runPD.getName()))
            {
                PropertyDescriptor publishPd = runPD.clone();
                publishPd.setName("Run" + runPD.getName());
                addProperty(publishPd, runProperties.get(runPD.getPropertyURI()), dataMap, types);
            }
        }

        Map<String, Object> batchProperties = context.getProperties(context.getBatch(run));
        for (PropertyDescriptor batchPD : context.getBatchPDs())
        {
            if (!TARGET_STUDY_PROPERTY_NAME.equals(batchPD.getName()) &&
                    !PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equals(batchPD.getName()))
            {
                PropertyDescriptor publishPd = batchPD.clone();
                publishPd.setName("Batch" + batchPD.getName());
                addProperty(publishPd, batchProperties.get(batchPD.getPropertyURI()), dataMap, types);
            }
        }


        
    }

    protected class CopyToStudyContext
    {
        private Map<ExpRun, ExpExperiment> _batches = new HashMap<ExpRun, ExpExperiment>();
        private Map<ExpRun, Map<String, Object>> _runPropertyCache = new HashMap<ExpRun, Map<String, Object>>();
        private Map<ExpExperiment, Map<String, Object>> _batchPropertyCache = new HashMap<ExpExperiment, Map<String, Object>>();
        private List<PropertyDescriptor> _runPDs;
        private List<PropertyDescriptor> _batchPDs;

        public CopyToStudyContext(ExpProtocol protocol, PropertyDescriptor... extraRunPDs)
        {
            _runPDs = new ArrayList<PropertyDescriptor>(Arrays.asList(getPropertyDescriptors(getRunDomain(protocol))));
            _runPDs.addAll(Arrays.asList(extraRunPDs));
            _batchPDs = new ArrayList<PropertyDescriptor>(Arrays.asList(getPropertyDescriptors(getBatchDomain(protocol))));
        }

        public List<PropertyDescriptor> getRunPDs()
        {
            return _runPDs;
        }

        public List<PropertyDescriptor> getBatchPDs()
        {
            return _batchPDs;
        }

        public ExpExperiment getBatch(ExpRun run)
        {
            ExpExperiment result = null;
            if (!_batches.containsKey(run))
            {
                result = AssayService.get().findBatch(run);
                _batches.put(run, result);
            }
            else
            {
                result = _batches.get(run);
            }
            return result;
        }

        private <Type extends ExpObject> Map<String, Object> getProperties(Type object, Map<Type, Map<String, Object>> cache) throws SQLException
        {
            if (object == null)
            {
                return Collections.emptyMap();
            }
            Map<String, Object> result = cache.get(object);
            if (result == null)
            {
                result = OntologyManager.getProperties(object.getContainer(), object.getLSID());
                cache.put(object, result);
            }
            return result;
        }

        public Map<String, Object> getProperties(ExpExperiment batch) throws SQLException
        {
            return getProperties(batch, _batchPropertyCache);
        }

        public Map<String, Object> getProperties(ExpRun run) throws SQLException
        {
            return getProperties(run, _runPropertyCache);
        }
    }

    protected void registerLsidHandler()
    {
        LsidManager.get().registerHandler(_runLSIDPrefix, new LsidManager.LsidHandler()
        {
            public ExpRun getObject(Lsid lsid)
            {
                return ExperimentService.get().getExpRun(lsid.toString());
            }

            public String getDisplayURL(Lsid lsid)
            {
                ExpRun run = ExperimentService.get().getExpRun(lsid.toString());
                if (run == null)
                    return null;
                ExpProtocol protocol = run.getProtocol();
                if (protocol == null)
                    return null;
                ActionURL dataURL = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(run.getContainer(), protocol, run.getRowId());
                return dataURL.getLocalURIString();
            }

            public Container getContainer(Lsid lsid)
            {
                ExpRun run = getObject(lsid);
                return run == null ? null : run.getContainer();
            }
        });
    }

    public Priority getPriority(ExpProtocol protocol)
    {
        if (ExpProtocol.ApplicationType.ExperimentRun.equals(protocol.getApplicationType()))
        {
            Lsid lsid = new Lsid(protocol.getLSID());
            if (_protocolLSIDPrefix.equals(lsid.getNamespacePrefix()))
            {
                return Priority.HIGH;
            }
        }
        return null;
    }

    protected void addOutputMaterials(AssayRunUploadContext context, Map<ExpMaterial, String> outputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
    }

    protected void addOutputDatas(AssayRunUploadContext context, Map<ExpData, String> outputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        Map<String, File> files;
        try
        {
            files = context.getUploadedData();
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
        if (files.size() == 0)
        {
            throw new IllegalStateException("AssayRunUploadContext " + context + " provided no upload data");
        }

        assert files.size() == 1;

        Map.Entry<String, File> entry = files.entrySet().iterator().next();

        ExpData data = createData(context.getContainer(), entry.getValue(), entry.getKey(), _dataType);
        outputDatas.put(data, "Data");
    }

    protected void addInputDatas(AssayRunUploadContext context, Map<ExpData, String> inputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
    }

    protected void addInputMaterials(AssayRunUploadContext context, Map<ExpMaterial, String> inputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
    }

    public static String getDomainURIForPrefix(ExpProtocol protocol, String domainPrefix)
    {
        String result = null;
        for (String uri : protocol.getObjectProperties().keySet())
        {
            Lsid uriLSID = new Lsid(uri);
            if (uriLSID.getNamespacePrefix() != null && uriLSID.getNamespacePrefix().startsWith(domainPrefix))
            {
                if (result == null)
                {
                    result = uri;
                }
                else
                {
                    throw new IllegalStateException("More than one domain matches for prefix '" + domainPrefix + "' in protocol with LSID '" + protocol.getLSID() + "'");
                }
            }
        }
        if (result == null)
        {
            throw new IllegalArgumentException("No domain match for prefix '" + domainPrefix + "' in protocol with LSID '" + protocol.getLSID() + "'");
        }
        return result;
    }
    
    public static Domain getDomainByPrefix(ExpProtocol protocol, String domainPrefix)
    {
        Container container = protocol.getContainer();
        return PropertyService.get().getDomain(container, getDomainURIForPrefix(protocol, domainPrefix));
    }

    public Domain getRunDataDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_DATA);
    }

    public List<PropertyDescriptor> getRunTableColumns(ExpProtocol protocol)
    {
        List<PropertyDescriptor> cols = new ArrayList<PropertyDescriptor>();
        Domain runDomain = getRunDomain(protocol);
        if (runDomain != null)
            Collections.addAll(cols, getPropertyDescriptors(runDomain));
        return cols;
    }

    public Domain getBatchDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_BATCH);
    }

    public Domain getRunInputDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_RUN);
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, Integer value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(sourceContainer, name, value, PropertyType.INTEGER, dataMap, types);
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, Double value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(sourceContainer, name, value, PropertyType.DOUBLE, dataMap, types);
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, Boolean value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(sourceContainer, name, value, PropertyType.BOOLEAN, dataMap, types);
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, Date value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(sourceContainer, name, value, PropertyType.DATE_TIME, dataMap, types);
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, String value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(sourceContainer, name, value, PropertyType.STRING, dataMap, types);
    }

    protected PropertyDescriptor addProperty(PropertyDescriptor pd, ObjectProperty value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(pd, value == null ? null : value.getValueQcAware(), dataMap, types);
    }

    protected PropertyDescriptor addProperty(PropertyDescriptor pd, Object value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        dataMap.put(pd.getName(), value);
        if (types != null)
            types.add(pd);
        return pd;
    }

    protected PropertyDescriptor addProperty(Container sourceContainer, String name, Object value, PropertyType type, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        return addProperty(createPublishPropertyDescriptor(sourceContainer, name, type), value, dataMap, types);
    }

    protected PropertyDescriptor createPublishPropertyDescriptor(Container sourceContainer, String name, PropertyType type)
    {
        String label = name;
        if (name.contains(" "))
            name = name.replace(" ", "");
        PropertyDescriptor pd = new PropertyDescriptor(null, type.getTypeUri(), name, label, sourceContainer);
        if (type.getJavaType() == Double.class)
            pd.setFormat("0.###");
        return pd;
    }

    protected DomainProperty addProperty(Domain domain, String name, PropertyType type)
    {
        return addProperty(domain, name, name, type);
    }

    protected DomainProperty addProperty(Domain domain, String name, String label, PropertyType type)
    {
        return addProperty(domain, name, label, type, null);
    }
    
    protected DomainProperty addProperty(Domain domain, String name, String label, PropertyType type, String description)
    {
        DomainProperty prop = domain.addProperty();
        prop.setLabel(label);
        prop.setName(name);
        prop.setType(PropertyService.get().getType(domain.getContainer(), type.getXmlName()));
        prop.setDescription(description);

        if (AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(prop.getName()) ||
            AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(prop.getName()) ||
            AbstractAssayProvider.VISITID_PROPERTY_NAME.equals(prop.getName()) ||
            AbstractAssayProvider.DATE_PROPERTY_NAME.equals(prop.getName()))
        {
            prop.setDefaultValueTypeEnum(DefaultValueType.FIXED_EDITABLE);
        }
        else
        {
            prop.setDefaultValueTypeEnum(DefaultValueType.LAST_ENTERED);
        }
        return prop;
    }

    public static boolean isDomainType(Domain domain, ExpProtocol protocol, String domainPrefix)
    {
        String domainURI;
        if (protocol.getRowId() > 0)
        {
            domainURI = getDomainURIForPrefix(protocol, domainPrefix);
        }
        else
        {
            domainURI = getPresubstitutionLsid(domainPrefix);
        }
        return domainURI.equals(domain.getTypeURI());
    }

    public static String getPresubstitutionLsid(String prefix)
    {
        return "urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":" + ASSAY_NAME_SUBSTITUTION;
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createRunDomain(Container c, User user)
    {
        Domain domain = PropertyService.get().createDomain(c, getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_RUN), "Run Fields");
        domain.setDescription("The user is prompted to enter run level properties for each file they import.  This is the second step of the import process.");
        return new Pair<Domain, Map<DomainProperty, Object>>(domain, Collections.<DomainProperty, Object>emptyMap());
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createBatchDomain(Container c, User user)
    {
        Domain domain = PropertyService.get().createDomain(c, getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_BATCH), "Batch Fields");
        List<ParticipantVisitResolverType> resolverTypes = getParticipantVisitResolverTypes();
        if (resolverTypes != null && resolverTypes.size() > 0)
            addProperty(domain, PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME, PARTICIPANT_VISIT_RESOLVER_PROPERTY_CAPTION, PropertyType.STRING).setRequired(true);

        DomainProperty studyProp = addProperty(domain, TARGET_STUDY_PROPERTY_NAME, TARGET_STUDY_PROPERTY_CAPTION, PropertyType.STRING);
        studyProp.setLookup(new Lookup(null, "study", "Study"));

        domain.setDescription("The user is prompted for batch properties once for each set of runs they import. The run " +
                "set is a convenience to let users set properties that seldom change in one place and import many runs " +
                "using them. This is the first step of the import process.");
        return new Pair<Domain, Map<DomainProperty, Object>>(domain, Collections.<DomainProperty, Object>emptyMap());
    }

    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = new ArrayList<Pair<Domain, Map<DomainProperty, Object>>>();
        boolean transaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                transaction = true;
            }

            result.add(createBatchDomain(c, user));
            result.add(createRunDomain(c, user));

            if (transaction)
            {
                ExperimentService.get().commitTransaction();
                transaction = false;
            }
        }
        finally
        {
            if (transaction)
            {
                ExperimentService.get().rollbackTransaction();
            }
        }

        return result;
    }

    public List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles)
    {
        List<AssayDataCollector> result = new ArrayList<AssayDataCollector>();
        if (uploadedFiles != null)
            result.add(new PreviouslyUploadedDataCollector(uploadedFiles));
        result.add(new FileUploadDataCollector());
        return result;
    }

    public Domain getRunDomain(ExpProtocol protocol)
    {
        return getRunInputDomain(protocol);
    }

    protected ExpRun createExperimentRun(AssayRunUploadContext context) throws ExperimentException
    {
        File file = null;
        String originalFileName = null;
        {
            try
            {
                Map<String, File> uploadedData = context.getUploadedData();
                if (uploadedData != null && uploadedData.size() != 0)
                {
                    Map.Entry<String, File> entry = uploadedData.entrySet().iterator().next();
                    file = entry.getValue();
                    originalFileName = entry.getKey();
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        String name = null;
        if (context.getName() != null)
        {
            name = context.getName();
        }
        else if (file != null)
        {
            name = originalFileName;
        }
        ExpRun run = createExperimentRun(name, context.getContainer(), context.getProtocol());

        run.setComments(context.getComments());
        if (file != null)
        {
            run.setFilePathRoot(file.getParentFile());
        }
        return run;
    }

    public ExpRun createExperimentRun(String name, Container container, ExpProtocol protocol)
    {
        if (name == null)
        {
            name = "[Untitled]";
        }

        String entityId = GUID.makeGUID();
        ExpRun run = ExperimentService.get().createExperimentRun(container, name);

        Lsid lsid = new Lsid(_runLSIDPrefix, "Folder-" + container.getRowId(), entityId);
        run.setLSID(lsid.toString());
        run.setProtocol(ExperimentService.get().getExpProtocol(protocol.getRowId()));
        run.setEntityId(entityId);
        return run;
    }

    /**
     * @param batch if not null, the run group that's already created for this batch. If null, a new one needs to be created
     * @return the run and batch that were inserted
     * @throws ExperimentException
     */
    public Pair<ExpRun, ExpExperiment> saveExperimentRun(AssayRunUploadContext context, ExpExperiment batch) throws ExperimentException, ValidationException
    {
        ExpRun run = createExperimentRun(context);

        Map<ExpMaterial, String> inputMaterials = new HashMap<ExpMaterial, String>();
        Map<ExpData, String> inputDatas = new HashMap<ExpData, String>();
        Map<ExpMaterial, String> outputMaterials = new HashMap<ExpMaterial, String>();
        Map<ExpData, String> outputDatas = new HashMap<ExpData, String>();

        Map<DomainProperty, String> runProperties = context.getRunProperties();
        Map<DomainProperty, String> batchProperties = context.getBatchProperties();

        Map<DomainProperty, String> allProperties = new HashMap<DomainProperty, String>();
        allProperties.putAll(runProperties);
        allProperties.putAll(batchProperties);

        ParticipantVisitResolverType resolverType = null;
        for (Map.Entry<DomainProperty, String> entry : allProperties.entrySet())
        {
            if (entry.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                resolverType = AbstractAssayProvider.findType(entry.getValue(), getParticipantVisitResolverTypes());
                resolverType.configureRun(context, run, inputDatas);
                break;
            }
        }

        addInputMaterials(context, inputMaterials, resolverType);
        addInputDatas(context, inputDatas, resolverType);
        addOutputMaterials(context, outputMaterials, resolverType);
        addOutputDatas(context, outputDatas, resolverType);

        try
        {
            ParticipantVisitResolver resolver = null;
            if (resolverType != null)
            {
                String targetStudyId = null;
                for (Map.Entry<DomainProperty, String> property : allProperties.entrySet())
                {
                    if (TARGET_STUDY_PROPERTY_NAME.equals(property.getKey().getName()))
                    {
                        targetStudyId = property.getValue();
                        break;
                    }
                }
                Container targetStudy = null;
                if (targetStudyId != null && targetStudyId.length() > 0)
                    targetStudy = ContainerManager.getForId(targetStudyId);
                
                resolver = resolverType.createResolver(Collections.unmodifiableCollection(inputMaterials.keySet()),
                        Collections.unmodifiableCollection(inputDatas.keySet()),
                        Collections.unmodifiableCollection(outputMaterials.keySet()),
                        Collections.unmodifiableCollection(outputDatas.keySet()),
                        context.getContainer(),
                        targetStudy, context.getUser());
            }
            resolveExtraRunData(resolver, context, inputMaterials, inputDatas, outputMaterials, outputDatas);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }

        DbScope scope = ExperimentService.get().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        try
        {
            if (transactionOwner)
                scope.beginTransaction();

            savePropertyObject(run.getLSID(), runProperties, context.getContainer());

            // Save the batch first
            if (batch == null)
            {
                // Make sure that we have a batch to associate with this run
                batch = AssayService.get().createStandardBatch(run.getContainer(), null, context.getProtocol());
                batch.save(context.getUser());
                savePropertyObject(batch.getLSID(), batchProperties, context.getContainer());
            }
            run.save(context.getUser());
            // Add the run to the batch so that we can find it when we're loading the data files
            batch.addRuns(context.getUser(), run);
            
            run = ExperimentService.get().insertSimpleExperimentRun(run,
                inputMaterials,
                inputDatas,
                outputMaterials,
                outputDatas,
                new ViewBackgroundInfo(context.getContainer(),
                        context.getUser(), context.getActionURL()), LOG, true);

            validate(context, run);

            if (transactionOwner)
                scope.commitTransaction();

            return new Pair<ExpRun, ExpExperiment>(run, batch);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (transactionOwner)
                scope.closeConnection();
        }
    }

    protected PropertyDescriptor[] getPropertyDescriptors(Domain domain)
    {
        DomainProperty[] domainProperties = domain.getProperties();
        PropertyDescriptor[] pds = new PropertyDescriptor[domainProperties.length];
        for (int i = 0; i < domainProperties.length; i++)
            pds[i] = domainProperties[i].getPropertyDescriptor();
        return pds;
    }

    protected void resolveExtraRunData(ParticipantVisitResolver resolver,
                                  AssayRunUploadContext context,
                                  Map<ExpMaterial, String> inputMaterials,
                                  Map<ExpData, String> inputDatas,
                                  Map<ExpMaterial, String> outputMaterials,
                                  Map<ExpData, String> outputDatas) throws ExperimentException
    {

    }

    protected void savePropertyObject(String parentLSID, Map<DomainProperty, String> properties, Container container) throws ExperimentException
    {
        try {
            ObjectProperty[] objProperties = new ObjectProperty[properties.size()];
            int i = 0;
            for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
            {
                DomainProperty pd = entry.getKey();
                ObjectProperty property = new ObjectProperty(parentLSID,
                        container, pd.getPropertyURI(),
                        entry.getValue(), pd.getPropertyDescriptor().getPropertyType());
                property.setName(pd.getName());
                objProperties[i++] = property;
            }
            OntologyManager.insertProperties(container, parentLSID, objProperties);
        }
        catch (ValidationException ve)
        {
            throw new ExperimentException(ve.getMessage(), ve);
        }
    }

    public ExpProtocol createAssayDefinition(User user, Container container, String name, String description)
            throws ExperimentException
    {
        String protocolLsid = new Lsid(_protocolLSIDPrefix, "Folder-" + container.getRowId(), name).toString();

        ExpProtocol protocol = ExperimentService.get().createExpProtocol(container, ExpProtocol.ApplicationType.ExperimentRun, name);
        protocol.setProtocolDescription(description);
        protocol.setLSID(protocolLsid);
        protocol.setMaxInputMaterialPerInstance(1);
        protocol.setMaxInputDataPerInstance(1);

        if (ExperimentService.get().getExpProtocol(protocol.getLSID()) != null)
        {
            throw new ExperimentException("An assay with that name already exists");
        }

        return ExperimentService.get().insertSimpleProtocol(protocol, user);
    }

    public Container getAssociatedStudyContainer(ExpProtocol protocol, Object dataId)
    {
        boolean onRunObject = false;
        DomainProperty targetStudyColumn = null;
        Domain runDomain = getRunDomain(protocol);
        DomainProperty[] runColumns = runDomain.getProperties();
        for (DomainProperty pd : runColumns)
        {
            if (TARGET_STUDY_PROPERTY_NAME.equals(pd.getName()))
            {
                targetStudyColumn = pd;
                onRunObject = true;
                break;
            }
        }

        if (targetStudyColumn == null)
        {
            Domain batchDomain = getBatchDomain(protocol);
            DomainProperty[] domainColumns = batchDomain.getProperties();
            for (DomainProperty pd : domainColumns)
            {
                if (TARGET_STUDY_PROPERTY_NAME.equals(pd.getName()))
                {
                    targetStudyColumn = pd;
                    break;
                }
            }
        }

        if (targetStudyColumn == null)
            return null;
        ExpData data = getDataForDataRow(dataId);
        if (data == null)
            return null;
        ExpRun run = data.getRun();
        if (run == null)
            return null;

        ExpObject source = null;
        if (onRunObject)
        {
            source = run;
        }
        else
        {
            source = AssayService.get().findBatch(run);
        }

        if (source != null)
        {
            Map<String, Object> properties;
            try
            {
                properties = getProperties(source);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            String targetStudyId = (String) properties.get(targetStudyColumn.getPropertyURI());

            if (targetStudyId != null)
                return ContainerManager.getForId(targetStudyId);
        }
        return null;
    }

    protected Map<String, Object> getProperties(ExpObject object) throws SQLException
    {
        return OntologyManager.getProperties(object.getContainer(), object.getLSID());
    }

    public abstract ExpData getDataForDataRow(Object dataRowId);


    public Map<String, Class<? extends Controller>> getImportActions()
    {
        return Collections.<String, Class<? extends Controller>>singletonMap(IMPORT_DATA_LINK_NAME, UploadWizardAction.class);
    }
    
    public ExpRunTable createRunTable(UserSchema schema, String alias, ExpProtocol protocol)
    {
        final ExpRunTable runTable = new ExpSchema(schema.getUser(), schema.getContainer()).createRunsTable(alias);
        ColumnInfo dataLinkColumn = runTable.getColumn(ExpRunTable.Column.Name);
        dataLinkColumn.setCaption("Assay Id");
        dataLinkColumn.setDescription("The assay/experiment ID that uniquely identifies this assay run.");
        dataLinkColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new AssayDataLinkDisplayColumn(colInfo, runTable.getContainerFilter());
            }
        });
        return runTable;
    }

    public static ParticipantVisitResolverType findType(String name, List<ParticipantVisitResolverType> types)
    {
        for (ParticipantVisitResolverType type : types)
        {
            if (name.equals(type.getName()))
            {
                return type;
            }
        }
        throw new IllegalArgumentException("Unexpected resolver type: " + name);
    }

    private Set<String> getPropertyDomains(ExpProtocol protocol)
    {
        Set<String> result = new HashSet<String>();
        for (ObjectProperty prop : protocol.getObjectProperties().values())
        {
            Lsid lsid = new Lsid(prop.getPropertyURI());
            if (lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(ExpProtocol.ASSAY_DOMAIN_PREFIX))
            {
                result.add(prop.getPropertyURI());
            }
        }
        return result;
    }

    public List<Pair<Domain, Map<DomainProperty, Object>>> getDomains(ExpProtocol protocol)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> domains = new ArrayList<Pair<Domain, Map<DomainProperty, Object>>>();
        for (String uri : getPropertyDomains(protocol))
        {
            Domain domain = PropertyService.get().getDomain(protocol.getContainer(), uri);
            Map<DomainProperty, Object> values = DefaultValueService.get().getDefaultValues(domain.getContainer(), domain);
            domains.add(new Pair<Domain, Map<DomainProperty, Object>>(domain, values));
        }
        sortDomainList(domains);
        return domains;
    }

    public Set<String> getReservedPropertyNames(ExpProtocol protocol, Domain domain)
    {
        Set<String> reservedNames = new HashSet<String>();
        boolean runDomain = isDomainType(domain, protocol, ExpProtocol.ASSAY_DOMAIN_RUN);
        boolean batchDomain = isDomainType(domain, protocol, ExpProtocol.ASSAY_DOMAIN_BATCH);

        if (runDomain || batchDomain)
        {
            TableInfo runTable = ExperimentService.get().getTinfoExperimentRun();
            for (ColumnInfo column : runTable.getColumns())
                reservedNames.add(column.getName());
            reservedNames.add("AssayId");
            reservedNames.add("Assay Id");
        }
        reservedNames.add("RowId");
        reservedNames.add("Row Id");
        reservedNames.add("Container");
        reservedNames.add("LSID");
        return reservedNames;
    }

    public Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer)
    {
        ExpProtocol copy = ExperimentService.get().createExpProtocol(targetContainer, ExpProtocol.ApplicationType.ExperimentRun, "Unknown");
        copy.setName(null);
        return new Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>>(copy, createDefaultDomains(targetContainer, user));
    }

    protected void sortDomainList(List<Pair<Domain, Map<DomainProperty, Object>>> domains)
    {
        // Rely on the assay provider to return a list of default domains in the right order (Collections.sort() is
        // stable so that domains that haven't been inserted and have id 0 stay in the same order), and rely on the fact
        // that they get inserted in the same order, so they will have ascending ids.
        Collections.sort(domains, new Comparator<Pair<Domain, Map<DomainProperty, Object>>>(){

            public int compare(Pair<Domain, Map<DomainProperty, Object>> dom1, Pair<Domain, Map<DomainProperty, Object>> dom2)
            {
                return new Integer(dom1.getKey().getTypeId()).compareTo(new Integer(dom2.getKey().getTypeId()));
            }
        });
    }

    public Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer, ExpProtocol toCopy)
    {
        ExpProtocol copy = ExperimentService.get().createExpProtocol(targetContainer, toCopy.getApplicationType(), toCopy.getName());
        copy.setDescription(toCopy.getDescription());

        List<Pair<Domain, Map<DomainProperty, Object>>> originalDomains = getDomains(toCopy);
        List<Pair<Domain, Map<DomainProperty, Object>>> copiedDomains = new ArrayList<Pair<Domain, Map<DomainProperty, Object>>>(originalDomains.size());
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : originalDomains)
        {
            Domain domain = domainInfo.getKey();
            Map<DomainProperty, Object> originalDefaults = domainInfo.getValue();
            Map<DomainProperty, Object> copiedDefaults = new HashMap<DomainProperty, Object>();

            String uri = domain.getTypeURI();
            Lsid domainLsid = new Lsid(uri);
            String name = domain.getName();
            String defaultPrefix = toCopy.getName() + " ";
            if (name.startsWith(defaultPrefix))
                name = name.substring(defaultPrefix.length());
            Domain domainCopy = PropertyService.get().createDomain(targetContainer, getPresubstitutionLsid(domainLsid.getNamespacePrefix()), name);
            for (DomainProperty propSrc : domain.getProperties())
            {
                DomainProperty propCopy = domainCopy.addProperty();
                copiedDefaults.put(propCopy, originalDefaults.get(propSrc));
                propCopy.setDescription(propSrc.getDescription());
                propCopy.setFormat(propSrc.getFormatString());
                propCopy.setLabel(propSrc.getLabel());
                propCopy.setName(propSrc.getName());
                propCopy.setDescription(propSrc.getDescription());
                propCopy.setType(propSrc.getType());
                propCopy.setRequired(propSrc.isRequired());
                propCopy.setHidden(propSrc.isHidden());
                propCopy.setQcEnabled(propSrc.isQcEnabled());
                propCopy.setDefaultValueTypeEnum(propSrc.getDefaultValueTypeEnum());
                // check to see if we're moving a lookup column to another container:
                Lookup lookup = propSrc.getLookup();
                if (lookup != null && !toCopy.getContainer().equals(targetContainer))
                {
                    // we need to update the lookup properties if the lookup container is either the source or the destination container
                    if (lookup.getContainer() == null)
                        lookup.setContainer(propSrc.getContainer());
                    else if (lookup.getContainer().equals(targetContainer))
                        lookup.setContainer(null);
                }
                propCopy.setLookup(lookup);
            }
            copiedDomains.add(new Pair<Domain, Map<DomainProperty, Object>>(domainCopy, copiedDefaults));
        }
        return new Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>>(copy, copiedDomains);
    }

    public boolean isFileLinkPropertyAllowed(ExpProtocol protocol, Domain domain)
    {
        Lsid domainLsid = new Lsid(domain.getTypeURI());
        return domainLsid.getNamespacePrefix().equals(ExpProtocol.ASSAY_DOMAIN_BATCH) ||
                domainLsid.getNamespacePrefix().equals(ExpProtocol.ASSAY_DOMAIN_RUN);
    }

    public Container getTargetStudy(ExpRun run)
    {
        ExpProtocol protocol = run.getProtocol();
        Domain batchDomain = getBatchDomain(protocol);
        DomainProperty[] batchColumns = batchDomain.getProperties();
        Domain runDomain = getRunDomain(protocol);
        DomainProperty[] runColumns = runDomain.getProperties();

        List<DomainProperty> pds = new ArrayList<DomainProperty>();
        pds.addAll(Arrays.asList(runColumns));
        pds.addAll(Arrays.asList(batchColumns));

        Map<String, ObjectProperty> props = run.getObjectProperties();

        for (DomainProperty pd : pds)
        {
            ObjectProperty prop = props.get(pd.getPropertyURI());
            if (prop != null && TARGET_STUDY_PROPERTY_NAME.equals(pd.getName()))
            {
                return ContainerManager.getForId(prop.getStringValue());
            }
        }
        return null;
    }

    private Map<String, Set<String>> _requiredDomainProperties;
    public boolean isMandatoryDomainProperty(Domain domain, String propertyName)
    {
        if (_requiredDomainProperties == null)
            _requiredDomainProperties = getRequiredDomainProperties();

        Lsid domainLsid = new Lsid(domain.getTypeURI());
        String domainPrefix = domainLsid.getNamespacePrefix();

        Set<String> domainSet = _requiredDomainProperties.get(domainPrefix);
        return domainSet != null && domainSet.contains(propertyName);
    }

    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<String, Set<String>> domainMap = new HashMap<String, Set<String>>();
        Set<String> batchProperties = domainMap.get(ExpProtocol.ASSAY_DOMAIN_BATCH);
        if (batchProperties == null)
        {
            batchProperties = new HashSet<String>();
            domainMap.put(ExpProtocol.ASSAY_DOMAIN_BATCH, batchProperties);
        }
        batchProperties.add(PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME);
        return domainMap;
    }

    public boolean allowUpload(User user, Container container, ExpProtocol protocol)
    {
        return isPipelineSetup(container);
    }

    public boolean allowDefaultValues(Domain domain)
    {
        Lsid domainLsid = new Lsid(domain.getTypeURI());
        return ExpProtocol.ASSAY_DOMAIN_DATA.equals(domainLsid.getNamespacePrefix());
    }

    protected boolean isPipelineSetup(Container container)
    {
        PipelineService service = PipelineService.get();
        URI uriRoot = null;
        if (container != null)
        {
            PipeRoot pr = service.findPipelineRoot(container);
            if (pr != null)
            {
                uriRoot = pr.getUri(container);
            }
        }
        return (uriRoot != null);
    }

    public HttpView getDisallowedUploadMessageView(User user, Container container, ExpProtocol protocol)
    {
        if (!isPipelineSetup(container))
        {
            StringBuilder html = new StringBuilder();
            html.append("<b>Pipeline root has not been set.</b> ");
            if (container.hasPermission(user, ACL.PERM_ADMIN))
            {
                ActionURL url = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(container);
                html.append("[<a href=\"").append(url.getLocalURIString()).append("\">setup pipeline</a>]");
            }
            else
                html.append(" Please ask an administrator for assistance.");
            return new HtmlView(html.toString());
        }

        return null;
    }

    public static ExpData createData(Container c, File file, String name, DataType dataType) throws ExperimentException
    {
        try
        {
            ExpData data = null;
            if (file != null)
            {
                data = ExperimentService.get().getExpDataByURL(file, c);
            }
            if (data == null)
            {
                data = ExperimentService.get().createData(c, dataType, name);
                data.setLSID(ExperimentService.get().generateGuidLSID(c, dataType));
                if (file != null)
                {
                    data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
                }
            }
            return data;
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    public boolean canCopyToStudy()
    {
        return true;
    }

    public RunListQueryView createRunQueryView(ViewContext context, ExpProtocol protocol)
    {
        RunListQueryView queryView = new RunListQueryView(protocol, context);

        if (hasCustomView(ExpProtocol.AssayDomainTypes.Run, true))
        {
            ActionURL runDetailsURL = new ActionURL(AssayRunDetailsAction.class, context.getContainer());
            runDetailsURL.addParameter("rowId", protocol.getRowId());
            Map<String, String> params = new HashMap<String, String>();
            params.put("runId", "RowId");

            AbstractTableInfo ati = (AbstractTableInfo)queryView.getTable();
            ati.setDetailsURL(new DetailsURL(runDetailsURL, params));
            queryView.setShowDetailsColumn(true);
        }

        return queryView;
    }

    public ResultsQueryView createResultsQueryView(ViewContext context, ExpProtocol protocol)
    {
        String name = AssayService.get().getResultsTableName(protocol);
        QuerySettings settings = new QuerySettings(context, name);
        settings.setSchemaName(AssayService.ASSAY_SCHEMA_NAME);
        settings.setQueryName(name);
        ResultsQueryView queryView = new ResultsQueryView(protocol, context, settings);

        if (hasCustomView(ExpProtocol.AssayDomainTypes.Result, true))
        {
            ActionURL resultDetailsURL = new ActionURL(AssayResultDetailsAction.class, context.getContainer());
            resultDetailsURL.addParameter("rowId", protocol.getRowId());
            Map<String, String> params = new HashMap<String, String>();
            // map ObjectId to url parameter ResultDetailsForm.dataRowId
            params.put("dataRowId", "ObjectId");

            AbstractTableInfo ati = (AbstractTableInfo)queryView.getTable();
            ati.addDetailsURL(new DetailsURL(resultDetailsURL, params));
            queryView.setShowDetailsColumn(true);
        }

        return queryView;
    }

    public boolean hasCustomView(IAssayDomainType domainType, boolean details)
    {
        return false;
    }
    
    public ModelAndView createBatchesView(ViewContext context, ExpProtocol protocol)
    {
        return null;
    }

    public ModelAndView createBatchDetailsView(ViewContext context, ExpProtocol protocol, ExpExperiment batch)
    {
        return null;
    }

    public ModelAndView createRunsView(ViewContext context, ExpProtocol protocol)
    {
        return null;
    }

    public ModelAndView createRunDetailsView(ViewContext context, ExpProtocol protocol, ExpRun run)
    {
        return null;
    }

    public ModelAndView createResultsView(ViewContext context, ExpProtocol protocol)
    {
        return null;
    }

    public ModelAndView createResultDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object dataRowId)
    {
        QueryView queryView = createResultsQueryView(context, protocol);

        DataRegion region = new DataRegion();

        // remove the DetailsColumn from the column list
        List<DisplayColumn> columns = queryView.getDisplayColumns();
        ListIterator<DisplayColumn> iter = columns.listIterator();
        while (iter.hasNext())
        {
            DisplayColumn column = iter.next();
            if (column instanceof DetailsColumn)
                iter.remove();
        }
        region.setDisplayColumns(columns);

        ExpRun run = data.getRun();
        ActionURL runUrl = PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(
            context.getContainer(), protocol,
            queryView.getTable().getContainerFilter(), run.getRowId());

        ButtonBar bb = new ButtonBar();
        bb.getList().add(new ActionButton("Show Run", runUrl));
        region.setButtonBar(bb, DataRegion.MODE_DETAILS);

        return new DetailsView(region, dataRowId);
    }

    public void deleteProtocol(ExpProtocol protocol, User user) throws ExperimentException
    {
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : getDomains(protocol))
        {
            Domain domain = domainInfo.getKey();
            for (DomainProperty prop : domain.getProperties())
            {
                prop.delete();
            }
            try
            {
                domain.delete(user);
            }
            catch (DomainNotFoundException e)
            {
                throw new ExperimentException(e);
            }
        }
    }

    public Class<? extends Controller> getDesignerAction()
    {
        return DesignerAction.class;
    }

    /**
     * Adds columns to an assay data table, providing a link to any datasets that have
     * had data copied into them.
     * @return The names of the added columns that should be visible
     */
    protected Set<String> addCopiedToStudyColumns(AbstractTableInfo table, ExpProtocol protocol, User user, String keyColumnName, boolean setVisibleColumns)
    {
        Set<String> visibleColumnNames = new HashSet<String>();
        int datasetIndex = 0;
        Set<String> usedColumnNames = new HashSet<String>();
        for (final Container studyContainer : StudyService.get().getStudyContainersForAssayProtocol(protocol.getRowId()))
        {
            if (!studyContainer.hasPermission(user, ACL.PERM_READ))
                continue;

            // We need the dataset ID as a separate column in order to display the URL
            String datasetIdSQL = "(SELECT max(sd.datasetid) FROM study.StudyData sd, study.dataset d " +
                "WHERE sd.container = '" + studyContainer.getId() + "' AND " +
                "sd.container = d.container AND sd.datasetid = d.datasetid AND " +
                "d.protocolid = " + protocol.getRowId() + " AND " +    
                "sd._key = CAST(" + ExprColumn.STR_TABLE_ALIAS + "." + keyColumnName + " AS " +
                table.getSqlDialect().sqlTypeNameFromSqlType(Types.VARCHAR) +
                "(200)))";

            final ExprColumn datasetColumn = new ExprColumn(table,
                "dataset" + datasetIndex++,
                new SQLFragment(datasetIdSQL),
                Types.INTEGER);
            datasetColumn.setIsHidden(true);
            table.addColumn(datasetColumn);

            String studyCopiedSql = "(SELECT CASE WHEN " + datasetIdSQL +
                " IS NOT NULL THEN 'copied' ELSE NULL END)";

            String studyName = StudyService.get().getStudyName(studyContainer);
            if (studyName == null)
                continue; // No study in that folder
            String studyColumnName = "copied_to_" + studyName;

            // column names must be unique. Prevent collisions
            while (usedColumnNames.contains(studyColumnName))
                studyColumnName = studyColumnName + datasetIndex;
            usedColumnNames.add(studyColumnName);
            String finalStudyColumnName = studyColumnName;

            final ExprColumn studyCopiedColumn = new ExprColumn(table,
                studyColumnName,
                new SQLFragment(studyCopiedSql),
                Types.VARCHAR);
            final String copiedToStudyColumnCaption = "Copied to " + studyName;
            studyCopiedColumn.setCaption(copiedToStudyColumnCaption);

            studyCopiedColumn.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new StudyDisplayColumn(copiedToStudyColumnCaption, studyContainer, datasetColumn, studyCopiedColumn);
                }
            });

            table.addColumn(studyCopiedColumn);

            visibleColumnNames.add(studyCopiedColumn.getName());
        }
        if (setVisibleColumns)
        {
            List<FieldKey> visibleColumns = new ArrayList<FieldKey>();
            for (FieldKey key : table.getDefaultVisibleColumns())
            {
                visibleColumns.add(key);
            }
            for (String columnName : visibleColumnNames)
            {
                visibleColumns.add(new FieldKey(null, columnName));
            }
            table.setDefaultVisibleColumns(visibleColumns);
        }

        return visibleColumnNames;
    }

    /** Adds the materials as inputs to the run as a whole, plus as inputs for the "work" node for the run. */
    public static void addInputMaterials(ExpRun expRun, User user, Set<ExpMaterial> materialInputs)
    {
        for (ExpProtocolApplication protApp : expRun.getProtocolApplications())
        {
            if (!protApp.getApplicationType().equals(ExpProtocol.ApplicationType.ExperimentRunOutput))
            {
                Set<ExpMaterial> newInputs = new LinkedHashSet<ExpMaterial>();
                newInputs.addAll(materialInputs);
                newInputs.removeAll(protApp.getInputMaterials());
                int index = 1;
                for (ExpMaterial newInput : newInputs)
                {
                    protApp.addMaterialInput(user, newInput, "Sample" + (index == 1 ? "" : Integer.toString(index)));
                    index++;
                }
            }
        }
    }

    public boolean hasUsefulDetailsPage()
    {
        return true;
    }

    public void setValidationAndAnalysisScripts(ExpProtocol protocol, List<File> scripts) throws ExperimentException
    {
        if (scripts.size() > 1)
            throw new ExperimentException("Only one script is supported for this release");

        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(protocol.getObjectProperties());
        String propertyURI = protocol.getLSID() + "#ValidationScript";

        if (scripts.isEmpty())
            props.remove(propertyURI);
        else
        {
            File scriptFile = scripts.get(0);
            if (scriptFile.exists())
            {
                String ext = FileUtil.getExtension(scriptFile);
                ScriptEngine engine = ServiceRegistry.get().getService(ScriptEngineManager.class).getEngineByExtension(ext);
                if (engine != null)
                {
                    ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(),
                            propertyURI, scriptFile.getAbsolutePath());
                    props.put(propertyURI, prop);
                }
                else
                    throw new ExperimentException("Script engine for the extension : " + ext + " has not been registered");
            }
            else
                throw new ExperimentException("The validation script is invalid or does not exist");
        }
        protocol.setObjectProperties(props);
    }

    public List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope)
    {
        if (scope == Scope.ASSAY_DEF || scope == Scope.ALL)
        {
            ObjectProperty prop = protocol.getObjectProperties().get(protocol.getLSID() + "#ValidationScript");
            if (prop != null)
            {
                return Collections.singletonList(new File(prop.getStringValue()));
            }
        }
        return Collections.emptyList();
    }

    public void validate(AssayRunUploadContext context, ExpRun run) throws ValidationException
    {
        for (File scriptFile : getValidationAndAnalysisScripts(context.getProtocol(), Scope.ALL))
        {
            // read the contents of the script file
            if (scriptFile.exists())
            {
                BufferedReader br = null;
                StringBuffer sb = new StringBuffer();
                try {
                    br = new BufferedReader(new FileReader(scriptFile));
                    String l;
                    while ((l = br.readLine()) != null)
                        sb.append(l).append('\n');
                }
                catch (Exception e)
                {
                    throw new ValidationException(e.getMessage());
                }
                finally
                {
                    if (br != null)
                        try {br.close();} catch(IOException ioe) {}
                }

                ScriptEngine engine = ServiceRegistry.get().getService(ScriptEngineManager.class).getEngineByExtension(FileUtil.getExtension(scriptFile));
                if (engine != null)
                {
                    File scriptDir = getScriptDir(context.getProtocol());
                    try
                    {
                        DataExchangeHandler dataHandler = getDataExchangeHandler();
                        if (dataHandler != null)
                        {
                            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                            String script = sb.toString();
                            File runInfo = dataHandler.createValidationRunInfo(context, run, scriptDir);

                            bindings.put(ExternalScriptEngine.WORKING_DIRECTORY, scriptDir.getAbsolutePath());
                            bindings.put(ExternalScriptEngine.SCRIPT_PATH, scriptFile.getAbsolutePath());

                            Map<String, String> paramMap = new HashMap<String, String>();

                            paramMap.put("runInfo", runInfo.getAbsolutePath().replaceAll("\\\\", "/"));
                            bindings.put(ExternalScriptEngine.PARAM_REPLACEMENT_MAP, paramMap);

                            Object output = engine.eval(script);

                            // process any output from the validation script
                            dataHandler.processValidationOutput(runInfo);
                        }
                    }
                    catch (ValidationException e)
                    {
                        throw e;
                    }
                    catch (Exception e)
                    {
                        throw new ValidationException(e.getMessage());
                    }
                    finally
                    {
                        // clean up temp directory
                        FileUtil.deleteDir(scriptDir);
                    }
                }
                else
                {
                    // we may just want to log an error rather than fail the upload due to an engine config problem.
                    throw new ValidationException("A script engine implementation was not found for the specified QC script. " +
                            "Check configurations in the Admin Console.");
                }
            }
            else
            {
                throw new ValidationException("The validation script: " + scriptFile.getAbsolutePath() + " configured for this Assay does not exist. Please check " +
                        "the configuration for this Assay design.");
            }
        }
    }

    public DataType getDataType()
    {
        return _dataType;
    }

    /**
     * Return the helper to handle data exchange between the server and external scripts.
     */
    public DataExchangeHandler getDataExchangeHandler()
    {
        return null;
    }

    protected File getScriptDir(ExpProtocol protocol)
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File tempRoot = new File(tempDir, ExternalScriptEngine.DEFAULT_WORKING_DIRECTORY);
        if (!tempRoot.exists())
            tempRoot.mkdirs();

        File tempFolder = new File(tempRoot.getAbsolutePath() + File.separator + "AssayId_" + protocol.getRowId(), String.valueOf(Thread.currentThread().getId()));
        if (!tempFolder.exists())
            tempFolder.mkdirs();

        return tempFolder;
    }
}
