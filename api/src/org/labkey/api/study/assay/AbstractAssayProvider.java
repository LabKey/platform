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

import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
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
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.query.RunDataQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.common.util.Pair;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
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

    protected final String _protocolLSIDPrefix;
    protected final String _runLSIDPrefix;
    protected final String _dataLSIDPrefix;

    public AbstractAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, DataType dataType)
    {
        _dataLSIDPrefix = dataType.getNamespacePrefix();
        _protocolLSIDPrefix = protocolLSIDPrefix;
        _runLSIDPrefix = runLSIDPrefix;
        registerLsidHandler();
    }

    protected void addStandardRunPublishProperties(Container study, Collection<PropertyDescriptor> types, Map<String, Object> dataMap, ExpRun run)
    {
        addProperty(study, "Run Assay Id", run.getProtocol().getRowId(), dataMap, types);
        addProperty(study, "Run Name", run.getName(), dataMap, types);
        addProperty(study, "Run Comments", run.getComments(), dataMap, types);
        addProperty(study, "Run CreatedOn", run.getCreated(), dataMap, types);
        User createdBy = run.getCreatedBy();
        addProperty(study, "Run CreatedBy", createdBy == null ? null : createdBy.getDisplayName(HttpView.currentContext()), dataMap, types);
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
                ActionURL dataURL = AssayService.get().getAssayDataURL(run.getContainer(), protocol, run.getRowId());
                return dataURL.getLocalURIString();
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
            throw new IllegalStateException("AssayRunUploadContext " + context + "provided no upload data");
        }

        assert files.size() == 1;

        File file = files.values().iterator().next();

        ExpData data = createData(context.getContainer(), file, new DataType(_dataLSIDPrefix));
        outputDatas.put(data, "Data");
    }

    protected void addInputDatas(AssayRunUploadContext context, Map<ExpData, String> inputDatas, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
    }

    protected void addInputMaterials(AssayRunUploadContext context, Map<ExpMaterial, String> inputMaterials, ParticipantVisitResolverType resolverType) throws ExperimentException
    {
        Collection<String> sampleIds = context.getSampleIds();
        int sampleNumber = 1;
        for (String sampleId : sampleIds)
        {
            String roleName = "Sample" + (sampleIds.size() == 1 ? "" : sampleNumber);
            inputMaterials.put(createSampleMaterial(context.getContainer(), context.getProtocol(), sampleId), roleName);
            sampleNumber++;
        }
    }

    protected ExpMaterial createSampleMaterial(Container currentContainer, ExpProtocol protocol, String sampleId)
    {
        String materialLSID = new Lsid("AssayRunMaterial", "Folder-" + currentContainer.getRowId(), sampleId).toString();
        ExpMaterial material = ExperimentService.get().getExpMaterial(materialLSID);
        if (material == null)
        {
            material = ExperimentService.get().createExpMaterial(currentContainer, materialLSID, sampleId);
        }
        return material;
    }

    public static String getDomainURIForPrefix(ExpProtocol protocol, String domainPrefix)
    {
        String result = null;
        for (String uri : protocol.retrieveObjectProperties().keySet())
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
    
    public static PropertyDescriptor[] getPropertiesForDomainPrefix(ExpProtocol protocol, String domainPrefix)
    {
        Container container = protocol.getContainer();
        return OntologyManager.getPropertiesForType(getDomainURIForPrefix(protocol, domainPrefix), container);
    }

    public PropertyDescriptor[] getRunDataColumns(ExpProtocol protocol)
    {
        return getPropertiesForDomainPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_DATA);
    }

    public PropertyDescriptor[] getUploadSetColumns(ExpProtocol protocol)
    {
        return getPropertiesForDomainPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_UPLOAD_SET);
    }

    public PropertyDescriptor[] getRunInputPropertyColumns(ExpProtocol protocol)
    {
        return getPropertiesForDomainPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_RUN);
    }

    protected void addProperty(Container sourceContainer, String name, Integer value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        addProperty(sourceContainer, name, value, PropertyType.INTEGER, dataMap, types);
    }

    protected void addProperty(Container sourceContainer, String name, Double value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        addProperty(sourceContainer, name, value, PropertyType.DOUBLE, dataMap, types);
    }

    protected void addProperty(Container sourceContainer, String name, Boolean value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        addProperty(sourceContainer, name, value, PropertyType.BOOLEAN, dataMap, types);
    }

    protected void addProperty(Container sourceContainer, String name, Date value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        addProperty(sourceContainer, name, value, PropertyType.DATE_TIME, dataMap, types);
    }

    protected void addProperty(Container sourceContainer, String name, String value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        addProperty(sourceContainer, name, value, PropertyType.STRING, dataMap, types);
    }

    protected void addProperty(PropertyDescriptor pd, ObjectProperty value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        addProperty(pd, value == null ? null : value.value(), dataMap, types);
    }

    protected void addProperty(PropertyDescriptor pd, Object value, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        dataMap.put(pd.getName(), value);
        if (types != null)
            types.add(pd);
    }

    protected void addProperty(Container sourceContainer, String name, Object value, PropertyType type, Map<String, Object> dataMap, Collection<PropertyDescriptor> types)
    {
        addProperty(createPublishPropertyDescriptor(sourceContainer, name, type), value, dataMap, types);
    }

    protected PropertyDescriptor createPublishPropertyDescriptor(Container sourceContainer, String name, PropertyType type)
    {
        PropertyDescriptor pd = new PropertyDescriptor(null, type.getTypeUri(), name, sourceContainer);
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

    protected Domain createRunDomain(Container c, User user)
    {
        Domain domain = PropertyService.get().createDomain(c, getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_RUN), "Run Fields");
        domain.setDescription("The user is prompted to enter run level properties for each file they upload.  This is the second step of the upload process.");
        return domain;
    }

    protected Domain createUploadSetDomain(Container c, User user)
    {
        Domain domain = PropertyService.get().createDomain(c, getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_UPLOAD_SET), "Upload Set Fields");
        List<ParticipantVisitResolverType> resolverTypes = getParticipantVisitResolverTypes();
        if (resolverTypes != null && resolverTypes.size() > 0)
            addProperty(domain, PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME, PARTICIPANT_VISIT_RESOLVER_PROPERTY_CAPTION, PropertyType.STRING).setRequired(true);

        DomainProperty studyProp = addProperty(domain, TARGET_STUDY_PROPERTY_NAME, TARGET_STUDY_PROPERTY_CAPTION, PropertyType.STRING);
        studyProp.setLookup(new Lookup(null, "study", "Study"));

        domain.setDescription("The user is prompted for upload set properties once for each set of runs they upload. The upload set is a convenience to let users set properties that seldom change in one place and upload many runs using them. This is the first step of the upload process.");
        return domain;
    }

    public List<Domain> createDefaultDomains(Container c, User user)
    {
        List<Domain> result = new ArrayList<Domain>();
        boolean transaction = false;
        try
        {
            if (!ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().beginTransaction();
                transaction = true;
            }

            Domain batchDomain = createUploadSetDomain(c, user);
            result.add(batchDomain);

            Domain runDomain = createRunDomain(c, user);
            result.add(runDomain);

            if (transaction)
            {
                ExperimentService.get().commitTransaction();
                transaction = false;
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
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

    public PropertyDescriptor[] getRunPropertyColumns(ExpProtocol protocol)
    {
        return getRunInputPropertyColumns(protocol);
    }

    protected ExpRun createExperimentRun(AssayRunUploadContext context) throws ExperimentException
    {
        Container container = context.getContainer();
        String entityId = GUID.makeGUID();
        File file = null;
        {
            try
            {
                Map<String, File> uploadedData = context.getUploadedData();
                if (uploadedData != null && uploadedData.size() != 0)
                {
                    file = uploadedData.values().iterator().next();
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        String name;
        if (context.getName() != null)
        {
            name = context.getName();
        }
        else if (file != null)
        {
            name = file.getName();
        }
        else
        {
            name = "[Untitled]";
        }

        ExpRun run = ExperimentService.get().createExperimentRun(container, name);

        Lsid lsid = new Lsid(_runLSIDPrefix, "Folder-" + container.getRowId(), entityId);
        run.setLSID(lsid.toString());
        run.setProtocol(ExperimentService.get().getExpProtocol(context.getProtocol().getRowId()));
        run.setComments(context.getComments());
        run.setEntityId(entityId);
        if (file != null)
        {
            run.setFilePathRoot(file.getParentFile());
        }
        return run;
    }

    public ExpRun saveExperimentRun(AssayRunUploadContext context) throws ExperimentException
    {
        ExpRun run = createExperimentRun(context);

        Map<ExpMaterial, String> inputMaterials = new HashMap<ExpMaterial, String>();
        Map<ExpData, String> inputDatas = new HashMap<ExpData, String>();
        Map<ExpMaterial, String> outputMaterials = new HashMap<ExpMaterial, String>();
        Map<ExpData, String> outputDatas = new HashMap<ExpData, String>();

        Map<PropertyDescriptor, String> runProperties = context.getRunProperties();
        Map<PropertyDescriptor, String> uploadSetProperties = context.getUploadSetProperties();

        Map<PropertyDescriptor, String> allProperties = new HashMap<PropertyDescriptor, String>();
        allProperties.putAll(runProperties);
        allProperties.putAll(uploadSetProperties);

        ParticipantVisitResolverType resolverType = null;
        for (Map.Entry<PropertyDescriptor, String> entry : allProperties.entrySet())
        {
            if (entry.getKey().getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME))
            {
                resolverType = AbstractAssayProvider.findType(entry.getValue(), context.getProvider().getParticipantVisitResolverTypes());
                resolverType.configureRun(context, run, runProperties, uploadSetProperties, inputDatas);
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
                for (Map.Entry<PropertyDescriptor, String> property : allProperties.entrySet())
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
            savePropertyObject(run.getLSID(), uploadSetProperties, context.getContainer());

            run = ExperimentService.get().insertSimpleExperimentRun(run,
                inputMaterials,
                inputDatas,
                outputMaterials,
                outputDatas,
                new ViewBackgroundInfo(context.getContainer(),
                        context.getUser(), context.getActionURL()), LOG);

            if (transactionOwner)
                scope.commitTransaction();

            return run;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if (transactionOwner)
                scope.closeConnection();
        }
    }

    protected void resolveExtraRunData(ParticipantVisitResolver resolver,
                                  AssayRunUploadContext context,
                                  Map<ExpMaterial, String> inputMaterials,
                                  Map<ExpData, String> inputDatas,
                                  Map<ExpMaterial, String> outputMaterials,
                                  Map<ExpData, String> outputDatas) throws ExperimentException
    {

    }

    protected void savePropertyObject(String parentLSID, Map<PropertyDescriptor, String> properties, Container container) throws ExperimentException
    {
        try {
            ObjectProperty[] objProperties = new ObjectProperty[properties.size()];
            int i = 0;
            for (Map.Entry<PropertyDescriptor, String> entry : properties.entrySet())
            {
                PropertyDescriptor pd = entry.getKey();
                ObjectProperty property = new ObjectProperty(parentLSID,
                        container.getId(), pd.getPropertyURI(),
                        entry.getValue(), pd.getPropertyType());
                property.setName(pd.getName());
                objProperties[i++] = property;
            }
            OntologyManager.insertProperties(container.getId(), objProperties, parentLSID);
        }
        catch (ValidationException ve)
        {
            throw new ExperimentException(ve.getMessage(), ve);
        }
    }

    public ExpProtocol createAssayDefinition(User user, Container container, String name, String description, int maxMaterials)
            throws ExperimentException, SQLException
    {
        String protocolLsid = new Lsid(_protocolLSIDPrefix, "Folder-" + container.getRowId(), name).toString();

        ExpProtocol protocol = ExperimentService.get().createExpProtocol(container, ExpProtocol.ApplicationType.ExperimentRun, name);
        protocol.setProtocolDescription(description);
        protocol.setLSID(protocolLsid);
        protocol.setMaxInputMaterialPerInstance(maxMaterials);
        protocol.setMaxInputDataPerInstance(1);

        if (ExperimentService.get().getExpProtocol(protocol.getLSID()) != null)
        {
            throw new ExperimentException("An assay with that name already exists");
        }

        return ExperimentService.get().insertSimpleProtocol(protocol, user);
    }

    protected PropertyDescriptor getRunTargetStudyColumn(ExpProtocol protocol)
    {
        PropertyDescriptor[] uploadSetColumns = getUploadSetColumns(protocol);
        for (PropertyDescriptor pd : uploadSetColumns)
        {
            if (TARGET_STUDY_PROPERTY_NAME.equals(pd.getName()))
                return pd;
        }
        PropertyDescriptor[] runColumns = getRunPropertyColumns(protocol);
        for (PropertyDescriptor pd : runColumns)
        {
            if (TARGET_STUDY_PROPERTY_NAME.equals(pd.getName()))
                return pd;
        }
        return null;
    }

    public Container getAssociatedStudyContainer(ExpProtocol protocol, Object dataId)
    {
        PropertyDescriptor targetStudyColumn = getRunTargetStudyColumn(protocol);
        if (targetStudyColumn == null)
            return null;
        ExpData data = getDataForDataRow(dataId);
        if (data == null)
            return null;
        ExpRun run = data.getRun();
        if (run == null)
            return null;
        Map<String, Object> runProperties;
        try
        {
            runProperties = getRunProperties(run);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        String targetStudyId = (String) runProperties.get(targetStudyColumn.getPropertyURI());
        if (targetStudyId != null)
            return ContainerManager.getForId(targetStudyId);
        return null;
    }

    protected Map<String, Object> getRunProperties(ExpRun run) throws SQLException
    {
        return OntologyManager.getProperties(run.getContainer().getId(), run.getLSID());
    }

    public abstract ExpData getDataForDataRow(Object dataRowId);


    public ActionURL getUploadWizardURL(Container container, ExpProtocol protocol)
    {
        return AssayService.get().getProtocolURL(container, protocol, "uploadWizard");
    }

    public ExpRunTable createRunTable(QuerySchema schema, String alias, ExpProtocol protocol)
    {
        ExpRunTable runTable = new ExpSchema(schema.getUser(), schema.getContainer()).createRunsTable(alias);
        ColumnInfo dataLinkColumn = runTable.getColumn(ExpRunTable.Column.Name);
        dataLinkColumn.setCaption("Assay Id");
        dataLinkColumn.setDescription("The assay/experiment ID that uniquely identifies this assay run.");
        dataLinkColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new AssayDataLinkDisplayColumn(colInfo);
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
    public void setPlateTemplate(Container container, ExpProtocol protocol, PlateTemplate template)
    {
        throw new UnsupportedOperationException("Only plate-based assays may have a plate template.");
    }

    public PlateTemplate getPlateTemplate(Container container, ExpProtocol protocol)
    {
        throw new UnsupportedOperationException("Only plate-based assays may have a plate template.");
    }

    public boolean isPlateBased()
    {
        return false;
    }

    private Set<String> getPropertyDomains(ExpProtocol protocol)
    {
        Set<String> result = new HashSet<String>();
        for (ObjectProperty prop : protocol.retrieveObjectProperties().values())
        {
            Lsid lsid = new Lsid(prop.getPropertyURI());
            if (lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(ExpProtocol.ASSAY_DOMAIN_PREFIX))
            {
                result.add(prop.getPropertyURI());
            }
        }
        return result;
    }

    public List<Domain> getDomains(ExpProtocol protocol)
    {
        List<Domain> domains = new ArrayList<Domain>();
        for (String uri : getPropertyDomains(protocol))
        {
            Domain domain = PropertyService.get().getDomain(protocol.getContainer(), uri);
            domains.add(domain);
        }
        return domains;
    }

    public Set<String> getReservedPropertyNames(ExpProtocol protocol, Domain domain)
    {
        Set<String> reservedNames = new HashSet<String>();
        boolean runDomain = isDomainType(domain, protocol, ExpProtocol.ASSAY_DOMAIN_RUN);
        boolean uploadSetDomain = isDomainType(domain, protocol, ExpProtocol.ASSAY_DOMAIN_UPLOAD_SET);

        if (runDomain || uploadSetDomain)
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

    public Pair<ExpProtocol, List<Domain>> getAssayTemplate(User user, Container targetContainer)
    {
        ExpProtocol copy = ExperimentService.get().createExpProtocol(targetContainer, ExpProtocol.ApplicationType.ExperimentRun, "Unknown");
        copy.setName(null);
        return new Pair<ExpProtocol, List<Domain>>(copy, createDefaultDomains(targetContainer, user));
    }

    public Pair<ExpProtocol, List<Domain>> getAssayTemplate(User user, Container targetContainer, ExpProtocol toCopy)
    {
        ExpProtocol copy = ExperimentService.get().createExpProtocol(targetContainer, toCopy.getApplicationType(), toCopy.getName());
        copy.setDescription(toCopy.getDescription());

        List<Domain> originalDomains = getDomains(toCopy);
        List<Domain> copiedDomains = new ArrayList<Domain>(originalDomains.size());
        for (Domain domain : originalDomains)
        {
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
                propCopy.setDescription(propSrc.getDescription());
                propCopy.setFormat(propSrc.getFormatString());
                propCopy.setLabel(propSrc.getLabel());
                propCopy.setName(propSrc.getName());
                propCopy.setDescription(propSrc.getDescription());
                propCopy.setType(propSrc.getType());
                propCopy.setRequired(propSrc.isRequired());
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
            copiedDomains.add(domainCopy);
        }
        return new Pair<ExpProtocol, List<Domain>>(copy, copiedDomains);
    }

    public boolean isFileLinkPropertyAllowed(ExpProtocol protocol, Domain domain)
    {
        Lsid domainLsid = new Lsid(domain.getTypeURI());
        return domainLsid.getNamespacePrefix().equals(ExpProtocol.ASSAY_DOMAIN_UPLOAD_SET) ||
                domainLsid.getNamespacePrefix().equals(ExpProtocol.ASSAY_DOMAIN_RUN);
    }

    public Container getTargetStudy(ExpRun run)
    {
        ExpProtocol protocol = run.getProtocol();
        PropertyDescriptor[] runPDs = getRunPropertyColumns(protocol);
        PropertyDescriptor[] uploadSetPDs = getUploadSetColumns(protocol);

        List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
        pds.addAll(Arrays.asList(runPDs));
        pds.addAll(Arrays.asList(uploadSetPDs));

        Map<String, ObjectProperty> props = run.getObjectProperties();

        for (PropertyDescriptor pd : pds)
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
    public boolean isRequiredDomainProperty(Domain domain, String propertyName)
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
        Set<String> uploadSetProperties = domainMap.get(ExpProtocol.ASSAY_DOMAIN_UPLOAD_SET);
        if (uploadSetProperties == null)
        {
            uploadSetProperties = new HashSet<String>();
            domainMap.put(ExpProtocol.ASSAY_DOMAIN_UPLOAD_SET, uploadSetProperties);
        }
        uploadSetProperties.add(PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME);
        return domainMap;
    }

    public boolean allowUpload(User user, Container container, ExpProtocol protocol)
    {
        return isPipelineSetup(container) && (!isPlateBased() || getPlateTemplate(container, protocol) != null);
    }

    protected boolean isPipelineSetup(Container container)
    {
        PipelineService service = PipelineService.get();
        URI uriRoot = null;
        if (container != null)
        {
            try
            {
                PipeRoot pr = service.findPipelineRoot(container);
                if (pr != null)
                {
                    uriRoot = pr.getUri(container);
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        return (uriRoot != null);
    }

    public HttpView getDisallowedUploadMessageView(User user, Container container, ExpProtocol protocol)
    {
        StringBuilder html = new StringBuilder();
        if (!isPipelineSetup(container))
        {
            html.append("<b>Pipeline root has not been set.</b> ");
            if (container.hasPermission(user, ACL.PERM_ADMIN))
            {
                ActionURL url = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(container);
                html.append("[<a href=\"").append(url.getLocalURIString()).append("\">setup pipeline</a>]");
            }
            else
                html.append(" Please ask an administrator for assistance.");
        }

        if (isPlateBased() && getPlateTemplate(container, protocol) == null)
        {
            if (html.length() > 0)
                html.append("<br>");

            html.append("<b>This assay design does not reference a valid plate template.</b> ");
            if (container.hasPermission(user, ACL.PERM_INSERT))
            {
                ActionURL designerURL = AssayService.get().getDesignerURL(container, protocol, false);
                html.append("[<a href=\"").append(designerURL.getLocalURIString()).append("\">edit assay design</a>]");
            }
            else
                html.append(" Please ask an administrator for assistance.");
        }

        if (html.length() > 0)
            return new HtmlView(html.toString());
        else
            return null;
    }

    public static ExpData createData(Container c, File file, DataType dataType) throws ExperimentException
    {
        try
        {
            file = file.getCanonicalFile();
            ExpData data = ExperimentService.get().getExpDataByURL(file, c);
            if (data == null)
            {
                data = ExperimentService.get().createData(c, dataType, file.getName());
                data.setLSID(ExperimentService.get().generateGuidLSID(c, dataType));
                data.setDataFileURI(file.toURI());
            }
            return data;
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    public boolean canPublish()
    {
        return true;
    }

    public QueryView createRunView(ViewContext context, ExpProtocol protocol)
    {
        return new RunListQueryView(protocol, context);
    }

    public QueryView createRunDataView(ViewContext context, ExpProtocol protocol)
    {
        String name = getRunDataTableName(protocol);
        QuerySettings settings = new QuerySettings(context, name);
        settings.setSchemaName(AssayService.ASSAY_SCHEMA_NAME);
        settings.setQueryName(name);
        return new RunDataQueryView(protocol, context, settings);
    }


    public String getRunListTableName(ExpProtocol protocol)
    {
        return protocol.getName() + " Runs";
    }

    public String getRunDataTableName(ExpProtocol protocol)
    {
        return protocol.getName() + " Data";
    }

    public void deleteProtocol(ExpProtocol protocol, User user) throws ExperimentException
    {
        for (Domain domain : getDomains(protocol))
        {
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
}
