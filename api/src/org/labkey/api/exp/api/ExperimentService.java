/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.SQLFragment;
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
import org.labkey.api.exp.query.ExpDataProtocolInputTable;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpExperimentTable;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.exp.query.ExpMaterialProtocolInputTable;
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
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

public interface ExperimentService extends ExperimentRunTypeSource
{
    String MODULE_NAME = "Experiment";
    String SCHEMA_LOCATION = "http://cpas.fhcrc.org/exp/xml http://www.labkey.org/download/XarSchema/V2.3/expTypes.xsd";

    String SAMPLE_DERIVATION_PROTOCOL_LSID = "urn:lsid:labkey.org:Protocol:SampleDerivationProtocol";
    String SAMPLE_DERIVATION_PROTOCOL_NAME = "Sample Derivation Protocol";

    int SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE = 1;
    int SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE = 10;
    int SIMPLE_PROTOCOL_EXTRA_STEP_SEQUENCE = 15;
    int SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE = 20;

    static ExperimentService get()
    {
        return ServiceRegistry.get().getService(ExperimentService.class);
    }

    static void setInstance(ExperimentService impl)
    {
        ServiceRegistry.get().registerService(ExperimentService.class, impl);
    }

    @Nullable
    ExpObject findObjectFromLSID(String lsid);

    ExpRun getExpRun(int rowid);

    List<? extends ExpRun> getExpRuns(Collection<Integer> rowIds);

    ExpRun getExpRun(String lsid);

    List<? extends ExpRun> getExpRuns(Container container, @Nullable ExpProtocol parentProtocol, @Nullable ExpProtocol childProtocol);

    List<? extends ExpRun> getExpRunsForJobId(int jobId);

    List<? extends ExpRun> getExpRunsForFilePathRoot(File filePathRoot);

    ExpRun createExperimentRun(Container container, String name);

    void queueSyncRunEdges(int runId);

    void queueSyncRunEdges(ExpRun run);

    void syncRunEdges(int runId);

    void syncRunEdges(ExpRun run);

    void syncRunEdges(Collection<ExpRun> runs);

    ExpData getExpData(int rowid);

    ExpData getExpData(String lsid);

    @NotNull
    List<? extends ExpData> getExpDatas(int... rowid);

    @NotNull
    List<? extends ExpData> getExpDatasByLSID(Collection<String> lsids);

    @NotNull
    List<? extends ExpData> getExpDatas(Collection<Integer> rowid);

    List<? extends ExpData> getExpDatas(Container container, @Nullable DataType type, @Nullable String name);

    @NotNull
    List<? extends ExpData> getExpDatasUnderPath(@NotNull File path, @Nullable Container c);

    /**
     * Get all ExpData that are members of the ExpDataClass.
     */
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
     * Create a new DataClass with the provided domain properties and top level options.
     */
    @Deprecated
    ExpDataClass createDataClass(@NotNull Container c, @NotNull User u, @NotNull String name, String description,
                                 List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, Integer sampleSetId, String nameExpression,
                                 @Nullable TemplateInfo templateInfo, @Nullable String category)
            throws ExperimentException;

    /**
     * Create a new DataClass with the provided domain properties and top level options.
     */
    ExpDataClass createDataClass(@NotNull Container c, @NotNull User u, @NotNull String name, @Nullable DataClassDomainKindProperties options,
                                 List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, @Nullable TemplateInfo templateInfo)
            throws ExperimentException;

    /**
     * Update a DataClass with the provided domain properties and top level options.
     */
    ValidationException updateDataClass(@NotNull Container c, @NotNull User u, @NotNull ExpDataClass dataClass,
                                        @Nullable DataClassDomainKindProperties options,
                                        GWTDomain<? extends GWTPropertyDescriptor> original,
                                        GWTDomain<? extends GWTPropertyDescriptor> update);

    /**
     * Get all DataClass definitions in the container.  If <code>includeOtherContainers</code> is true,
     * a user must be provided to check for read permission of the containers in scope.
     */
    List<? extends ExpDataClass> getDataClasses(@NotNull Container container, @Nullable User user, boolean includeOtherContainers);

    /**
     * Get a DataClass by name within the definition container.
     */
    ExpDataClass getDataClass(@NotNull Container definitionContainer, @NotNull String dataClassName);

    /**
     * Get a DataClass by name within scope -- current, project, and shared.
     * Requires a user to check for container read permission.
     */
    ExpDataClass getDataClass(@NotNull Container scope, @NotNull User user, @NotNull String dataClassName);

    /**
     * Get a DataClass by rowId within the definition container.
     */
    ExpDataClass getDataClass(@NotNull Container definitionContainer, int rowId);

    /**
     * Get a DataClass by rowId within scope -- current, project, and shared.
     * Requires a user to check for container read permission.
     */
    ExpDataClass getDataClass(@NotNull Container scope, @NotNull User user, int rowId);

    /**
     * Get a DataClass by LSID.
     * NOTE: Prefer using one of the getDataClass methods that accept a Container and User for permission checking.
     */
    ExpDataClass getDataClass(@NotNull String lsid);

    /**
     * Get a DataClass by RowId
     * NOTE: Prefer using one of the getDataClass methods that accept a Container and User for permission checking.
     */
    ExpDataClass getDataClass(int rowId);

    /**
     * Get materials with the given names, optionally within the provided sample set.
     * If the materials don't exist, throw an exception if <code>throwIfMissing</code> is true
     * or create new materials if <code>createIfMissing</code> is true, otherwise missing samples
     * will be ignored.
     *
     * @param container       Samples will be found within this container, project, or shared container.
     * @param user            Samples will only be resolved within containers that the user has ReadPermission.
     * @param sampleNames     The set of samples to be resolved by name.
     * @param sampleSet       Optional sample set that the samples must live in.
     * @param throwIfMissing  Throw ExperimentException if any of the sampleNames do not exist.
     * @param createIfMissing Create missing samples in the given <code>sampleSet</code>.
     * @return Resolved samples
     * @throws ExperimentException
     */
    @NotNull List<? extends ExpMaterial> getExpMaterials(Container container, @Nullable User user, Set<String> sampleNames, @Nullable ExpSampleSet sampleSet, boolean throwIfMissing, boolean createIfMissing) throws ExperimentException;

    /* This version of createExpMaterial() takes name from lsid.getObjectId() */
    ExpMaterial createExpMaterial(Container container, Lsid lsid);

    ExpMaterial createExpMaterial(Container container, String lsid, String name);

    @Nullable
    ExpMaterial getExpMaterial(int rowid);

    @NotNull List<? extends ExpMaterial> getExpMaterials(Collection<Integer> rowids);

    ExpMaterial getExpMaterial(String lsid);

    /**
     * Looks in all the sample sets visible from the given container for a single match with the specified name
     */
    @NotNull List<? extends ExpMaterial> getExpMaterialsByName(String name, Container container, User user);

    @Nullable ExpData findExpData(Container c, User user,
                                  @NotNull String dataClassName, String dataName,
                                  RemapCache cache, Map<Integer, ExpData> dataCache)
            throws ValidationException;

    @Nullable ExpMaterial findExpMaterial(Container c, User user,
                                          String sampleSetName, String sampleName,
                                          RemapCache cache, Map<Integer, ExpMaterial> materialCache)
            throws ValidationException;

    /**
     * Use {@link SampleSetService} instead.
     */
    @Deprecated
    default Map<String, ExpSampleSet> getSampleSetsForRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType type)
    {
        return SampleSetService.get().getSampleSetsForRoles(container, filter, type);
    }

    /**
     * Create a new SampleSet with the provided properties.
     * If a 'Name' property exists in the list, it will be used as the 'id' property of the SampleSet.
     * Either a 'Name' property must exist or at least one idCol index must be provided.
     * A name expression may be provided instead of idCols and will be used to generate the sample names.
     */
    @NotNull
    @Deprecated
    default ExpSampleSet createSampleSet(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol, String nameExpression)
            throws ExperimentException, SQLException
    {
        return SampleSetService.get().createSampleSet(container, user, name, description, properties, indices, idCol1, idCol2, idCol3, parentCol, nameExpression);
    }

    /**
     * Use {@link SampleSetService} instead.
     * (MAB) todo need a builder interface, or at least  parameter bean
     */
    @NotNull
    @Deprecated
    default ExpSampleSet createSampleSet(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                         String nameExpression, @Nullable TemplateInfo templateInfo)
            throws ExperimentException, SQLException
    {
        return SampleSetService.get().createSampleSet(container, user, name, description, properties, indices, idCol1, idCol2, idCol3, parentCol, nameExpression, templateInfo);
    }

    /**
     * Use {@link SampleSetService} instead.
     * (MAB) todo need a builder interface, or at least  parameter bean
     */
    @NotNull
    @Deprecated
    default ExpSampleSet createSampleSet(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, List<GWTIndex> indices, int idCol1, int idCol2, int idCol3, int parentCol,
                                         String nameExpression, @Nullable TemplateInfo templateInfo, Map<String, String> importAliases)
            throws ExperimentException, SQLException
    {
        return SampleSetService.get().createSampleSet(container, user, name, description, properties, indices, idCol1, idCol2, idCol3, parentCol, nameExpression, templateInfo, importAliases);
    }

    @NotNull
    @Deprecated
    default ExpSampleSet createSampleSet()
    {
        return SampleSetService.get().createSampleSet();
    }

    /**
     * Use {@link SampleSetService} instead.
     */
    @Nullable
    @Deprecated
    default ExpSampleSet getSampleSet(int rowId)
    {
        return SampleSetService.get().getSampleSet(rowId);
    }

    /**
     * Use {@link SampleSetService} instead.
     */
    @Nullable
    @Deprecated
    default ExpSampleSet getSampleSet(String lsid)
    {
        return SampleSetService.get().getSampleSet(lsid);
    }

    /**
     * Use {@link SampleSetService} instead.
     * @param includeOtherContainers whether sample sets from the shared container or the container's project should be included
     */
    @Deprecated
    default List<? extends ExpSampleSet> getSampleSets(@NotNull Container container, User user, boolean includeOtherContainers)
    {
        return SampleSetService.get().getSampleSets(container, user, includeOtherContainers);
    }

    /**
     * Use {@link SampleSetService} instead.
     * Get a SampleSet by name within the definition container.
     */
    @Deprecated
    default ExpSampleSet getSampleSet(@NotNull Container definitionContainer, @NotNull String sampleSetName)
    {
        return SampleSetService.get().getSampleSet(definitionContainer, sampleSetName);
    }

    /**
     * Use {@link SampleSetService} instead.
     * Get a SampleSet by name within scope -- current, project, and shared.
     * Requires a user to check for container read permission.
     */
    @Deprecated
    default ExpSampleSet getSampleSet(@NotNull Container scope, @NotNull User user, @NotNull String sampleSetName)
    {
        return SampleSetService.get().getSampleSet(scope, user, sampleSetName);
    }

    /**
     * Get a SampleSet by rowId within the definition container.
     */
    @Deprecated
    default ExpSampleSet getSampleSet(@NotNull Container definitionContainer, int rowId)
    {
        return SampleSetService.get().getSampleSet(definitionContainer, rowId);
    }

    /**
     * Get a SampleSet by rowId within scope -- current, project, and shared.
     * Requires a user to check for container read permission.
     */
    @Deprecated
    default ExpSampleSet getSampleSet(@NotNull Container scope, @NotNull User user, int rowId)
    {
        return SampleSetService.get().getSampleSet(scope, user, rowId);
    }

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

    ExpDataProtocolInput createDataProtocolInput(
            @NotNull String name, @NotNull ExpProtocol protocol, boolean input,
            @Nullable ExpDataClass dataClass, @Nullable ExpProtocolInputCriteria criteria,
            int minOccurs, @Nullable Integer maxOccurs);

    ExpMaterialProtocolInput createMaterialProtocolInput(
            @NotNull String name, @NotNull ExpProtocol protocol, boolean input,
            @Nullable ExpSampleSet sampleSet, @Nullable ExpProtocolInputCriteria criteria,
            int minOccurs, @Nullable Integer maxOccurs);

    @Nullable ExpProtocolInput getProtocolInput(Lsid lsid);

    @Nullable ExpDataProtocolInput getDataProtocolInput(int rowId);
    @Nullable ExpDataProtocolInput getDataProtocolInput(Lsid lsid);
    List<? extends ExpDataProtocolInput> getDataProtocolInputs(int protocolId, boolean input, @Nullable String name, @Nullable Integer materialSourceId);

    @Nullable ExpMaterialProtocolInput getMaterialProtocolInput(int rowId);
    @Nullable ExpMaterialProtocolInput getMaterialProtocolInput(Lsid lsid);
    List<? extends ExpMaterialProtocolInput> getMaterialProtocolInputs(int protocolId, boolean input, @Nullable String name, @Nullable Integer materialSourceId);

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

    Pair<Set<ExpData>, Set<ExpMaterial>> getParents(Container c, User user, ExpRunItem start);

    Pair<Set<ExpData>, Set<ExpMaterial>> getChildren(Container c, User user, ExpRunItem start);

    /**
     * Find all child and grandchild samples Samples that are direct descendants of <code>start</code> ExpData,
     * ignoring any sample children derived from ExpData children.
     */
    Set<ExpMaterial> getRelatedChildSamples(Container c, User user, ExpData start);

    /**
     * Find all parent ExpData that are parents of the <code>start</code> ExpMaterial,
     * stopping at the first parent generation (no grandparents.)
     */
    Set<ExpData> getNearestParentDatas(Container c, User user, ExpMaterial start);

    /**
     * Get the lineage for the seed Identifiable object.  Typically, the seed object is a ExpMaterial,
     * a ExpData (in a DataClass), or an ExpRun.
     */
    @NotNull
    ExpLineage getLineage(Container c, User user, @NotNull Identifiable start, @NotNull ExpLineageOptions options);

    /**
     * The following methods return TableInfo's suitable for using in queries.
     * These TableInfo's initially have no columns, but have methods to
     * add particular columns as needed by the client.
     */
    ExpRunTable createRunTable(String name, UserSchema schema, ContainerFilter cf);

    /**
     * Create a RunGroupMap junction table joining Runs and RunGroups.
     */
    ExpRunGroupMapTable createRunGroupMapTable(String name, UserSchema schema, ContainerFilter cf);

    ExpDataTable createDataTable(String name, UserSchema schema, ContainerFilter cf);

    ExpDataInputTable createDataInputTable(String name, ExpSchema expSchema, ContainerFilter cf);

    ExpDataProtocolInputTable createDataProtocolInputTable(String name, ExpSchema schema, ContainerFilter cf);

    ExpSampleSetTable createSampleSetTable(String name, UserSchema schema, ContainerFilter cf);

    ExpDataClassTable createDataClassTable(String name, UserSchema schema, ContainerFilter cf);

    ExpDataClassDataTable createDataClassDataTable(String name, UserSchema schema, ContainerFilter cf, @NotNull ExpDataClass dataClass);

    ExpProtocolTable createProtocolTable(String name, UserSchema schema, ContainerFilter cf);

    ExpExperimentTable createExperimentTable(String name, UserSchema schema, ContainerFilter cf);

    @Deprecated // TODO ContainerFilter
    default ExpMaterialTable createMaterialTable(String name, UserSchema schema)
    {
        return createMaterialTable(name, schema, null);
    }
    ExpMaterialTable createMaterialTable(String name, UserSchema schema, ContainerFilter cf);

    ExpMaterialInputTable createMaterialInputTable(String name, ExpSchema expSchema, ContainerFilter cf);

    ExpMaterialProtocolInputTable createMaterialProtocolInputTable(String name, ExpSchema schema, ContainerFilter cf);

    ExpProtocolApplicationTable createProtocolApplicationTable(String name, UserSchema schema, ContainerFilter cf);

    ExpQCFlagTable createQCFlagsTable(String name, UserSchema schema, ContainerFilter cf);

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

    TableInfo getTinfoProtocolInput();

    TableInfo getTinfoPropertyDescriptor();

    TableInfo getTinfoAssayQCFlag();

    TableInfo getTinfoAlias();

    TableInfo getTinfoDataAliasMap();

    TableInfo getTinfoMaterialAliasMap();

    TableInfo getTinfoEdge();

    @Deprecated
    default String getDefaultSampleSetLsid()
    {
        return SampleSetService.get().getDefaultSampleSetLsid();
    }

    /**
     * Get all runs associated with these materials, including the source runs and any derived runs
     * @param materials to get runs for
     * @return List of ExpRun's associated to these materials
     */
    List<? extends ExpRun> getRunsUsingMaterials(List<ExpMaterial> materials);

    /**
     * Get all runs associated with these materials, including the source runs and any derived runs
     * @param materialIds to get runs for
     * @return List of ExpRun's associated to these materials
     */
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

    @Deprecated
    default Lsid getSampleSetLsid(String name, Container container)
    {
        return SampleSetService.get().getSampleSetLsid(name, container);
    }

    void deleteExperimentRunsByRowIds(Container container, final User user, int... selectedRunIds);

    void deleteExperimentRunsByRowIds(Container container, final User user, @NotNull Collection<Integer> selectedRunIds);

    void deleteExpExperimentByRowId(Container container, User user, int experimentId);

    /**
     * Increment and get the sample counters for the given date, or the current date if no date is supplied.
     * The resulting map has keys "dailySampleCount", "weeklySampleCount", "monthlySampleCount", and "yearlySampleCount".
     */
    @Deprecated
    default Map<String, Long> incrementSampleCounts(@Nullable Date counterDate)
    {
        return SampleSetService.get().incrementSampleCounts(counterDate);
    }

    void addExperimentListener(ExperimentListener listener);

    void clearCaches();

    List<ProtocolApplicationParameter> getProtocolApplicationParameters(int rowId);

    void moveContainer(Container c, Container cOldParent, Container cNewParent) throws ExperimentException;

    LsidType findType(Lsid lsid);

    Identifiable getObject(Lsid lsid);

    List<? extends ExpData> deleteExperimentRunForMove(int runId, User user);

    /**
     * Kicks off an asynchronous move - a PipelineJob is submitted to the queue to perform the move
     */
    void moveRuns(ViewBackgroundInfo targetInfo, Container sourceContainer, List<ExpRun> runs) throws IOException;

    /**
     * Insert a protocol with optional steps and predecessor configurations.
     *
     * @param baseProtocol the base/top-level protocol to create. Expected to have an ApplicationType and a
     *                     ProtocolParameter value for XarConstants.APPLICATION_LSID_TEMPLATE_URI.
     * @param steps        the protocol steps. Expected to have an ApplicationType and a ProtocolParameter value
     *                     for XarConstants.APPLICATION_LSID_TEMPLATE_URI.
     * @param predecessors Map of Protocol LSIDs to a List of Protocol LSIDs where each entry represents a
     *                     node in the Experiment Protocol graph. If this is not provided the baseProtocol and
     *                     subsequent steps will be organized sequentially.
     * @param user         user with insert permissions
     * @return the saved ExpProtocol
     * @throws ExperimentException
     */
    ExpProtocol insertProtocol(@NotNull ExpProtocol baseProtocol, @Nullable List<ExpProtocol> steps, @Nullable Map<String, List<String>> predecessors, User user) throws ExperimentException;

    ExpProtocol insertSimpleProtocol(ExpProtocol baseProtocol, User user) throws ExperimentException;

    /**
     * The run must be a instance of a protocol created with insertSimpleProtocol().
     * The run must have at least one input and one output.
     *
     * @param run             ExperimentRun, populated with protocol, name, etc
     * @param inputMaterials  map from input role name to input material
     * @param inputDatas      map from input role name to input data
     * @param outputMaterials map from output role name to output material
     * @param outputDatas     map from output role name to output data
     * @param transformedDatas map of output rolw name to transformed output data
     * @param info            context information, including the user
     * @param log             output log target
     * @param loadDataFiles   When true, the files associated with <code>inputDatas</code> and <code>transformedDatas</code> will be loaded by their associated data handler.
     */
    ExpRun saveSimpleExperimentRun(ExpRun run, Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas, Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas, Map<ExpData, String> transformedDatas, ViewBackgroundInfo info, Logger log, boolean loadDataFiles) throws ExperimentException;

    ExpRun saveSimpleExperimentRun(ExpRun run,
                                   Map<ExpMaterial, String> inputMaterials,
                                   Map<ExpData, String> inputDatas,
                                   Map<ExpMaterial, String> outputMaterials,
                                   Map<ExpData, String> outputDatas,
                                   Map<ExpData, String> transformedDatas,
                                   ViewBackgroundInfo info,
                                   Logger log,
                                   boolean loadDataFiles,
                                   @Nullable Set<String> runInputLsids,
                                   @Nullable Set<Pair<String, String>> finalOutputLsids)
            throws ExperimentException;

    /**
     * Adds an extra protocol application to a run created by saveSimpleExperimentRun() to track more complex
     * workflows.
     *
     * @param expRun run to which the extra should be added
     * @param name   name of the protocol application
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

    void registerProtocolInputCriteria(ExpProtocolInputCriteria.Factory factory);

    ProtocolImplementation getProtocolImplementation(String name);

    ExpProtocolApplication getExpProtocolApplication(int rowId);

    List<? extends ExpProtocolApplication> getExpProtocolApplicationsForRun(int runId);

    List<? extends ExpProtocol> getExpProtocols(Container... containers);

    List<? extends ExpProtocol> getAllExpProtocols();

    List<? extends ExpProtocol> getExpProtocolsWithParameterValue(@NotNull String parameterURI, @NotNull String parameterValue, @Nullable Container c);

    void registerRunEditor(ExpRunEditor editor);

    @NotNull
    List<ExpRunEditor> getRunEditors();

    /**
     * Kicks off a pipeline job to asynchronously load the XAR from disk
     *
     * @return the job responsible for doing the work
     */
    PipelineJob importXarAsync(ViewBackgroundInfo info, File file, String description, PipeRoot root) throws IOException;

    /**
     * Loads the xar synchronously, in the context of the pipelineJob
     *
     * @return the runs loaded from the XAR
     */
    List<ExpRun> importXar(XarSource source, PipelineJob pipelineJob, boolean reloadExistingRuns) throws ExperimentException;

    List<ExpRun> importXar(XarSource source, PipelineJob pipelineJob, XarImportOptions options) throws ExperimentException;

    File exportXarForRuns(User user, Set<Integer> runIds, Integer expRowId, XarExportOptions options) throws NotFoundException, IOException, ExperimentException;

    /**
     * Create an experiment run to represent the work that the task's job has done so far.
     * The job's recorded actions will be marked as completed after creating the ExpRun so subsequent
     * runs created by the job won't duplicate the previous actions.
     *
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

    void onBeforeRunSaved(ExpProtocol protocol, ExpRun run, Container container, User user) throws BatchValidationException;

    void onRunDataCreated(ExpProtocol protocol, ExpRun run, Container container, User user) throws BatchValidationException;

    void onMaterialsCreated(List<? extends ExpMaterial> materials, Container container, User user);

    // creates a non-assay backed sample derivation protocol
    ExpProtocol ensureSampleDerivationProtocol(User user) throws ExperimentException;

    // see org.labkey.experiment.LSIDRelativizer
    String LSID_OPTION_ABSOLUTE = "ABSOLUTE";
    String LSID_OPTION_FOLDER_RELATIVE = "FOLDER_RELATIVE";
    String LSID_OPTION_PARTIAL_FOLDER_RELATIVE = "PARTIAL_FOLDER_RELATIVE";

    /**
     * Get the set of runs that can be deleted based on the materials supplied.
     * INCLUDES: Derivative runs, and if only remaining output/derivative the immediate precursor run
     * @param materials Set of materials to get runs for
     * @return Set of runs that can be deleted based on the materials
     */
    List<ExpRun> getDeletableRunsFromMaterials(Collection<? extends ExpMaterial> materials);

    boolean useUXDomainDesigner();

    List<String> collectRunsToInvestigate(ExpRunItem start, ExpLineageOptions options);

    SQLFragment generateExperimentTreeSQLLsidSeeds(List<String> lsids, ExpLineageOptions options);

    class XarExportOptions
    {
        String _lsidRelativizer = LSID_OPTION_FOLDER_RELATIVE;
        String _xarXmlFileName = "experiment.xar";
        java.io.File _exportFile = null;
        // TODO consider tracking separate sets for input data and output data
        boolean _filterDataRoles = false;
        Set<String> dataRoles = new HashSet<>();
        Logger _log = null;

        public String getLsidRelativizer()
        {
            return _lsidRelativizer;
        }

        public XarExportOptions setLsidRelativizer(String lsidRelativizer)
        {
            _lsidRelativizer = lsidRelativizer;
            return this;
        }

        public String getXarXmlFileName()
        {
            return _xarXmlFileName;
        }

        public XarExportOptions setXarXmlFileName(String xarXmlFileName)
        {
            _xarXmlFileName = xarXmlFileName;
            return this;
        }

        public File getExportFile()
        {
            return _exportFile;
        }

        public XarExportOptions setExportFile(File exportFile)
        {
            _exportFile = exportFile;
            return this;
        }

        public Logger getLog()
        {
            return _log;
        }

        public XarExportOptions setLog(Logger log)
        {
            _log = log;
            return this;
        }

        public boolean isFilterDataRoles()
        {
            return _filterDataRoles;
        }

        public XarExportOptions setFilterDataRoles(boolean filterInputDataRoles)
        {
            _filterDataRoles = filterInputDataRoles;
            return this;
        }

        public Set<String> getDataRoles()
        {
            return dataRoles;
        }

        public XarExportOptions setDataRoles(Set<String> dataRoles)
        {
            this.dataRoles = dataRoles;
            return this;
        }
    }

    class XarImportOptions
    {
        boolean _replaceExistingRuns = false;
        boolean _useOriginalDataFileUrl = false;
        boolean _strictValidateExistingSampleSet = true;


        // e.g. delete then re-import

        public boolean isReplaceExistingRuns()
        {
            return _replaceExistingRuns;
        }

        public XarImportOptions setReplaceExistingRuns(boolean b)
        {
            _replaceExistingRuns = b;
            return this;
        }

        // use OriginalFileUrl to find file on disk (probably only useful for internal xar copy/move)

        public boolean isUseOriginalDataFileUrl()
        {
            return _useOriginalDataFileUrl;
        }

        public XarImportOptions setUseOriginalDataFileUrl(boolean useOriginalDataFileUrl)
        {
            _useOriginalDataFileUrl = useOriginalDataFileUrl;
            return this;
        }

        public boolean isStrictValidateExistingSampleSet()
        {
            return _strictValidateExistingSampleSet;
        }

        public XarImportOptions setStrictValidateExistingSampleSet(boolean strictValidateExistingSampleSet)
        {
            _strictValidateExistingSampleSet = strictValidateExistingSampleSet;
            return this;
        }
    }
}
