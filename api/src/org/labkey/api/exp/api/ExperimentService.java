/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidType;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.exp.query.ExpDataClassTable;
import org.labkey.api.exp.query.ExpDataInputTable;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpProtocolApplicationTable;
import org.labkey.api.exp.query.ExpProtocolTable;
import org.labkey.api.exp.query.ExpQCFlagTable;
import org.labkey.api.exp.query.ExpRunGroupMapTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSampleSetTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

public interface ExperimentService extends ExperimentRunTypeSource
{
    String MODULE_NAME = "Experiment";
    String SCHEMA_LOCATION = "http://cpas.fhcrc.org/exp/xml http://www.labkey.org/download/XarSchema/V2.3/expTypes.xsd";

    String SAMPLE_DERIVATION_PROTOCOL_LSID = "urn:lsid:labkey.org:Protocol:SampleDerivationProtocol";

    int SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE = 1;
    int SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE = 10;
    int SIMPLE_PROTOCOL_EXTRA_STEP_SEQUENCE = 15;
    int SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE = 20;

    String EXPERIMENTAL_LINEAGE_PERFORMANCE = "very-new-hotness";

    static ExperimentService get()
    {
        return ServiceRegistry.get(ExperimentService.class);
    }

    static void setInstance(ExperimentService impl)
    {
        ServiceRegistry.get().registerService(ExperimentService.class, impl);
    }

    @Nullable
    ExpObject findObjectFromLSID(String lsid);

    ExpRun getExpRun(int rowid);
    ExpRun getExpRun(String lsid);
    List<? extends ExpRun> getExpRuns(Container container, @Nullable ExpProtocol parentProtocol, @Nullable ExpProtocol childProtocol);
    List<? extends ExpRun> getExpRunsForJobId(int jobId);
    List<? extends ExpRun> getExpRunsForFilePathRoot(File filePathRoot);
    ExpRun createExperimentRun(Container container, String name);
    void syncRunEdges(ExpRun run);

    ExpData getExpData(int rowid);
    ExpData getExpData(String lsid);
    List<? extends ExpData> getExpDatas(int... rowid);
    List<? extends ExpData> getExpDatasByLSID(Collection<String> lsids);
    List<? extends ExpData> getExpDatas(Collection<Integer> rowid);
    List<? extends ExpData> getExpDatas(Container container, @Nullable DataType type, @Nullable String name);
    @NotNull
    List<? extends ExpData> getExpDatasUnderPath(@NotNull File path, @Nullable Container c);

    /** Get all ExpData that are members of the ExpDataClass. */
    List<? extends ExpData> getExpDatas(ExpDataClass dataClass);
    ExpData getExpData(ExpDataClass dataClass, String name);
    ExpData getExpData(ExpDataClass dataClass, int rowId);

    /**
     * Create a data object.  The object will be unsaved, and will have a name which is a GUID.
     */
    ExpData createData(Container container, @NotNull DataType type);
    ExpData createData(Container container, @NotNull DataType type, @NotNull String name);
    ExpData createData(Container container, @NotNull DataType type, @NotNull String name, boolean generated);
    ExpData createData(Container container, String name, String lsid);
    ExpData createData(URI uri, XarSource source) throws XarFormatException;

    /**
     * Create a new DataClass with the provided properties.
     */
    ExpDataClass createDataClass(Container c, User u, String name, String description,
             List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, Integer sampleSetId, String nameExpression,
             @Nullable TemplateInfo templateInfo)
        throws ExperimentException, SQLException;

    List<? extends ExpDataClass> getDataClasses(Container container, User user, boolean includeOtherContainers);

    /** Get a DataClass by name within the definition container. */
    ExpDataClass getDataClass(Container definitionContainer, String dataClassName);

    /**
     * Get a DataClass by name within scope -- current, project, and shared.
     * Requires a user to check for container read permission.
     */
    ExpDataClass getDataClass(Container scope, User user, String dataClassName);

    ExpDataClass getDataClass(int rowId);
    ExpDataClass getDataClass(String lsid);

    /**
     * Get materials with the given names, optionally within the provided sample set.
     * If the materials don't exist, throw an exception if <code>throwIfMissing</code> is true
     * or create new materials if <code>createIfMissing</code> is true, otherwise missing samples
     * will be ignored.
     *
     * @param container Samples will be found within this container, project, or shared container.
     * @param user Samples will only be resolved within containers that the user has ReadPermission.
     * @param sampleNames The set of samples to be resolved by name.
     * @param sampleSet Optional sample set that the samples must live in.
     * @param throwIfMissing Throw ExperimentException if any of the sampleNames do not exist.
     * @param createIfMissing Create missing samples in the given <code>sampleSet</code> or the active sample set.
     * @return Resolved samples
     * @throws ExperimentException
     */
    @NotNull List<? extends ExpMaterial> getExpMaterials(Container container, @Nullable User user, Set<String> sampleNames, @Nullable ExpSampleSet sampleSet, boolean throwIfMissing, boolean createIfMissing) throws ExperimentException;

    /* This version of createExpMaterial() takes name from lsid.getObjectId() */
    ExpMaterial createExpMaterial(Container container, Lsid lsid);
    ExpMaterial createExpMaterial(Container container, String lsid, String name);
    ExpMaterial getExpMaterial(int rowid);
    @NotNull List<? extends ExpMaterial> getExpMaterials(Collection<Integer> rowids);
    ExpMaterial getExpMaterial(String lsid);

    /**
     * Looks in all the sample sets visible from the given container for a single match with the specified name
     */
    @NotNull List<? extends ExpMaterial> getExpMaterialsByName(String name, Container container, User user);

    Map<String, ExpSampleSet> getSampleSetsForRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType type);

    /**
     * Create a new SampleSet with the provided properties.
     * If a 'Name' property exists in the list, it will be used as the 'id' property of the SampleSet.
     * Either a 'Name' property must exist or at least one idCol index must be provided.
     * A name expression may be provided instead of idCols and will be used to generate the sample names.
     */
    @NotNull
    ExpSampleSet createSampleSet(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol, String nameExpression)
        throws ExperimentException, SQLException;

    /** (MAB) todo need a builder interface, or at least  parameter bean */
    @NotNull
    ExpSampleSet createSampleSet(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                 String nameExpression, @Nullable TemplateInfo templateInfo)
            throws ExperimentException, SQLException;

    @NotNull
    ExpSampleSet createSampleSet();
    @Nullable
    ExpSampleSet getSampleSet(int rowId);
    @Nullable
    ExpSampleSet getSampleSet(String lsid);

    /**
     * @param includeOtherContainers whether sample sets from the shared container or the container's project should be included
     */
    List<? extends ExpSampleSet> getSampleSets(Container container, User user, boolean includeOtherContainers);
    ExpSampleSet getSampleSet(Container container, String name);

    /**
     * @param includeOtherContainers If true, try getting sample set from project and shared containers.
     */
    ExpSampleSet getSampleSet(Container container, String name, boolean includeOtherContainers);
    ExpSampleSet lookupActiveSampleSet(Container container);
    void setActiveSampleSet(Container container, ExpSampleSet sampleSet);

    ExpExperiment createHiddenRunGroup(Container container, User user, ExpRun... runs);

    ExpExperiment createExpExperiment(Container container, String name);
    ExpExperiment getExpExperiment(int rowid);
    ExpExperiment getExpExperiment(String lsid);
    List<? extends ExpExperiment> getExperiments(Container container, User user, boolean includeOtherContainers, boolean includeBatches);

    ExpProtocol getExpProtocol(int rowid);
    ExpProtocol getExpProtocol(String lsid);
    ExpProtocol getExpProtocol(Container container, String name);
    ExpProtocol createExpProtocol(Container container, ExpProtocol.ApplicationType type, String name);
    ExpProtocol createExpProtocol(Container container, ExpProtocol.ApplicationType type, String name, String lsid);

    /**
     * @param type may be null. If non-null, only return roles that are used for that type of application (input, output, or intermediate)
     */
    Set<String> getDataInputRoles(Container container, ContainerFilter containerFilter, ExpProtocol.ApplicationType... type);
    /**
     * @param type may be null. If non-null, only return roles that are used for that type of application (input, output, or intermediate)
     */
    Set<String> getMaterialInputRoles(Container container, ExpProtocol.ApplicationType... type);

    /**
     * Get the DataInput edge between the dataId and the protocolApplication.
     */
    @Nullable ExpDataRunInput getDataInput(int dataId, int targetProtocolApplicationId);
    @Nullable ExpDataRunInput getDataInput(Lsid lsid);

    /**
     * Get the MaterialInput edge between the materialId and the protocolApplication.
     */
    @Nullable ExpMaterialRunInput getMaterialInput(int materialId, int targetProtocolApplicationId);
    @Nullable ExpMaterialRunInput getMaterialInput(Lsid lsid);

    Pair<Set<ExpData>, Set<ExpMaterial>> getParents(ExpProtocolOutput start);
    Pair<Set<ExpData>, Set<ExpMaterial>> getChildren(ExpProtocolOutput start);

    /**
     * Find all child and grandchild samples Samples that are direct descendants of <code>start</code> ExpData,
     * ignoring any sample children derived from ExpData children.
     */
    Set<ExpMaterial> getRelatedChildSamples(ExpData start);

    /**
     * Find all parent ExpData that are parents of the <code>start</code> ExpMaterial,
     * stopping at the first parent generation (no grandparents.)
     */
    Set<ExpData> getNearestParentDatas(ExpMaterial start);

    ExpLineage getLineage(ExpProtocolOutput start, ExpLineageOptions options);

    /**
     * The following methods return TableInfo's suitable for using in queries.
     * These TableInfo's initially have no columns, but have methods to
     * add particular columns as needed by the client.
     */
    ExpRunTable createRunTable(String name, UserSchema schema);
    /** Create a RunGroupMap junction table joining Runs and RunGroups. */
    ExpRunGroupMapTable createRunGroupMapTable(String name, UserSchema schema);
    ExpDataTable createDataTable(String name, UserSchema schema);
    ExpDataInputTable createDataInputTable(String name, ExpSchema expSchema);
    ExpSampleSetTable createSampleSetTable(String name, UserSchema schema);
    ExpDataClassTable createDataClassTable(String name, UserSchema schema);
    ExpDataClassDataTable createDataClassDataTable(String name, UserSchema schema, @NotNull ExpDataClass dataClass);
    ExpProtocolTable createProtocolTable(String name, UserSchema schema);
    ExpExperimentTable createExperimentTable(String name, UserSchema schema);
    ExpMaterialTable createMaterialTable(String name, UserSchema schema);
    ExpMaterialInputTable createMaterialInputTable(String name, ExpSchema expSchema);
    ExpProtocolApplicationTable createProtocolApplicationTable(String name, UserSchema schema);
    ExpQCFlagTable createQCFlagsTable(String name, UserSchema schema);
    ExpDataTable createFilesTable(String name, UserSchema schema);

    String generateLSID(Container container, Class<? extends ExpObject> clazz, String name);
    String generateGuidLSID(Container container, Class<? extends ExpObject> clazz);
    String generateLSID(@NotNull Container container, @NotNull DataType type, @NotNull String name);
    String generateGuidLSID(Container container, DataType type);

    DataType getDataType(String namespacePrefix);

    DbScope.Transaction ensureTransaction();

    ExperimentRunListView createExperimentRunWebPart(ViewContext context, ExperimentRunType type);

    DbSchema getSchema();

    ExpProtocolApplication getExpProtocolApplication(String lsid);
    List<? extends ExpProtocolApplication> getExpProtocolApplicationsForProtocolLSID(String protocolLSID);

    List<? extends ExpData> getExpData(Container c);
    ExpData getExpDataByURL(String canonicalURL, @Nullable Container container);
    ExpData getExpDataByURL(File f, @Nullable Container c);
    ExpData getExpDataByURL(Path p, @Nullable Container c);
    List<? extends ExpData> getAllExpDataByURL(String canonicalURL);

    TableInfo getTinfoMaterial();
    TableInfo getTinfoMaterialSource();
    TableInfo getTinfoProtocol();
    TableInfo getTinfoProtocolApplication();
    TableInfo getTinfoExperiment();
    TableInfo getTinfoExperimentRun();
    TableInfo getTinfoRunList();
    TableInfo getTinfoData();
    TableInfo getTinfoDataClass();
    TableInfo getTinfoMaterialInput();
    TableInfo getTinfoDataInput();
    TableInfo getTinfoPropertyDescriptor();
    TableInfo getTinfoAssayQCFlag();
    TableInfo getTinfoAlias();
    TableInfo getTinfoDataAliasMap();
    TableInfo getTinfoMaterialAliasMap();
    ExpSampleSet ensureDefaultSampleSet();
    ExpSampleSet ensureActiveSampleSet(Container container);
    String getDefaultSampleSetLsid();

    List<? extends ExpRun> getRunsUsingMaterials(List<ExpMaterial> materials);
    List<? extends ExpRun> getRunsUsingMaterials(int... materialIds);
    List<? extends ExpRun> getRunsUsingDatas(List<ExpData> datas);

    ExpRun getCreatingRun(File file, Container c);
    List<? extends ExpRun> getExpRunsForProtocolIds(boolean includeRelated, int... rowIds);
    List<? extends ExpRun> getExpRunsForProtocolIds(boolean includeRelated, @NotNull Collection<Integer> rowIds);
    List<? extends ExpRun> getRunsUsingSampleSets(ExpSampleSet... sampleSets);
    List<? extends ExpRun> getRunsUsingDataClasses(Collection<ExpDataClass> dataClasses);

    /**
     * @return the subset of these runs which are supposed to be deleted when one of their inputs is deleted.
     */
    List<? extends ExpRun> runsDeletedWithInput(List<? extends ExpRun> runs);

    void deleteAllExpObjInContainer(Container container, User user) throws ExperimentException;

    Lsid getSampleSetLsid(String name, Container container);

    void deleteExperimentRunsByRowIds(Container container, final User user, int... selectedRunIds);
    void deleteExperimentRunsByRowIds(Container container, final User user, @NotNull Collection<Integer> selectedRunIds);

    void deleteExpExperimentByRowId(Container container, User user, int experimentId);

    /**
     * Increment and get the sample counters for the given date, or the current date if no date is supplied.
     * The resulting map has keys "dailySampleCount", "weeklySampleCount", "monthlySampleCount", and "yearlySampleCount".
     */
    Map<String, Integer> incrementSampleCounts(@Nullable Date counterDate);

    void addExperimentListener(ExperimentListener listener);

    void clearCaches();

    List<ProtocolApplicationParameter> getProtocolApplicationParameters(int rowId);

    void moveContainer(Container c, Container cOldParent, Container cNewParent) throws ExperimentException;

    LsidType findType(Lsid lsid);

    Identifiable getObject(Lsid lsid);

    List<? extends ExpData> deleteExperimentRunForMove(int runId, User user);

    /** Kicks off an asynchronous move - a PipelineJob is submitted to the queue to perform the move */
    void moveRuns(ViewBackgroundInfo targetInfo, Container sourceContainer, List<ExpRun> runs) throws IOException;

    /**
     * Insert a protocol with optional steps and predecessor configurations.
     * @param baseProtocol the base/top-level protocol to create. Expected to have an ApplicationType and a
     *                     ProtocolParameter value for XarConstants.APPLICATION_LSID_TEMPLATE_URI.
     * @param steps the protocol steps. Expected to have an ApplicationType and a ProtocolParameter value
     *              for XarConstants.APPLICATION_LSID_TEMPLATE_URI.
     * @param predecessors Map of Protocol LSIDs to a List of Protocol LSIDs where each entry represents a
     *                     node in the Experiment Protocol graph. If this is not provided the baseProtocol and
     *                     subsequent steps will be organized sequentially.
     * @param user user with insert permissions
     * @return the saved ExpProtocol
     * @throws ExperimentException
     */
    ExpProtocol insertProtocol(@NotNull ExpProtocol baseProtocol, @Nullable List<ExpProtocol> steps, @Nullable Map<String, List<String>> predecessors, User user) throws ExperimentException;

    ExpProtocol insertSimpleProtocol(ExpProtocol baseProtocol, User user) throws ExperimentException;

    /**
     * The run must be a instance of a protocol created with insertSimpleProtocol().
     * The run must have at least one input and one output.
     * @param run ExperimentRun, populated with protocol, name, etc
     * @param inputMaterials map from input role name to input material
     * @param inputDatas map from input role name to input data
     * @param outputMaterials map from output role name to output material
     * @param outputDatas map from output role name to output data
     * @param info context information, including the user
     * @param log output log target
     */
    ExpRun saveSimpleExperimentRun(ExpRun run, Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas, Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas, Map<ExpData, String> transformedDatas, ViewBackgroundInfo info, Logger log, boolean loadDataFiles) throws ExperimentException;

    /**
     * Adds an extra protocol application to a run created by saveSimpleExperimentRun() to track more complex
     * workflows.
     * @param expRun run to which the extra should be added
     * @param name name of the protocol application
     * @return a fully populated but not yet saved ExpProtocolApplication. It will have no inputs and outputs.
     */
    ExpProtocolApplication createSimpleRunExtraProtocolApplication(ExpRun expRun, String name);
    ExpRun deriveSamples(Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials, ViewBackgroundInfo info, Logger log) throws ExperimentException;
    ExpRun derive(Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas,
                  Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas,
                  ViewBackgroundInfo info, Logger log)
        throws ExperimentException;
    void deriveSamplesBulk(List<? extends SimpleRunRecord> runRecords, ViewBackgroundInfo info, Logger log) throws ExperimentException;

    void registerExperimentDataHandler(ExperimentDataHandler handler);
    void registerExperimentRunTypeSource(ExperimentRunTypeSource source);
    void registerDataType(DataType type);
    void registerProtocolImplementation(ProtocolImplementation impl);

    ProtocolImplementation getProtocolImplementation(String name);

    ExpProtocolApplication getExpProtocolApplication(int rowId);
    List<? extends ExpProtocolApplication> getExpProtocolApplicationsForRun(int runId);

    List<? extends ExpProtocol> getExpProtocols(Container... containers);
    List<? extends ExpProtocol> getAllExpProtocols();
    List<? extends ExpProtocol> getExpProtocolsWithParameterValue(@NotNull String parameterURI, @NotNull String parameterValue, @Nullable Container c);

    /**
     * Kicks off a pipeline job to asynchronously load the XAR from disk
     * @return the job responsible for doing the work
     */
    PipelineJob importXarAsync(ViewBackgroundInfo info, File file, String description, PipeRoot root) throws IOException;

    /**
     * Loads the xar synchronously, in the context of the pipelineJob
     * @return the runs loaded from the XAR
     */
    List<ExpRun> importXar(XarSource source, PipelineJob pipelineJob, boolean reloadExistingRuns) throws ExperimentException;

    /**
     * Create an experiment run to represent the work that the task's job has done so far.
     * The job's recorded actions will be marked as completed after creating the ExpRun so subsequent
     * runs created by the job won't duplicate the previous actions.
     * @param job Pipeline job.
     * @return the run created from the job's actions.
     */
    ExpRun importRun(PipelineJob job, XarSource source) throws SQLException, PipelineJobException, ValidationException;

    /**
     * Provides access to an object that should be locked before inserting protocols. Locking when doing
     * experiment run insertion has turned out to be problematic and deadlock prone. It's more pragmatic to have
     * the occasional import fail with a SQLException due to duplicate insertions compared with deadlocking the
     * whole server.
     *
     * @return lock object on which to synchronize
     */
    Lock getProtocolImportLock();

    HttpView createRunExportView(Container container, String defaultFilenamePrefix);
    HttpView createFileExportView(Container container, String defaultFilenamePrefix);

    void auditRunEvent(User user, ExpProtocol protocol, ExpRun run, @Nullable ExpExperiment runGroup, String message);

    List<? extends ExpExperiment> getMatchingBatches(String name, Container container, ExpProtocol protocol);

    List<? extends ExpProtocol> getExpProtocolsUsedByRuns(Container c, ContainerFilter containerFilter);

    @Nullable
    ExperimentRunType getExperimentRunType(@NotNull String description, @Nullable Container container);

    GWTPropertyValidator convertJsonToPropertyValidator(JSONObject obj) throws JSONException;
    GWTPropertyDescriptor convertJsonToPropertyDescriptor(JSONObject obj) throws JSONException;
    GWTDomain convertJsonToDomain(JSONObject obj) throws JSONException;

    JSONObject convertPropertyDescriptorToJson(GWTPropertyDescriptor pd);
    JSONArray convertPropertyValidatorsToJson(GWTPropertyDescriptor pd);

    List<ValidationException> onBeforeRunCreated(ExpProtocol protocol, ExpRun run, Container container, User user);
    List<ValidationException> onRunDataCreated(ExpProtocol protocol, ExpRun run, Container container, User user);
    void onMaterialsCreated(List<? extends ExpMaterial> materials, Container container, User user);
}
