/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentMaterialListener;
import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidType;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
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
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

public class ExperimentService
{
    static private Interface instance;

    public static final String MODULE_NAME = "Experiment";

    public static final String SCHEMA_LOCATION = "http://cpas.fhcrc.org/exp/xml http://www.labkey.org/download/XarSchema/V2.3/expTypes.xsd";

    static public Interface get()
    {
        return instance;
    }

    static public void setInstance(Interface impl)
    {
        instance = impl;
    }

    public interface Interface extends ExperimentRunTypeSource
    {
        public static final String SAMPLE_DERIVATION_PROTOCOL_LSID = "urn:lsid:labkey.org:Protocol:SampleDerivationProtocol";

        public static final int SIMPLE_PROTOCOL_FIRST_STEP_SEQUENCE = 1;
        public static final int SIMPLE_PROTOCOL_CORE_STEP_SEQUENCE = 10;
        public static final int SIMPLE_PROTOCOL_EXTRA_STEP_SEQUENCE = 15;
        public static final int SIMPLE_PROTOCOL_OUTPUT_STEP_SEQUENCE = 20;

        @Nullable
        ExpObject findObjectFromLSID(String lsid);

        ExpRun getExpRun(int rowid);
        ExpRun getExpRun(String lsid);
        List<? extends ExpRun> getExpRuns(Container container, @Nullable ExpProtocol parentProtocol, @Nullable ExpProtocol childProtocol);
        List<? extends ExpRun> getExpRunsForJobId(int jobId);
        List<? extends ExpRun> getExpRunsForFilePathRoot(File filePathRoot);
        ExpRun createExperimentRun(Container container, String name);

        ExpData getExpData(int rowid);
        ExpData getExpData(String lsid);
        List<? extends ExpData> getExpDatas(int... rowid);
        List<? extends ExpData> getExpDatas(Collection<Integer> rowid);
        List<? extends ExpData> getExpDatas(Container container, @Nullable DataType type, @Nullable String name);
        @NotNull
        List<? extends ExpData> getExpDatasUnderPath(@NotNull File path, @Nullable Container c);

        /**
         * Create a data object.  The object will be unsaved, and will have a name which is a GUID.
         */
        ExpData createData(Container container, DataType type);
        ExpData createData(Container container, DataType type, String name);
        ExpData createData(Container container, String name, String lsid);
        ExpData createData(URI uri, XarSource source) throws XarFormatException;

        ExpMaterial createExpMaterial(Container container, String lsid, String name);
        ExpMaterial getExpMaterial(int rowid);
        List<? extends ExpMaterial> getExpMaterials(Collection<Integer> rowids);
        ExpMaterial getExpMaterial(String lsid);

        /**
         * Looks in all the sample sets visible from the given container for a single match with the specified name 
         */
        List<? extends ExpMaterial> getExpMaterialsByName(String name, Container container, User user);

        Map<String, ExpSampleSet> getSampleSetsForRoles(Container container, ContainerFilter filter, ExpProtocol.ApplicationType type);

        /**
         * Create a new SampleSet with the provided properties.  If a 'Name' property exists in the list, it will be used
         * as the 'id' property of the SampleSet.  Either a 'Name' property must exist or at least one idCol index must be provided.
         */
        ExpSampleSet createSampleSet(Container container, User user, String name, String description, List<GWTPropertyDescriptor> properties, int idCol1, int idCol2, int idCol3, int parentCol)
            throws ExperimentException, SQLException;

        ExpSampleSet createSampleSet();
        ExpSampleSet getSampleSet(int rowid);
        ExpSampleSet getSampleSet(String lsid);

        /**
         * @param includeOtherContainers whether sample sets from the shared container or the container's project should be included
         */
        List<? extends ExpSampleSet> getSampleSets(Container container, User user, boolean includeOtherContainers);
        ExpSampleSet getSampleSet(Container container, String name);
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
        ExpProtocolTable createProtocolTable(String name, UserSchema schema);
        ExpExperimentTable createExperimentTable(String name, UserSchema schema);
        ExpMaterialTable createMaterialTable(String name, UserSchema schema);
        ExpMaterialInputTable createMaterialInputTable(String name, ExpSchema expSchema);
        ExpProtocolApplicationTable createProtocolApplicationTable(String name, UserSchema schema);
        ExpQCFlagTable createQCFlagsTable(String name, UserSchema schema);

        String generateLSID(Container container, Class<? extends ExpObject> clazz, String name);
        String generateGuidLSID(Container container, Class<? extends ExpObject> clazz);
        String generateLSID(Container container, DataType type, String name);
        String generateGuidLSID(Container container, DataType type);

        DataType getDataType(String namespacePrefix);

        DbScope.Transaction ensureTransaction();
        @Deprecated /** Call DbScope.Transaction.commit() instead */
        void commitTransaction();
        @Deprecated /** Call DbScope.Transaction.close() instead */
        void closeTransaction();

        ExperimentRunListView createExperimentRunWebPart(ViewContext context, ExperimentRunType type);

        public DbSchema getSchema();

        ExpProtocolApplication getExpProtocolApplication(String lsid);
        List<? extends ExpProtocolApplication> getExpProtocolApplicationsForProtocolLSID(String protocolLSID);

        List<? extends ExpData> getExpData(Container c);
        ExpData getExpDataByURL(String canonicalURL, @Nullable Container container);
        ExpData getExpDataByURL(File f, @Nullable Container c);
        List<? extends ExpData> getAllExpDataByURL(String canonicalURL);

        TableInfo getTinfoMaterial();
        TableInfo getTinfoMaterialSource();
        TableInfo getTinfoProtocol();
        TableInfo getTinfoProtocolApplication();
        TableInfo getTinfoExperiment();
        TableInfo getTinfoExperimentRun();
        TableInfo getTinfoRunList();
        TableInfo getTinfoData();
        TableInfo getTinfoDataInput();
        TableInfo getTinfoPropertyDescriptor();
        TableInfo getTinfoAssayQCFlag();
        ExpSampleSet ensureDefaultSampleSet();
        ExpSampleSet ensureActiveSampleSet(Container container);
        public String getDefaultSampleSetLsid();

        List<? extends ExpRun> getRunsUsingMaterials(List<ExpMaterial> materials);
        List<? extends ExpRun> getRunsUsingMaterials(int... materialIds);
        List<? extends ExpRun> getRunsUsingDatas(List<ExpData> datas);

        ExpRun getCreatingRun(File file, Container c);
        List<? extends ExpRun> getExpRunsForProtocolIds(boolean includeRelated, int... rowIds);
        List<? extends ExpRun> getRunsUsingSampleSets(ExpSampleSet... sampleSets);

        /**
         * @return the subset of these runs which are supposed to be deleted when one of their inputs is deleted.
         */
        List<? extends ExpRun> runsDeletedWithInput(List<? extends ExpRun> runs);

        void deleteAllExpObjInContainer(Container container, User user) throws ExperimentException;

        Lsid getSampleSetLsid(String name, Container container);

        void deleteExperimentRunsByRowIds(Container container, final User user, int... selectedRunIds);

        void clearCaches();

        List<ProtocolApplicationParameter> getProtocolApplicationParameters(int rowId);

        void moveContainer(Container c, Container cOldParent, Container cNewParent) throws ExperimentException;

        LsidType findType(Lsid lsid);

        Identifiable getObject(Lsid lsid);

        List<? extends ExpData> deleteExperimentRunForMove(int runId, User user);

        /** Kicks off an asynchronous move - a PipelineJob is submitted to the queue to perform the move */
        void moveRuns(ViewBackgroundInfo targetInfo, Container sourceContainer, List<ExpRun> runs) throws IOException;

        public ExpProtocol insertSimpleProtocol(ExpProtocol baseProtocol, User user) throws ExperimentException;

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
        public ExpRun saveSimpleExperimentRun(ExpRun run, Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas, Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas, Map<ExpData, String> transformedDatas, ViewBackgroundInfo info, Logger log, boolean loadDataFiles) throws ExperimentException;

        /**
         * Adds an extra protocol application to a run created by saveSimpleExperimentRun() to track more complex
         * workflows.
         * @param expRun run to which the extra should be added
         * @param name name of the prococol application
         * @return a fully populated but not yet saved ExpProtocolApplication. It will have no inputs and outputs.
         */
        public ExpProtocolApplication createSimpleRunExtraProtocolApplication(ExpRun expRun, String name);
        public ExpRun deriveSamples(Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials, ViewBackgroundInfo info, Logger log) throws ExperimentException;

        public void registerExperimentMaterialListener(ExperimentMaterialListener listener);
        public void registerExperimentDataHandler(ExperimentDataHandler handler);
        public void registerExperimentRunTypeSource(ExperimentRunTypeSource source);
        public void registerDataType(DataType type);
        public void registerProtocolImplementation(ProtocolImplementation impl);

        public ProtocolImplementation getProtocolImplementation(String name);

        ExpProtocolApplication getExpProtocolApplication(int rowId);
        List<? extends ExpProtocolApplication> getExpProtocolApplicationsForRun(int runId);

        List<? extends ExpProtocol> getExpProtocols(Container... containers);
        List<? extends ExpProtocol> getAllExpProtocols();

        /**
         * Kicks off a pipeline job to asynchronously load the XAR from disk
         * @return the job responsible for doing the work
         */
        PipelineJob importXarAsync(ViewBackgroundInfo info, File file, String description, PipeRoot root) throws IOException;

        /**
         * Loads the xar synchronously, in the context of the pipelineJob
         * @return the runs loaded from the XAR
         */
        public List<ExpRun> importXar(XarSource source, PipelineJob pipelineJob, boolean reloadExistingRuns) throws ExperimentException;

        /**
         * Create an experiment run to represent the work that the task's job has done so far.
         * The job's recorded actions will be marked as completed after creating the ExpRun so subsequent
         * runs created by the job won't duplicate the previous actions.
         * @param job Pipeline job.
         * @return the run created from the job's actions.
         */
        public ExpRun importRun(PipelineJob job, XarSource source) throws SQLException, PipelineJobException, ValidationException;

        /**
         * Provides access to an object that should be locked before inserting protocols. Locking when doing
         * experiment run insertion has turned out to be problematic and deadlock prone. It's more pragmatic to have
         * the occassional import fail with a SQLException due to duplicate insertions compared with deadlocking the
         * whole server.
         *
         * @return lock object on which to synchronize
         */
        public Lock getProtocolImportLock();

        HttpView createRunExportView(Container container, String defaultFilenamePrefix);
        HttpView createFileExportView(Container container, String defaultFilenamePrefix);

        void auditRunEvent(User user, ExpProtocol protocol, ExpRun run, @Nullable ExpExperiment runGroup, String message);

        List<? extends ExpExperiment> getMatchingBatches(String name, Container container, ExpProtocol protocol);

        List<? extends ExpProtocol> getExpProtocolsUsedByRuns(Container c, ContainerFilter containerFilter);
    }
}
