/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.Filter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.defaults.SetDefaultValuesAssayAction;
import org.labkey.api.exp.DomainNotFoundException;
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
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.IAssayDomainType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.DesignerAction;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.assay.pipeline.AssayRunAsyncContext;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Sep 14, 2007
 */
public abstract class AbstractAssayProvider implements AssayProvider
{
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
    public static final String MANAGE_ASSAY_DESIGN_LINK = "Manage assay design";
    public static final String SET_DEFAULT_VALUES_LINK = "Set default values";

    public static final FieldKey BATCH_ROWID_FROM_RUN = FieldKey.fromParts(AssayService.BATCH_COLUMN_NAME, "RowId");

    public static final DataType RELATED_FILE_DATA_TYPE = new DataType("RelatedFile");
    public static final String SAVE_SCRIPT_FILES_PROPERTY_SUFFIX = "SaveScriptFiles";
    public static final String EDITABLE_RUNS_PROPERTY_SUFFIX = "EditableRuns";
    public static final String EDITABLE_RESULTS_PROPERTY_SUFFIX = "EditableResults";
    public static final String BACKGROUND_UPLOAD_PROPERTY_SUFFIX = "BackgroundUpload";

    protected final String _protocolLSIDPrefix;
    protected final String _runLSIDPrefix;
    protected final Set<Module> _requiredModules = new HashSet<>();

    private final Module _declaringModule;
    @Nullable protected AssayDataType _dataType;

    public int _maxFileInputs = 1;

    public AbstractAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, @Nullable AssayDataType dataType, Module declaringModule)
    {
        _dataType = dataType;
        _protocolLSIDPrefix = protocolLSIDPrefix;
        _runLSIDPrefix = runLSIDPrefix;
        _declaringModule = declaringModule;
    }

    public AssayProviderSchema createProviderSchema(User user, Container container, Container targetStudy)
    {
        return new AssayProviderSchema(user, container, this, targetStudy);
    }

    public ActionURL copyToStudy(User user, Container assayDataContainer, ExpProtocol protocol, @Nullable Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addInClause(getTableMetadata(protocol).getResultRowIdFieldKey(), dataKeys.keySet());

            AssayProtocolSchema schema = createProtocolSchema(user, assayDataContainer, protocol, study);
            ContainerFilterable dataTable = schema.createDataTable();
            dataTable.setContainerFilter(new ContainerFilter.CurrentAndSubfolders(user));

            FieldKey objectIdFK = getTableMetadata(protocol).getResultRowIdFieldKey();
            FieldKey runLSIDFK = new FieldKey(getTableMetadata(protocol).getRunFieldKeyFromResults(), ExpRunTable.Column.LSID.toString());

            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(dataTable, Arrays.asList(objectIdFK, runLSIDFK));
            ColumnInfo rowIdColumn = columns.get(objectIdFK);
            ColumnInfo runLSIDColumn = columns.get(runLSIDFK);

            SQLFragment sql = QueryService.get().getSelectSQL(dataTable, columns.values(), filter, null, Table.ALL_ROWS, Table.NO_OFFSET, false);

            List<Map<String, Object>> dataMaps = new ArrayList<>();
            Container sourceContainer = null;
            Map<Container, Set<Integer>> rowIdsByTargetContainer = new HashMap<>();

            try (ResultSet rs = new SqlSelector(dataTable.getSchema(), sql).getResultSet())
            {
                while (rs.next())
                {
                    AssayPublishKey publishKey = dataKeys.get(((Number)rowIdColumn.getValue(rs)).intValue());

                    Container targetStudyContainer = study;
                    if (publishKey.getTargetStudy() != null)
                        targetStudyContainer = publishKey.getTargetStudy();
                    assert targetStudyContainer != null;

                    TimepointType studyType = AssayPublishService.get().getTimepointType(targetStudyContainer);

                    Map<String, Object> dataMap = new HashMap<>();

                    String runLSID = (String)runLSIDColumn.getValue(rs);
                    String sourceLSID = getSourceLSID(runLSID, publishKey.getDataId());

                    if (sourceContainer == null)
                    {
                        sourceContainer = ExperimentService.get().getExpRun(runLSID).getContainer();
                    }

                    dataMap.put(AssayPublishService.PARTICIPANTID_PROPERTY_NAME, publishKey.getParticipantId());

                    if (!studyType.isVisitBased())
                    {
                        dataMap.put(AssayPublishService.DATE_PROPERTY_NAME, publishKey.getDate());
                    }
                    else
                    {
                        // add the sequencenum only for visit-based studies, a date based sequencenum will get calculated
                        // for date-based studies in the ETL layer
                        dataMap.put(AssayPublishService.SEQUENCENUM_PROPERTY_NAME, publishKey.getVisitId());
                    }

                    dataMap.put(AssayPublishService.SOURCE_LSID_PROPERTY_NAME, sourceLSID);
                    dataMap.put(getTableMetadata(protocol).getDatasetRowIdPropertyName(), publishKey.getDataId());
                    dataMap.put(AssayPublishService.TARGET_STUDY_PROPERTY_NAME, targetStudyContainer);

                    // Remember which rows we're planning to copy, partitioned by the target study
                    Set<Integer> rowIds = rowIdsByTargetContainer.get(targetStudyContainer);
                    if (rowIds == null)
                    {
                        rowIds = new HashSet<>();
                        rowIdsByTargetContainer.put(targetStudyContainer, rowIds);
                    }
                    rowIds.add(publishKey.getDataId());

                    dataMaps.add(dataMap);
                }

                checkForAlreadyCopiedRows(user, protocol, errors, rowIdsByTargetContainer);

                if (!errors.isEmpty())
                {
                    return null;
                }
                
                return AssayPublishService.get().publishAssayData(user, sourceContainer, study, protocol.getName(), protocol,
                        dataMaps, getTableMetadata(protocol).getDatasetRowIdPropertyName(), errors);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private void checkForAlreadyCopiedRows(User user, ExpProtocol protocol, List<String> errors, Map<Container, Set<Integer>> rowIdsByTargetContainer)
    {
        for (Map.Entry<Container, Set<Integer>> entry : rowIdsByTargetContainer.entrySet())
        {
            Study targetStudy = StudyService.get().getStudy(entry.getKey());

            // Look for an existing dataset backed by this assay definition
            for (Dataset dataset : targetStudy.getDatasets())
            {
                if (protocol.equals(dataset.getAssayProtocol()) && dataset.getKeyPropertyName() != null)
                {
                    // Check to see if it already has the data rows that are being copied
                    TableInfo tableInfo = dataset.getTableInfo(user, false);
                    Filter datasetFilter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts(dataset.getKeyPropertyName()), entry.getValue()));
                    long existingRowCount = new TableSelector(tableInfo, datasetFilter, null).getRowCount();
                    if (existingRowCount > 0)
                    {
                        // If so, don't let the user copy them again, even if they have different participant/visit/date info
                        String errorMessage = existingRowCount == 1 ? "One of the selected rows has" : (existingRowCount + " of the selected rows have");
                        errorMessage += " already been copied to the study \"" + targetStudy.getLabel() + "\" in " + entry.getKey().getPath();
                        errorMessage += " (RowIds: " + entry.getValue() + ")";
                        errors.add(errorMessage);
                    }
                }
            }
        }
    }

    protected String getSourceLSID(String runLSID, int dataId)
    {
        return runLSID;
    }

    public void registerLsidHandler()
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

    public String getProtocolPattern()
    {
        return "%:" + Lsid.encodePart(_protocolLSIDPrefix).replace("%", "\\%") + ".%";
    }

    @NotNull
    public abstract AssayTableMetadata getTableMetadata(@NotNull ExpProtocol protocol);

    public static String getDomainURIForPrefix(ExpProtocol protocol, String domainPrefix)
    {
        String result = getDomainURIForPrefixIfExists(protocol, domainPrefix);
        if (result == null)
        {
            throw new IllegalArgumentException("No domain match for prefix '" + domainPrefix + "' in protocol with LSID '" + protocol.getLSID() + "'");
        }
        return result;
    }

    @Nullable
    public static String getDomainURIForPrefixIfExists(ExpProtocol protocol, String domainPrefix)
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
        return result;
    }

    public static Domain getDomainByPrefix(ExpProtocol protocol, String domainPrefix)
    {
        Container container = protocol.getContainer();
        return PropertyService.get().getDomain(container, getDomainURIForPrefix(protocol, domainPrefix));
    }

    @Nullable
    public static Domain getDomainByPrefixIfExists(ExpProtocol protocol, String domainPrefix)
    {
        String domainURI = getDomainURIForPrefixIfExists(protocol, domainPrefix);
        if (null == domainURI)
            return null;
        Container container = protocol.getContainer();
        return PropertyService.get().getDomain(container, domainURI);
    }

    public Domain getResultsDomain(ExpProtocol protocol)
    {
        return getDomainByPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_DATA);
    }

    public void changeDomain(User user, ExpProtocol protocol, GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update)
    {
        // NOTE: this will only be needed in HaplotypeAssayProvider; thus this is no-op.
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
        PropertyDescriptor pd = new PropertyDescriptor(null, type, name, label, sourceContainer);
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

    protected DomainProperty addProperty(Domain domain, String name, String label, PropertyType type, @Nullable String description)
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

    public static String getPresubstitutionLsid(String prefix)
    {
        return "urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":" + ASSAY_NAME_SUBSTITUTION;
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createRunDomain(Container c, User user)
    {
        Domain domain = PropertyService.get().createDomain(c, getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_RUN), "Run Fields");
        domain.setDescription("The user is prompted to enter run level properties for each file they import.  This is the second step of the import process.");
        return new Pair<>(domain, Collections.emptyMap());
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createBatchDomain(Container c, User user)
    {
        return createBatchDomain(c, user, true);
    }

    protected Pair<Domain, Map<DomainProperty, Object>> createBatchDomain(Container c, User user, boolean includeStandardProperties)
    {
        Domain domain = PropertyService.get().createDomain(c, getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_BATCH), "Batch Fields");
        domain.setDescription("The user is prompted for batch properties once for each set of runs they import. The batch " +
                "is a convenience to let users set properties that seldom change in one place and import many runs " +
                "using them. This is the first step of the import process.");

        if (includeStandardProperties)
        {
            List<ParticipantVisitResolverType> resolverTypes = getParticipantVisitResolverTypes();
            if (resolverTypes != null && resolverTypes.size() > 0)
            {
                DomainProperty resolverProperty = addProperty(domain, PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME, PARTICIPANT_VISIT_RESOLVER_PROPERTY_CAPTION, PropertyType.STRING);
                resolverProperty.setHidden(true);
            }

            DomainProperty studyProp = addProperty(domain, TARGET_STUDY_PROPERTY_NAME, TARGET_STUDY_PROPERTY_CAPTION, PropertyType.STRING);
            studyProp.setShownInInsertView(true);
        }

        return new Pair<>(domain, Collections.emptyMap());
    }

    /**
     * @return domains and their default property values
     */
    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = new ArrayList<>();

        result.add(createBatchDomain(c, user));
        result.add(createRunDomain(c, user));

        return result;
    }

    public List<AssayDataCollector> getDataCollectors(@Nullable Map<String, File> uploadedFiles, AssayRunUploadForm context)
    {
        return getDataCollectors(uploadedFiles, context, true);
    }

    @Override
    public String getResourceName()
    {
        return getName();
    }

    public List<AssayDataCollector> getDataCollectors(@Nullable Map<String, File> uploadedFiles, AssayRunUploadForm context, boolean allowFileReuseOnReRun)
    {
        List<AssayDataCollector> result = new ArrayList<>();
        if (!PipelineDataCollector.getFileQueue(context).isEmpty())
        {
            result.add(new PipelineDataCollector());
        }
        else
        {
            if (allowFileReuseOnReRun && context.getReRun() != null)
            {
                // In the re-run scenario, figure out what files to offer up for reuse

                Map<String, File> reusableFiles = new HashMap<>();
                // Include any files that were uploaded as part of this request
                if (uploadedFiles != null && !uploadedFiles.isEmpty())
                {
                    reusableFiles.putAll(uploadedFiles);
                }
                else
                {
                    // Look for input data files to the original version of the run
                    List<? extends ExpData> inputDatas = context.getReRun().getInputDatas(ExpDataRunInput.DEFAULT_ROLE, ExpProtocol.ApplicationType.ExperimentRunOutput);
                    if (inputDatas.size() == 1)
                    {
                        // There's exactly one input, so just use it
                        addReusableData(reusableFiles, inputDatas.get(0));
                    }
                    else if (inputDatas.size() > 1)
                    {
                        // The original run was likely run through a transform script
                        // See https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=16952
                        for (ExpData inputData : inputDatas)
                        {
                            // Look for a file that was created by the "core" step in the protocol. Transformed files
                            // are created by the ExperimentRunOutput step, and will have a different ActionSequence
                            if (inputData.getSourceApplication().getApplicationType() == ExpProtocol.ApplicationType.ProtocolApplication && inputData.getSourceApplication().getActionSequence() == ExperimentService.SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE && inputData.getSourceApplication().getActionSequence() == ExperimentService.SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE)
                            {
                                if (reusableFiles.size() >= getMaxFileInputs())
                                {
                                    throw new IllegalStateException("More than " + getMaxFileInputs() + " primary data file(s) associated with run: " + context.getReRun().getRowId() + "(\"" + reusableFiles.values() + "\" and \"" + inputData + "\")");
                                }
                                addReusableData(reusableFiles, inputData);
                            }
                        }
                    }
                }

                // Filter out any files that aren't under the current pipeline root, since we won't be able to resolve
                // them successfully due to security restrictions for what's an allowable input to the new run. See issue 18387.
                PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(context.getContainer());
                for (Iterator<Map.Entry<String, File>> iter = reusableFiles.entrySet().iterator(); iter.hasNext(); )
                {
                    Map.Entry<String, File> entry = iter.next();
                    // If it's not under the current pipeline root
                    if (pipeRoot == null || !pipeRoot.isUnderRoot(entry.getValue()))
                    {
                        // Remove it from the collection
                        iter.remove();
                    }
                }

                if (getMaxFileInputs() == 1)
                {
                    // This assay only allows for one input file, so keep it simple with separate options
                    // to reuse or re-upload
                    if (!reusableFiles.isEmpty())
                    {
                        result.add(new PreviouslyUploadedDataCollector(reusableFiles));
                    }
                    result.add(new FileUploadDataCollector(getMaxFileInputs()));
                }
                else
                {
                    // We allow multiple files per assay run, so give a UI that lets the user mix and match
                    // between existing ones and new ones
                    result.add(new FileUploadDataCollector(getMaxFileInputs(), reusableFiles));
                }
            }
            else
            {
                // Normal (non-rerun) scenario
                if (uploadedFiles != null)
                {
                    result.add(new PreviouslyUploadedDataCollector(uploadedFiles));
                }
                result.add(new FileUploadDataCollector(getMaxFileInputs()));
            }
        }
        return result;
    }

    private void addReusableData(Map<String, File> reusableFiles, ExpData inputData)
    {
        // Not all datas are associated with a file
        if (inputData.getFile() != null)
        {
            reusableFiles.put(AssayDataCollector.PRIMARY_FILE + (reusableFiles.size() == 0 ? "" : Integer.toString(reusableFiles.size())), inputData.getFile());
        }
    }

    @Override
    public AssayRunCreator getRunCreator()
    {
        return new DefaultAssayRunCreator<>(this);
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

        return ExperimentService.get().insertSimpleProtocol(protocol, user);
    }

    @Nullable
    public Pair<ExpProtocol.AssayDomainTypes, DomainProperty> findTargetStudyProperty(ExpProtocol protocol)
    {
        DomainProperty targetStudyDP;

        Domain domain = getResultsDomain(protocol);
        if (domain != null && null != (targetStudyDP = domain.getPropertyByName(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME)))
            return new Pair<>(ExpProtocol.AssayDomainTypes.Result, targetStudyDP);

        domain = getRunDomain(protocol);
        if (domain != null && null != (targetStudyDP = domain.getPropertyByName(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME)))
            return new Pair<>(ExpProtocol.AssayDomainTypes.Run, targetStudyDP);

        domain = getBatchDomain(protocol);
        if (domain != null && null != (targetStudyDP = domain.getPropertyByName(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME)))
            return new Pair<>(ExpProtocol.AssayDomainTypes.Batch, targetStudyDP);

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
            Map<String, Object> properties = OntologyManager.getProperties(source.getContainer(), source.getLSID());
            String targetStudyId = (String) properties.get(targetStudyColumn.getPropertyURI());

            if (targetStudyId != null)
                return ContainerManager.getForId(targetStudyId);
        }
        
        return null;
    }

    public abstract ExpData getDataForDataRow(Object dataRowId, ExpProtocol protocol);


    public ActionURL getImportURL(Container container, ExpProtocol protocol)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(container, protocol, UploadWizardAction.class);
    }

    public static ParticipantVisitResolverType findType(String name, List<ParticipantVisitResolverType> types)
    {
        if (name == null)
        {
            return null;
        }

        String decodedName = ParticipantVisitResolverType.Serializer.decodeBaseStringValue(name);
        if (decodedName == null)
        {
            return null;
        }
        for (ParticipantVisitResolverType type : types)
        {
            if (decodedName.equals(type.getName()))
            {
                return type;
            }
        }
        throw new IllegalArgumentException("Unexpected resolver type: " + name);
    }

    private Set<String> getPropertyDomains(ExpProtocol protocol)
    {
        Set<String> result = new HashSet<>();
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
        List<Pair<Domain, Map<DomainProperty, Object>>> domains = new ArrayList<>();
        for (String uri : getPropertyDomains(protocol))
        {
            Domain domain = PropertyService.get().getDomain(protocol.getContainer(), uri);
            if (domain != null)
            {
                Map<DomainProperty, Object> values = DefaultValueService.get().getDefaultValues(domain.getContainer(), domain);
                domains.add(new Pair<>(domain, values));
            }
        }
        sortDomainList(domains);
        return domains;
    }

    public Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer)
    {
        ExpProtocol copy = ExperimentService.get().createExpProtocol(targetContainer, ExpProtocol.ApplicationType.ExperimentRun, "Unknown");
        copy.setName(null);
        return new Pair<>(copy, createDefaultDomains(targetContainer, user));
    }

    protected void sortDomainList(List<Pair<Domain, Map<DomainProperty, Object>>> domains)
    {
        // Rely on the assay provider to return a list of default domains in the right order (Collections.sort() is
        // stable so that domains that haven't been inserted and have id 0 stay in the same order), and rely on the fact
        // that they get inserted in the same order, so they will have ascending ids.
        domains.sort(Comparator.comparingInt(pair -> pair.getKey().getTypeId()));
    }

    public Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> getAssayTemplate(User user, Container targetContainer, ExpProtocol toCopy)
    {
        ExpProtocol copy = ExperimentService.get().createExpProtocol(targetContainer, toCopy.getApplicationType(), toCopy.getName());
        copy.setDescription(toCopy.getDescription());
        Map<String, ObjectProperty> copiedProps = new HashMap<>();
        for (ObjectProperty prop : toCopy.getObjectProperties().values())
        {
            copiedProps.put(createPropertyURI(copy, prop.getName()), prop);
        }
        copy.setObjectProperties(copiedProps);

        List<Pair<Domain, Map<DomainProperty, Object>>> originalDomains = getDomains(toCopy);
        List<Pair<Domain, Map<DomainProperty, Object>>> copiedDomains = new ArrayList<>(originalDomains.size());
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : originalDomains)
        {
            Domain domain = domainInfo.getKey();
            Map<DomainProperty, Object> originalDefaults = domainInfo.getValue();
            Map<DomainProperty, Object> copiedDefaults = new HashMap<>();

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
                propCopy.copyFrom(propSrc, targetContainer);
            }
            copiedDomains.add(new Pair<>(domainCopy, copiedDefaults));
        }
        return new Pair<>(copy, copiedDomains);
    }

    public boolean isFileLinkPropertyAllowed(ExpProtocol protocol, Domain domain)
    {
        Lsid domainLsid = new Lsid(domain.getTypeURI());
        return domainLsid.getNamespacePrefix().equals(ExpProtocol.ASSAY_DOMAIN_BATCH) ||
                domainLsid.getNamespacePrefix().equals(ExpProtocol.ASSAY_DOMAIN_RUN) ||
                domainLsid.getNamespacePrefix().equals(ExpProtocol.ASSAY_DOMAIN_DATA);
    }

    // UNDONE: also look at result row for TargetStudy
    // CONSIDER: combine with .getAssociatedStudyContainer()
    public Container getTargetStudy(ExpRun run)
    {
        ExpProtocol protocol = run.getProtocol();
        Domain batchDomain = getBatchDomain(protocol);
        List<? extends DomainProperty> batchColumns = batchDomain.getProperties();
        Domain runDomain = getRunDomain(protocol);
        List<? extends DomainProperty> runColumns = runDomain.getProperties();

        List<DomainProperty> pds = new ArrayList<>();
        pds.addAll(runColumns);
        pds.addAll(batchColumns);

        Map<String, ObjectProperty> props = new HashMap<>(run.getObjectProperties());
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
        Map<String, Set<String>> domainMap = new HashMap<>();
        Set<String> batchProperties = domainMap.get(ExpProtocol.ASSAY_DOMAIN_BATCH);
        if (batchProperties == null)
        {
            batchProperties = new HashSet<>();
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

    public ModelAndView createResultsView(ViewContext context, ExpProtocol protocol, BindException errors)
    {
        return null;
    }

    public ModelAndView createResultDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object dataRowId)
    {
        AssayProtocolSchema schema = createProtocolSchema(context.getUser(), context.getContainer(), protocol, null);
        QuerySettings settings = schema.getSettings(context, AssayProtocolSchema.DATA_TABLE_NAME, AssayProtocolSchema.DATA_TABLE_NAME);
        QueryView queryView = schema.createDataQueryView(context, settings, null);

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
        List<Domain> domains = new ArrayList<>();
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : domainInfos)
            domains.add(domainInfo.getKey());

        Set<Container> defaultValueContainers = new HashSet<>();
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

            // Make sure we kill the pointer to the domain as well
            PropertyDescriptor prop = OntologyManager.getPropertyDescriptor(domain.getTypeURI(), domain.getContainer());
            if (prop != null)
            {
                OntologyManager.deletePropertyDescriptor(prop);
            }
        }

        // Take care of a few extra settings, such as whether runs and data rows are editable
        for (Map.Entry<String, ObjectProperty> entry : protocol.getObjectProperties().entrySet())
        {
            if (entry.getKey().startsWith(protocol.getLSID() + "#"))
            {
                PropertyDescriptor prop = OntologyManager.getPropertyDescriptor(entry.getKey(), protocol.getContainer());
                if (prop != null)
                {
                    OntologyManager.deletePropertyDescriptor(prop);
                }
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
     * Adds the materials as inputs to the run as a whole, plus as inputs for the "work" node for the run.
     * @param materialInputs Map of materials to roles.  If role is null, a generic role of "Sample N" will be used.
     */
    public static void addInputMaterials(ExpRun expRun, User user, Map<ExpMaterial, String> materialInputs)
    {
        for (ExpProtocolApplication protApp : expRun.getProtocolApplications())
        {
            if (!protApp.getApplicationType().equals(ExpProtocol.ApplicationType.ExperimentRunOutput))
            {
                Map<ExpMaterial, String> newInputs = new LinkedHashMap<>();
                newInputs.putAll(materialInputs);
                for (ExpMaterial material : protApp.getInputMaterials())
                    newInputs.remove(material);
                int index = 1;
                for (Map.Entry<ExpMaterial, String> entry : newInputs.entrySet())
                {
                    ExpMaterial newInput = entry.getKey();
                    String role = entry.getValue();
                    if (role == null)
                        role = "Sample" + (index == 1 ? "" : Integer.toString(index));
                    protApp.addMaterialInput(user, newInput, role);
                    index++;
                }
            }
        }
    }

    public boolean hasUsefulDetailsPage()
    {
        return true;
    }

    private static final String SCRIPT_PATH_DELIMITER = "|";

    @Override
    public void setValidationAndAnalysisScripts(ExpProtocol protocol, @NotNull List<File> scripts) throws ExperimentException
    {
        Map<String, ObjectProperty> props = new HashMap<>(protocol.getObjectProperties());
        String propertyURI = ScriptType.TRANSFORM.getPropertyURI(protocol);

        StringBuilder sb = new StringBuilder();
        String separator = "";
        for (File scriptFile : scripts)
        {
            if (scriptFile.isFile())
            {
                String ext = FileUtil.getExtension(scriptFile);
                ScriptEngine engine = ServiceRegistry.get().getService(ScriptEngineManager.class).getEngineByExtension(ext);
                if (engine != null)
                {
                    sb.append(separator);
                    sb.append(scriptFile.getAbsolutePath());
                    separator = SCRIPT_PATH_DELIMITER;
                }
                else
                    throw new ExperimentException("Script engine for the extension : " + ext + " has not been registered.\nFor documentation about how to configure a " +
                            "scripting engine, paste this link into your browser: \"https://www.labkey.org/wiki/home/Documentation/page.view?name=configureScripting\".");
            }
            else
                throw new ExperimentException("The transform script '" + scriptFile.getPath() + "' is invalid or does not exist");
        }
        if (sb.length() > 0)
        {
            ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(),
                    propertyURI, sb.toString());
            props.put(propertyURI, prop);
        }
        else
        {
            props.remove(propertyURI);
        }
        // Be sure to strip out any validation scripts that were stored with the legacy propertyURI. We merge and save
        // them as a single list in the TRANSFORM 
        props.remove(ScriptType.VALIDATION.getPropertyURI(protocol));
        protocol.setObjectProperties(props);
    }

    /** For migrating legacy assay designs that have separate transform and validation script properties */
    private enum ScriptType
    {
        VALIDATION("ValidationScript"),
        TRANSFORM("TransformScript");

        private final String _uriSuffix;

        ScriptType(String uriSuffix)
        {
            _uriSuffix = uriSuffix;
        }

        public String getPropertyURI(ExpProtocol protocol)
        {
            return protocol.getLSID() + "#" + _uriSuffix;
        }
    }

    @NotNull
    @Override
    public List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope)
    {
        List<File> result = new ArrayList<>();
        if (scope == Scope.ASSAY_DEF || scope == Scope.ALL)
        {
            ObjectProperty transformScripts = protocol.getObjectProperties().get(ScriptType.TRANSFORM.getPropertyURI(protocol));
            if (transformScripts != null)
            {
                for (String scriptPath : transformScripts.getStringValue().split("\\" + SCRIPT_PATH_DELIMITER))
                {
                    result.add(new File(scriptPath));
                }
            }
            ObjectProperty validationScripts = protocol.getObjectProperties().get(ScriptType.VALIDATION.getPropertyURI(protocol));
            if (validationScripts != null)
            {
                for (String scriptPath : validationScripts.getStringValue().split("\\" + SCRIPT_PATH_DELIMITER))
                {
                    result.add(new File(scriptPath));
                }
            }
        }
        return result;
    }

    @Override
    public void setSaveScriptFiles(ExpProtocol protocol, boolean save) throws ExperimentException
    {
        setBooleanProperty(protocol, SAVE_SCRIPT_FILES_PROPERTY_SUFFIX, save);
    }

    private void setBooleanProperty(ExpProtocol protocol, String propertySuffix, boolean value)
    {
        Map<String, ObjectProperty> props = new HashMap<>(protocol.getObjectProperties());

        String propertyURI = createPropertyURI(protocol, propertySuffix);
        ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(), propertyURI, value);
        props.put(propertyURI, prop);

        protocol.setObjectProperties(props);
    }

    @Override
    public void setEditableResults(ExpProtocol protocol, boolean editable) throws ExperimentException
    {
        setBooleanProperty(protocol, EDITABLE_RESULTS_PROPERTY_SUFFIX, editable);
    }

    @Override
    public boolean supportsEditableResults()
    {
        return false;
    }

    @Override
    public boolean isEditableResults(ExpProtocol protocol)
    {
        return supportsEditableResults() && Boolean.TRUE.equals(getBooleanProperty(protocol, EDITABLE_RESULTS_PROPERTY_SUFFIX));
    }

    @Override
    public void setEditableRuns(ExpProtocol protocol, boolean editable) throws ExperimentException
    {
        setBooleanProperty(protocol, EDITABLE_RUNS_PROPERTY_SUFFIX, editable);
    }

    @Override
    public boolean isEditableRuns(ExpProtocol protocol)
    {
        return Boolean.TRUE.equals(getBooleanProperty(protocol, EDITABLE_RUNS_PROPERTY_SUFFIX));
    }

    @Override
    public boolean supportsBackgroundUpload()
    {
        return false;
    }

    @Override
    public ReRunSupport getReRunSupport()
    {
        return ReRunSupport.None;
    }

    @Override
    public void setBackgroundUpload(ExpProtocol protocol, boolean background) throws ExperimentException
    {
        setBooleanProperty(protocol, BACKGROUND_UPLOAD_PROPERTY_SUFFIX, background);
    }

    @Override
    public boolean isBackgroundUpload(ExpProtocol protocol)
    {
        return supportsBackgroundUpload() && Boolean.TRUE.equals(getBooleanProperty(protocol, BACKGROUND_UPLOAD_PROPERTY_SUFFIX));
    }

    @Override
    public boolean isSaveScriptFiles(ExpProtocol protocol)
    {
        return Boolean.TRUE.equals(getBooleanProperty(protocol, SAVE_SCRIPT_FILES_PROPERTY_SUFFIX));
    }

    @Override @Nullable
    public AssaySaveHandler getSaveHandler()
    {
        return null;
    }

    @Override
    public AssayRunUploadContext.Factory<? extends AbstractAssayProvider, ? extends AssayRunUploadContext.Factory> createRunUploadFactory(ExpProtocol protocol, ViewContext context)
    {
        return new AssayRunUploadContextImpl.Factory<>(protocol, this, context);
    }

    @Override
    public AssayRunUploadContext.Factory<? extends AbstractAssayProvider, ? extends AssayRunUploadContext.Factory> createRunUploadFactory(ExpProtocol protocol, User user, Container c)
    {
        return new AssayRunUploadContextImpl.Factory<>(protocol, this, user, c);
    }

    protected Boolean getBooleanProperty(ExpProtocol protocol, String propertySuffix)
    {
        ObjectProperty prop = protocol.getObjectProperties().get(createPropertyURI(protocol, propertySuffix));

        if (prop != null)
        {
            Object o = prop.value();
            if (o instanceof Boolean)
                return (Boolean)o;
        }
        return null;
    }

    private static String createPropertyURI(ExpProtocol protocol, String propertySuffix)
    {
        return protocol.getLSID() + "#" + propertySuffix;
    }

    @Nullable public AssayDataType getDataType()
    {
        return _dataType;
    }

    @NotNull
    @Override
    public List<AssayDataType> getRelatedDataTypes()
    {
        return Collections.emptyList();
    }

    public void setMaxFileInputs(int maxFileInputs)
    {
        _maxFileInputs = maxFileInputs;
    }

    public int getMaxFileInputs()
    {
        return _maxFileInputs;
    }

    /**
     * Return the helper to handle data exchange between the server and external scripts.
     */
    public DataExchangeHandler createDataExchangeHandler()
    {
        return null;
    }

    @Override
    public AssayRunDatabaseContext createRunDatabaseContext(ExpRun run, User user, HttpServletRequest request)
    {
        return new AssayRunDatabaseContext(run, user, request);
    }

    @Override
    public AssayRunAsyncContext createRunAsyncContext(AssayRunUploadContext context) throws IOException, ExperimentException
    {
        return new AssayRunAsyncContext(context);
    }

    @Override
    public List<NavTree> getHeaderLinks(ViewContext viewContext, ExpProtocol protocol, ContainerFilter containerFilter)
    {
        List<NavTree> result = new ArrayList<>();

        NavTree manageMenu = getManageMenuNavTree(viewContext, protocol);
        if (manageMenu.getChildCount() > 0) result.add(manageMenu);

        result.add(new NavTree("view batches", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(viewContext.getContainer(), protocol, containerFilter), AssayProtocolSchema.getLastFilterScope(protocol))));
        result.add(new NavTree("view runs", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(viewContext.getContainer(), protocol, containerFilter), AssayProtocolSchema.getLastFilterScope(protocol))));
        result.add(new NavTree("view results", PageFlowUtil.addLastFilterParameter(PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(viewContext.getContainer(), protocol, containerFilter), AssayProtocolSchema.getLastFilterScope(protocol))));
        if (isBackgroundUpload(protocol))
        {
            ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getShowUploadJobsURL(viewContext.getContainer(), protocol, containerFilter);
            result.add(new NavTree("view upload jobs", PageFlowUtil.addLastFilterParameter(url, AssayProtocolSchema.getLastFilterScope(protocol))));
        }

        if (AuditLogService.get().isViewable())
            result.add(new NavTree("view copy-to-study history", AssayPublishService.get().getPublishHistory(viewContext.getContainer(), protocol, containerFilter)));

        return result;
    }

    private NavTree getManageMenuNavTree(ViewContext context, ExpProtocol protocol)
    {
        Container protocolContainer = protocol.getContainer();
        Container contextContainer = context.getContainer();

        NavTree manageMenu = new NavTree(MANAGE_ASSAY_DESIGN_LINK);

        if (allowUpdate(context, protocol))
        {
            ActionURL editURL = PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(protocolContainer, protocol, false, context.getActionURL());
            if (editURL != null)
            {
                String editLink = editURL.toString();
                if (!protocolContainer.equals(contextContainer))
                {
                    editLink = "javascript: if (window.confirm('This assay is defined in the " + protocolContainer.getPath() + " folder. Would you still like to edit it?')) { window.location = '" + editLink + "' }";
                }
                manageMenu.addChild("Edit assay design", editLink);
            }

            ActionURL copyURL = PageFlowUtil.urlProvider(AssayUrls.class).getChooseCopyDestinationURL(protocol, protocolContainer);
            if (copyURL != null)
                manageMenu.addChild("Copy assay design", copyURL.toString());
        }

        if (allowDelete(context, protocol))
        {
            manageMenu.addChild("Delete assay design", PageFlowUtil.urlProvider(ExperimentUrls.class).getDeleteProtocolURL(protocol, PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(contextContainer)));
        }

        ActionURL exportURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getExportProtocolURL(protocolContainer, protocol);
        manageMenu.addChild("Export assay design", exportURL.toString());

        if (contextContainer.hasPermission(context.getUser(), AdminPermission.class))
        {
            List<Pair<Domain, Map<DomainProperty, Object>>> domainInfos = getDomains(protocol);
            if (!domainInfos.isEmpty())
            {
                NavTree setDefaultsTree = new NavTree(SET_DEFAULT_VALUES_LINK);
                ActionURL baseEditUrl = new ActionURL(SetDefaultValuesAssayAction.class, contextContainer);
                baseEditUrl.addParameter(ActionURL.Param.returnUrl, context.getActionURL().getLocalURIString());
                baseEditUrl.addParameter("providerName", getName());
                for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : domainInfos)
                {
                    Domain domain = domainInfo.getKey();
                    if (allowDefaultValues(domain) && !domain.getProperties().isEmpty())
                    {
                        ActionURL currentEditUrl = baseEditUrl.clone();
                        currentEditUrl.addParameter("domainId", domain.getTypeId());
                        setDefaultsTree.addChild(domain.getName(), currentEditUrl);
                    }
                }
                if (setDefaultsTree.hasChildren())
                {
                    manageMenu.addChild(setDefaultsTree);
                }
            }
        }

        // add registered AssayHeaderLinkProvider's manage assay links
        for (AssayHeaderLinkProvider headerLinkProvider : AssayService.get().getAssayHeaderLinkProviders())
        {
            manageMenu.addChildren(headerLinkProvider.getManageAssayDesignLinks(protocol, context.getContainer(), context.getUser()));
        }

        return manageMenu;
    }

    protected boolean allowUpdate(ViewContext viewContext, ExpProtocol protocol)
    {
        Container container = protocol.getContainer();
        return container.hasPermission(viewContext.getUser(), DesignAssayPermission.class);
    }

    protected boolean allowDelete(ViewContext viewContext, ExpProtocol protocol)
    {
        Container container = protocol.getContainer();
        //deleting will delete data as well as design, so user must have both design assay and delete perms
        return container.getPolicy().hasPermissions(viewContext.getUser(), DesignAssayPermission.class, DeletePermission.class);
    }

    public String getRunLSIDPrefix()
    {
        return _runLSIDPrefix;
    }

    @Override
    public boolean supportsFlagColumnType(ExpProtocol.AssayDomainTypes type)
    {
        return false;
    }

    public Module getDeclaringModule()
    {
        return _declaringModule;
    }

    @NotNull
    public Set<Module> getRequiredModules()
    {
        return Collections.unmodifiableSet(_requiredModules);
    }
}
