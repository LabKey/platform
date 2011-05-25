/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.IAssayDomainType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.ui.PropertiesEditorUtil;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.qc.DataTransformer;
import org.labkey.api.qc.DataValidator;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformDataHandler;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.actions.AssayDetailRedirectAction;
import org.labkey.api.study.actions.AssayResultDetailsAction;
import org.labkey.api.study.actions.AssayRunDetailsAction;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.DesignerAction;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.study.query.RunListQueryView;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.sql.ResultSet;
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

    public static final String ASSAY_SPECIMEN_MATCH_COLUMN_NAME = "AssayMatch";

    public static final String IMPORT_DATA_LINK_NAME = "Import Data";

    public static final FieldKey BATCH_ROWID_FROM_RUN = FieldKey.fromParts(AssayService.BATCH_COLUMN_NAME, "RowId");

    public static final DataType RELATED_FILE_DATA_TYPE = new DataType("RelatedFile");

    protected final String _protocolLSIDPrefix;
    protected final String _runLSIDPrefix;
    protected AssayTableMetadata _tableMetadata;
    protected final AssayDataType _dataType;

    public AbstractAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, AssayDataType dataType, AssayTableMetadata tableMetadata)
    {
        _dataType = dataType;
        _protocolLSIDPrefix = protocolLSIDPrefix;
        _runLSIDPrefix = runLSIDPrefix;
        _tableMetadata = tableMetadata;
        registerLsidHandler();
    }

    public AssaySchema getProviderSchema(User user, Container container, ExpProtocol protocol)
    {
        return null;
    }

    @Override
    public String getResourceName()
    {
        return getName();
    }

    public ActionURL copyToStudy(ViewContext viewContext, ExpProtocol protocol, @Nullable Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addInClause(getTableMetadata().getResultRowIdFieldKey().toString(), dataKeys.keySet());

            AssaySchema schema = AssayService.get().createSchema(viewContext.getUser(), viewContext.getContainer());
            ContainerFilterable dataTable = createDataTable(schema, protocol, true);
            dataTable.setContainerFilter(new ContainerFilter.CurrentAndSubfolders(viewContext.getUser()));

            FieldKey objectIdFK = getTableMetadata().getResultRowIdFieldKey();
            FieldKey runLSIDFK = new FieldKey(getTableMetadata().getRunFieldKeyFromResults(), ExpRunTable.Column.LSID.toString());

            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(dataTable, Arrays.asList(objectIdFK, runLSIDFK));
            ColumnInfo rowIdColumn = columns.get(objectIdFK);
            ColumnInfo runLSIDColumn = columns.get(runLSIDFK);

            SQLFragment sql = QueryService.get().getSelectSQL(dataTable, columns.values(), filter, null, Table.ALL_ROWS, 0, false);

            List<Map<String, Object>> dataMaps = new ArrayList<Map<String, Object>>();

            Container sourceContainer = null;

            ResultSet rs = null;

            try
            {
                rs = Table.executeQuery(dataTable.getSchema(), sql);
                while (rs.next())
                {
                    AssayPublishKey publishKey = dataKeys.get(((Number)rowIdColumn.getValue(rs)).intValue());

                    Container targetStudyContainer = study;
                    if (publishKey.getTargetStudy() != null)
                        targetStudyContainer = publishKey.getTargetStudy();
                    assert targetStudyContainer != null;

                    TimepointType studyType = AssayPublishService.get().getTimepointType(targetStudyContainer);

                    Map<String, Object> dataMap = new HashMap<String, Object>();

                    String runLSID = (String)runLSIDColumn.getValue(rs);
                    String sourceLSID = getSourceLSID(runLSID, publishKey.getDataId());
                    if (sourceContainer == null)
                    {
                        sourceContainer = ExperimentService.get().getExpRun(runLSID).getContainer();
                    }

                    dataMap.put(AssayPublishService.PARTICIPANTID_PROPERTY_NAME, publishKey.getParticipantId());
                    dataMap.put(AssayPublishService.SEQUENCENUM_PROPERTY_NAME, publishKey.getVisitId());
                    if (TimepointType.DATE == studyType)
                    {
                        dataMap.put(AssayPublishService.DATE_PROPERTY_NAME, publishKey.getDate());
                    }
                    dataMap.put(AssayPublishService.SOURCE_LSID_PROPERTY_NAME, sourceLSID);
                    dataMap.put(getTableMetadata().getDatasetRowIdPropertyName(), publishKey.getDataId());
                    dataMap.put(AssayPublishService.TARGET_STUDY_PROPERTY_NAME, targetStudyContainer);

                    dataMaps.add(dataMap);
                }

                return AssayPublishService.get().publishAssayData(viewContext.getUser(), sourceContainer, study, protocol.getName(), protocol,
                        dataMaps, getTableMetadata().getDatasetRowIdPropertyName(), errors);
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    protected String getSourceLSID(String runLSID, int dataId)
    {
        return runLSID;
    }

    protected void registerLsidHandler()
    {
        LsidManager.get().registerHandler(_runLSIDPrefix, new LsidManager.ExpRunLsidHandler());
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

        for (Map.Entry<String, File> entry : files.entrySet())
        {
            ExpData data = createData(context.getContainer(), entry.getValue(), entry.getValue().getName(), _dataType);
            outputDatas.put(data, "Data");
        }

        File primaryFile = files.get(AssayDataCollector.PRIMARY_FILE);
        if (primaryFile != null)
        {
            addRelatedOutputDatas(context.getContainer(), outputDatas, primaryFile, Collections.<AssayDataType>emptyList());
        }
    }

    protected FileFilter getRelatedOutputDataFileFilter(final File primaryFile, final String baseName)
    {
        return new FileFilter()
        {
            public boolean accept(File f)
            {
                // baseName doesn't include the trailing '.', so add it here.  We want to associate myRun.jpg
                // with myRun.xls, but we don't want to associate myRun2.xls with myRun.xls (which will happen without
                // the trailing dot in the check).
                return f.getName().startsWith(baseName + ".") && !primaryFile.equals(f);
            }
        };
    }

    /**
     * Add files that follow the general naming convention (same basename) as the primary file
     * @param knownRelatedDataTypes data types that should be given a particular LSID or role, others file types
     * will have them auto-generated based on their extension
     */
    public void addRelatedOutputDatas(Container container, Map<ExpData, String> outputDatas, final File primaryFile, List<AssayDataType> knownRelatedDataTypes) throws ExperimentException
    {
        final String baseName = getDataType().getFileType().getBaseName(primaryFile);
        if (baseName != null)
        {
            // Grab all the files that are related based on naming convention
            File[] relatedFiles = primaryFile.getParentFile().listFiles(getRelatedOutputDataFileFilter(primaryFile, baseName));
            if (relatedFiles != null)
            {
                for (File relatedFile : relatedFiles)
                {
                    Pair<ExpData, String> dataOutput = createdRelatedOutputData(container, knownRelatedDataTypes, baseName, relatedFile);
                    outputDatas.put(dataOutput.getKey(), dataOutput.getValue());
                }
            }
        }
    }

    /** Create an ExpData object for the file, and figure out what its role name should be */
    public static Pair<ExpData, String> createdRelatedOutputData(Container container, List<AssayDataType> knownRelatedDataTypes, String baseName, File relatedFile)
    {
        String roleName = null;
        DataType dataType = null;
        for (AssayDataType inputType : knownRelatedDataTypes)
        {
            // Check if we recognize it as a specially handled file type
            if (inputType.getFileType().isMatch(relatedFile.getName(), baseName))
            {
                roleName = inputType.getRole();
                dataType = inputType;
                break;
            }
        }
        // If not, make up a new type and role for it
        if (roleName == null)
        {
            roleName = relatedFile.getName().substring(baseName.length());
            while (roleName.length() > 0 && (roleName.startsWith(".") || roleName.startsWith("-") || roleName.startsWith("_") || roleName.startsWith(" ")))
            {
                roleName = roleName.substring(1);
            }
            if ("".equals(roleName))
            {
                roleName = null;
            }
            dataType = RELATED_FILE_DATA_TYPE;
        }
        return new Pair<ExpData, String>(createData(container, relatedFile, relatedFile.getName(), dataType), roleName);
    }

    public AssayTableMetadata getTableMetadata()
    {
        return _tableMetadata;
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

    public Domain getResultsDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_DATA);
    }

    public Domain getBatchDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_BATCH);
    }

    public Domain getRunDomain(ExpProtocol protocol)
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
        return addProperty(pd, value == null ? null : value.getValueMvAware(), dataMap, types);
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
        if (AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(prop.getName()))
            prop.setDimension(true);
        if (AbstractAssayProvider.VISITID_PROPERTY_NAME.equals(prop.getName()))
            prop.setMeasure(false);

        if (allowDefaultValues(domain))
        {
            if (AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(prop.getName()) ||
                AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(prop.getName()) ||
                AbstractAssayProvider.VISITID_PROPERTY_NAME.equals(prop.getName()) ||
                AbstractAssayProvider.DATE_PROPERTY_NAME.equals(prop.getName()))
            {
                prop.setDefaultValueTypeEnum(DefaultValueType.FIXED_EDITABLE);
            }
            else
            {
                prop.setDefaultValueTypeEnum(getDefaultValueDefault(domain));
            }
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
        studyProp.setShownInInsertView(true);

        domain.setDescription("The user is prompted for batch properties once for each set of runs they import. The batch " +
                "is a convenience to let users set properties that seldom change in one place and import many runs " +
                "using them. This is the first step of the import process.");
        return new Pair<Domain, Map<DomainProperty, Object>>(domain, Collections.<DomainProperty, Object>emptyMap());
    }

    /**
     * @return domains and their default property values
     */
    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = new ArrayList<Pair<Domain, Map<DomainProperty, Object>>>();
        try
        {
            ExperimentService.get().ensureTransaction();

            result.add(createBatchDomain(c, user));
            result.add(createRunDomain(c, user));

            ExperimentService.get().commitTransaction();
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }

        return result;
    }

    public List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles, AssayRunUploadForm context)
    {
        List<AssayDataCollector> result = new ArrayList<AssayDataCollector>();
        if (!PipelineDataCollector.getFileQueue(context).isEmpty())
        {
            result.add(new PipelineDataCollector());
        }
        else
        {
            if (uploadedFiles != null)
                result.add(new PreviouslyUploadedDataCollector(uploadedFiles));
            result.add(new FileUploadDataCollector());
        }
        return result;
    }

    protected ExpRun createExperimentRun(AssayRunUploadContext context) throws ExperimentException
    {
        String runName = context.getName();
        File file = null;
        {
            try
            {
                Map<String, File> uploadedData = context.getUploadedData();
                if (uploadedData != null && uploadedData.size() != 0)
                {
                    file = uploadedData.get(AssayDataCollector.PRIMARY_FILE);
                    if (runName == null)
                    {
                        runName = file.getName();
                    }
                }
            }
            catch (IOException e)
            {
                throw new ExperimentException(e);
            }
        }
        ExpRun run = createExperimentRun(runName, context.getContainer(), context.getProtocol());

        run.setComments(context.getComments());
        if (file != null)
        {
            run.setFilePathRoot(file.getParentFile());
        }
        else
        {
            run.setFilePathRoot(PipelineService.get().findPipelineRoot(context.getContainer()).getRootPath());
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

    private static final Object BATCH_CREATION_SYNC = new Object();

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
        Map<ExpData, String> transformedDatas = new HashMap<ExpData, String>();

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
        try
        {
            boolean saveBatchProps = false;
            scope.ensureTransaction();

            // Save the batch first
            if (batch == null)
            {
                // synchronize to prevent two uploads from grabbing the same batch ID (see bug 8685)
                synchronized (BATCH_CREATION_SYNC)
                {
                    // Make sure that we have a batch to associate with this run
                    batch = AssayService.get().createStandardBatch(run.getContainer(), null, context.getProtocol());
                    batch.save(context.getUser());
                }
                saveBatchProps = true;
            }
            run.save(context.getUser());
            // Add the run to the batch so that we can find it when we're loading the data files
            batch.addRuns(context.getUser(), run);

            ViewBackgroundInfo info = new ViewBackgroundInfo(context.getContainer(), context.getUser(), context.getActionURL());
            XarContext xarContext = new AssayUploadXarContext("Simple Run Creation", context);

            run = ExperimentService.get().insertSimpleExperimentRun(run,
                    inputMaterials,
                    inputDatas,
                    outputMaterials,
                    outputDatas,
                    transformedDatas,
                    info,
                    LOG,
                    false);

            // handle data transformation
            TransformResult transformResult = transform(context, run);
            List<ExpData> insertedDatas = new ArrayList<ExpData>();
            if (context instanceof AssayRunUploadForm)
                ((AssayRunUploadForm)context).setTransformResult(transformResult);

/*
            if (!transformResult.getTransformedData().isEmpty())
            {
                for (Map.Entry<DataType, File> entry : transformResult.getTransformedData().entrySet())
                {
                    // for any transformed data, we want to attach it to the run and request that it be imported
                    // instead of the original data.
                    ExpData data = createData(context.getContainer(), entry.getValue(), "transformed output", entry.getKey());
                    data.setSourceApplication(run.getOutputProtocolApplication());
                    data.setRun(run);
                    data.save(context.getUser());

                    run.getOutputProtocolApplication().addDataInput(context.getUser(), data, "Data");
                    insertedDatas.add(data);
                }
            }
            else
            {
                insertedDatas.addAll(inputDatas.keySet());
                insertedDatas.addAll(outputDatas.keySet());
            }
*/

            if (saveBatchProps)
            {
                if (!transformResult.getBatchProperties().isEmpty())
                {
                    Map<DomainProperty, String> props = transformResult.getBatchProperties();
                    List<ValidationError> errors = validateProperties(props);
                    if (!errors.isEmpty())
                        throw new ValidationException(errors);
                    savePropertyObject(batch.getLSID(), props, context.getContainer());
                }
                else
                    savePropertyObject(batch.getLSID(), batchProperties, context.getContainer());
            }

            if (!transformResult.getRunProperties().isEmpty())
            {
                Map<DomainProperty, String> props = transformResult.getRunProperties();
                List<ValidationError> errors = validateProperties(props);
                if (!errors.isEmpty())
                    throw new ValidationException(errors);
                savePropertyObject(run.getLSID(), props, context.getContainer());
            }
            else
                savePropertyObject(run.getLSID(), runProperties, context.getContainer());

            if (transformResult.getTransformedData().isEmpty())
            {
                insertedDatas.addAll(inputDatas.keySet());
                insertedDatas.addAll(outputDatas.keySet());

                for (ExpData insertedData : insertedDatas)
                {
                    insertedData.findDataHandler().importFile(insertedData, insertedData.getFile(), info, LOG, xarContext);
                }
            }
            else
            {
                ExpData data = ExperimentService.get().createData(context.getContainer(), getDataType());
                ExperimentDataHandler handler = data.findDataHandler();

                // this should assert to always be true
                if (handler instanceof TransformDataHandler)
                {
                    for (Map.Entry<ExpData, List<Map<String, Object>>> entry : transformResult.getTransformedData().entrySet())
                    {
                        ExpData expData = entry.getKey();

                        expData.setSourceApplication(run.getOutputProtocolApplication());
                        expData.setRun(run);
                        expData.save(context.getUser());

                        run.getOutputProtocolApplication().addDataInput(context.getUser(), expData, "Data");
                        // Add to the cached list of outputs 
                        run.getDataOutputs().add(expData);

                        ((TransformDataHandler)handler).importTransformDataMap(expData, context, run, entry.getValue());
                    }
                }
            }
            validate(context, run);

            scope.commitTransaction();

            return new Pair<ExpRun, ExpExperiment>(run, batch);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    public static List<ValidationError> validateProperties(Map<DomainProperty, String> properties)
    {
        List<ValidationError> errors = new ArrayList<ValidationError>();

        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            DomainProperty dp = entry.getKey();
            String value = entry.getValue();
            String label = dp.getPropertyDescriptor().getNonBlankCaption();
            PropertyType type = dp.getPropertyDescriptor().getPropertyType();
            boolean missing = (value == null || value.length() == 0);
            if (dp.isRequired() && missing)
            {
                errors.add(new SimpleValidationError(label + " is required and must be of type " + ColumnInfo.getFriendlyTypeName(type.getJavaType()) + "."));
            }
            else if (!missing)
            {
                try
                {
                    ConvertUtils.convert(value, type.getJavaType());
                }
                catch (ConversionException e)
                {
                    String message = label + " must be of type " + ColumnInfo.getFriendlyTypeName(type.getJavaType()) + ".";
                    message +=  "  Value \"" + value + "\" could not be converted";
                    if (e.getCause() instanceof ArithmeticException)
                        message +=  ": " + e.getCause().getLocalizedMessage();
                    else
                        message += ".";

                    errors.add(new SimpleValidationError(message));
                }
            }
        }
        return errors;
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

    public Pair<ExpProtocol.AssayDomainTypes, DomainProperty> findTargetStudyProperty(ExpProtocol protocol)
    {
        DomainProperty targetStudyDP;

        Domain domain = getResultsDomain(protocol);
        if (domain != null && null != (targetStudyDP = domain.getPropertyByName(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME)))
            return new Pair<ExpProtocol.AssayDomainTypes, DomainProperty>(ExpProtocol.AssayDomainTypes.Result, targetStudyDP);

        domain = getRunDomain(protocol);
        if (domain != null && null != (targetStudyDP = domain.getPropertyByName(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME)))
            return new Pair<ExpProtocol.AssayDomainTypes, DomainProperty>(ExpProtocol.AssayDomainTypes.Run, targetStudyDP);

        domain = getBatchDomain(protocol);
        if (domain != null && null != (targetStudyDP = domain.getPropertyByName(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME)))
            return new Pair<ExpProtocol.AssayDomainTypes, DomainProperty>(ExpProtocol.AssayDomainTypes.Batch, targetStudyDP);

        return null;
    }

    // CONSIDER: combining with .getTargetStudy()
    // UNDONE: Doesn't look at TargetStudy in Results domain yet.
    public Container getAssociatedStudyContainer(ExpProtocol protocol, Object dataId)
    {
        Pair<ExpProtocol.AssayDomainTypes, DomainProperty> pair = findTargetStudyProperty(protocol);
        if (pair == null)
            return null;

        DomainProperty targetStudyColumn = pair.second;

        ExpData data = getDataForDataRow(dataId, protocol);
        if (data == null)
            return null;
        ExpRun run = data.getRun();
        if (run == null)
            return null;

        ExpObject source;
        switch (pair.first)
        {

            case Run:
                source = run;
                break;

            case Result:
                // Ignore Results domain TargetStudy for now.
                // The participant resolver will find the TargetStudy on the row.
            case Batch:
            default:
                source = AssayService.get().findBatch(run);
                break;
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

    public abstract ExpData getDataForDataRow(Object dataRowId, ExpProtocol protocol);


    public ActionURL getImportURL(Container container, ExpProtocol protocol)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(container, protocol, UploadWizardAction.class);
    }

    public ExpRunTable createRunTable(AssaySchema schema, ExpProtocol protocol)
    {
        final ExpRunTable runTable = (ExpRunTable)ExpSchema.TableType.Runs.createTable(new ExpSchema(schema.getUser(), schema.getContainer()));
        ColumnInfo dataLinkColumn = runTable.getColumn(ExpRunTable.Column.Name);
        dataLinkColumn.setLabel("Assay Id");
        dataLinkColumn.setDescription("The assay/experiment ID that uniquely identifies this assay run.");
        dataLinkColumn.setURL(new DetailsURL(new ActionURL(AssayDetailRedirectAction.class, schema.getContainer()), Collections.singletonMap("runId", "rowId")));

        List<FieldKey> visibleColumns = new ArrayList<FieldKey>(runTable.getDefaultVisibleColumns());
        visibleColumns.remove(FieldKey.fromParts(ExpRunTable.Column.Protocol));
        runTable.setDefaultVisibleColumns(visibleColumns);
        
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

        if (runDomain)
        {
            for (ExpRunTable.Column column : ExpRunTable.Column.values())
            {
                reservedNames.add(column.toString());
            }
        }
        if (batchDomain)
        {
            for (ExpExperimentTable.Column column : ExpExperimentTable.Column.values())
            {
                reservedNames.add(column.toString());
            }
        }
        if (batchDomain || runDomain)
        {
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
                propCopy.setFormat(propSrc.getFormat());
                propCopy.setLabel(propSrc.getLabel());
                propCopy.setName(propSrc.getName());
                propCopy.setDescription(propSrc.getDescription());
                propCopy.setType(propSrc.getType());
                propCopy.setRequired(propSrc.isRequired());
                propCopy.setHidden(propSrc.isHidden());
                propCopy.setMvEnabled(propSrc.isMvEnabled());
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

    // UNDONE: also look at result row for TargetStudy
    // CONSIDER: combine with .getAssociatedStudyContainer()
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

        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(run.getObjectProperties());
        ExpExperiment batch = AssayService.get().findBatch(run);
        if (batch != null)
        {
            props.putAll(batch.getObjectProperties());
        }

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

    public boolean allowDefaultValues(Domain domain)
    {
        Lsid domainLsid = new Lsid(domain.getTypeURI());
        return !ExpProtocol.ASSAY_DOMAIN_DATA.equals(domainLsid.getNamespacePrefix());
    }

    public DefaultValueType[] getDefaultValueOptions(Domain domain)
    {
        return DefaultValueType.values();
    }

    public DefaultValueType getDefaultValueDefault(Domain domain)
    {
        return DefaultValueType.LAST_ENTERED;
    }

    public static ExpData createData(Container c, File file, String name, DataType dataType)
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
        else
        {
            if (!dataType.matches(new Lsid(data.getLSID())))
            {
                // Reset its LSID so that it's the correct type
                data.setLSID(ExperimentService.get().generateGuidLSID(c, dataType));
            }
        }
        return data;
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
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), AssaySchema.NAME);
        String name = AssayService.get().getResultsTableName(protocol);
        QuerySettings settings = schema.getSettings(context, name, name);
        ResultsQueryView queryView = new ResultsQueryView(protocol, context, settings);

        if (hasCustomView(ExpProtocol.AssayDomainTypes.Result, true))
        {
            ActionURL resultDetailsURL = new ActionURL(AssayResultDetailsAction.class, context.getContainer());
            resultDetailsURL.addParameter("rowId", protocol.getRowId());
            Map<String, String> params = new HashMap<String, String>();
            // map ObjectId to url parameter ResultDetailsForm.dataRowId
            params.put("dataRowId", AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME);

            AbstractTableInfo ati = (AbstractTableInfo)queryView.getTable();
            ati.setDetailsURL(new DetailsURL(resultDetailsURL, params));
            queryView.setShowDetailsColumn(true);
        }

        return queryView;
    }

    public boolean hasCustomView(IAssayDomainType domainType, boolean details)
    {
        return false;
    }

    public ModelAndView createBeginView(ViewContext context, ExpProtocol protocol)
    {
        return null;
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
        List<Pair<Domain, Map<DomainProperty, Object>>> domainInfos =  getDomains(protocol);
        List<Domain> domains = new ArrayList<Domain>();
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : domainInfos)
            domains.add(domainInfo.getKey());

        Set<Container> defaultValueContainers = new HashSet<Container>();
        defaultValueContainers.add(protocol.getContainer());
        defaultValueContainers.addAll(protocol.getExpRunContainers());
        clearDefaultValues(defaultValueContainers, domains);
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : domainInfos)
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

    private void clearDefaultValues(Set<Container> containers, List<Domain> domains)
    {
        for (Domain domain : domains)
        {
            for (Container container : containers)
                DefaultValueService.get().clearDefaultValues(container, domain);
        }
    }


    public Class<? extends Controller> getDesignerAction()
    {
        return DesignerAction.class;
    }

    @Override
    public Class<? extends Controller> getDataImportAction()
    {
        // default to assay designer, except in the case of tsv where the assay can support inferring the data domain
        return DesignerAction.class;
        //return ImportAction.class;
    }

    /**
     * Adds columns to an assay data table, providing a link to any datasets that have
     * had data copied into them.
     * @return The names of the added columns that should be visible
     */
    protected Set<String> addCopiedToStudyColumns(AbstractTableInfo table, ExpProtocol protocol, User user, boolean setVisibleColumns)
    {
        Set<String> visibleColumnNames = new HashSet<String>();
        int datasetIndex = 0;
        Set<String> usedColumnNames = new HashSet<String>();
        for (final DataSet assayDataSet : StudyService.get().getDatasetsForAssayProtocol(protocol.getRowId()))
        {
            if (!assayDataSet.getContainer().hasPermission(user, ReadPermission.class) || !assayDataSet.canRead(user))
            {
                continue;
            }

            String datasetIdColumnName = "dataset" + datasetIndex++;
            final StudyDataSetColumn datasetColumn = new StudyDataSetColumn(table,
                datasetIdColumnName, this, assayDataSet, user);
            datasetColumn.setHidden(true);
            table.addColumn(datasetColumn);

            String studyCopiedSql = "(SELECT CASE WHEN " + datasetColumn.getDatasetIdAlias() +
                "._key IS NOT NULL THEN 'copied' ELSE NULL END)";

            String studyName = assayDataSet.getStudy().getLabel();
            if (studyName == null)
                continue; // No study in that folder
            String studyColumnName = "copied_to_" + PropertiesEditorUtil.sanitizeName(studyName);

            // column names must be unique. Prevent collisions
            while (usedColumnNames.contains(studyColumnName))
                studyColumnName = studyColumnName + datasetIndex;
            usedColumnNames.add(studyColumnName);

            final ExprColumn studyCopiedColumn = new ExprColumn(table,
                studyColumnName,
                new SQLFragment(studyCopiedSql),
                JdbcType.VARCHAR,
                datasetColumn);
            final String copiedToStudyColumnCaption = "Copied to " + studyName;
            studyCopiedColumn.setLabel(copiedToStudyColumnCaption);
            studyCopiedColumn.setURL(StringExpressionFactory.createURL(StudyService.get().getDatasetURL(assayDataSet.getContainer(), assayDataSet.getDataSetId())));

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

    public void setValidationAndAnalysisScripts(ExpProtocol protocol, List<File> scripts, ScriptType type) throws ExperimentException
    {
        if (scripts.size() > 1)
            throw new ExperimentException("Only one script is supported for this release");

        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(protocol.getObjectProperties());
        String propertyURI;

        propertyURI = type.getPropertyURI(protocol);

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
                    throw new ExperimentException("Script engine for the extension : " + ext + " has not been registered.\nFor documentation about how to configure a " +
                            "scripting engine, paste this link into your browser: \"https://www.labkey.org/wiki/home/Documentation/page.view?name=configureScripting\".");
            }
            else
                throw new ExperimentException("The validation script is invalid or does not exist");
        }
        protocol.setObjectProperties(props);
    }

    public List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope, ScriptType type)
    {
        if (scope == Scope.ASSAY_DEF || scope == Scope.ALL)
        {
            String propertyURI = type.getPropertyURI(protocol);
            ObjectProperty prop = protocol.getObjectProperties().get(propertyURI);
            if (prop != null)
            {
                return Collections.singletonList(new File(prop.getStringValue()));
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void setSaveScriptFiles(ExpProtocol protocol, boolean save) throws ExperimentException
    {
        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(protocol.getObjectProperties());
        String propertyURI = protocol.getLSID() + "#SaveScriptFiles";

        ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(),
                propertyURI, save);
        props.put(propertyURI, prop);

        protocol.setObjectProperties(props);
    }

    @Override
    public boolean getSaveScriptFiles(ExpProtocol protocol)
    {
        String propertyURI = protocol.getLSID() + "#SaveScriptFiles";
        ObjectProperty prop = protocol.getObjectProperties().get(propertyURI);

        if (prop != null)
        {
            Object o = prop.value();
            if (o instanceof Boolean)
                return (Boolean)o;
        }
        return false;  
    }

    public void validate(AssayRunUploadContext context, ExpRun run) throws ValidationException
    {
        DataValidator validator = context.getProvider().getDataValidator();
        if (validator != null)
            validator.validate(context, run);
    }

    public AssayDataType getDataType()
    {
        return _dataType;
    }

    /**
     * Return the helper to handle data exchange between the server and external scripts.
     */
    public DataExchangeHandler createDataExchangeHandler()
    {
        return null;
    }

    public TransformResult transform(AssayRunUploadContext context, ExpRun run) throws ValidationException
    {
        DataTransformer transformer = context.getProvider().getDataTransformer();
        if (transformer != null)
            return transformer.transform(context, run);

        return DefaultTransformResult.createEmptyResult();
    }

    public DataTransformer getDataTransformer()
    {
        return new DefaultDataTransformer();
    }

    public DataValidator getDataValidator()
    {
        return new DefaultDataTransformer();
    }

    public void upgradeAssayDefinitions(User user, ExpProtocol protocol, double targetVersion) throws SQLException {}
}
