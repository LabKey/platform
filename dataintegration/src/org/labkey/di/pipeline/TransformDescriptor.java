/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.di.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.StringBuilderWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.pipeline.XarGeneratorFactorySettings;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.TaskPipelineRegistry;
import org.labkey.api.pipeline.TaskPipelineSettings;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.writer.ContainerUser;
import org.labkey.di.DataIntegrationQuerySchema;
import org.labkey.di.VariableMap;
import org.labkey.di.data.TransformDataType;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.steps.ExternalPipelineTaskMeta;
import org.labkey.di.steps.StepMeta;
import org.labkey.di.steps.TaskRefTransformStepMeta;
import org.labkey.di.steps.TestTask;
import org.labkey.etl.xml.EtlType;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.ScheduleBuilder;
import org.quartz.SimpleScheduleBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * User: jeckels
 * Date: 2/20/13
 */
public class TransformDescriptor implements ScheduledPipelineJobDescriptor<ScheduledPipelineJobContext>, Serializable
{
    private static final Logger LOG = Logger.getLogger(TransformDescriptor.class);

    private final String _id;
    private final String _name;
    private final String _description;
    private final String _moduleName;
    private final boolean _loadReferencedFiles;
    private final boolean _gatedByStep;
    private final boolean _standalone;
    private final boolean _siteScope;
    private boolean _allowMultipleQueuing;
    private final String _transactSourceSchema;
    private final String _transactTargetSchema;
    private Map<String, String> _pipelineParameters;
    private Map<ParameterDescription, Object> _constants;
    private static final String XAR_EXT = ".etl.xar.xml";

    // declared variables
    private Map<ParameterDescription,Object> _declaredVariables = new LinkedHashMap<>();

    // schedule
    private final Long _interval;
    private final CronExpression _cron;

    // steps
    private final FilterStrategy.Factory _defaultFactory;
    private final ArrayList<StepMeta> _stepMetaDatas;

    TransformDescriptor(String id, String name, String moduleName) throws XmlException, IOException
    {
        this(id, name, null, moduleName, null, null, null, null, null, false, false, true, false, false, null, null, null, null);
    }

    TransformDescriptor(String id, EtlType etlXml, String moduleName, Long interval,
                        CronExpression cron, FilterStrategy.Factory defaultFactory, ArrayList<StepMeta> stepMetaDatas,
                        Map<ParameterDescription, Object> declaredVariables, boolean gatedByStep,
                        Map<String, String> pipelineParameters, Map<ParameterDescription, Object> constants) throws XmlException, IOException
    {
        this(id, etlXml.getName(), etlXml.getDescription(), moduleName, interval, cron, defaultFactory, stepMetaDatas,
                declaredVariables, etlXml.getLoadReferencedFiles(), gatedByStep, etlXml.getStandalone(),
                etlXml.getSiteScope(), etlXml.getAllowMultipleQueuing(), pipelineParameters, constants,
                etlXml.getTransactSourceSchema(), etlXml.getTransactDestinationSchema());
    }

    private TransformDescriptor(String id, String name, String description, String moduleName, Long interval,
                                CronExpression cron, FilterStrategy.Factory defaultFactory, ArrayList<StepMeta> stepMetaDatas,
                                Map<ParameterDescription, Object> declaredVariables,
                                boolean loadReferencedFiles, boolean gatedByStep, boolean standalone, boolean siteScope, boolean allowMultipleQueuing,
                                Map<String, String> pipelineParameters, Map<ParameterDescription, Object> constants,
                                String transactSourceSchema, String transactTargetSchema) throws XmlException, IOException
    {
        _id = id;
        _name = name;
        _description = description;
        _moduleName = moduleName;
        _interval = interval;
        _cron = cron;
        _defaultFactory = defaultFactory;
        _stepMetaDatas = stepMetaDatas;
        if (null != declaredVariables)
            _declaredVariables.putAll(declaredVariables);
        _loadReferencedFiles = loadReferencedFiles;
        _gatedByStep = gatedByStep;
        _standalone = standalone;
        _siteScope = siteScope;
        _allowMultipleQueuing = allowMultipleQueuing;
        _pipelineParameters = pipelineParameters;
        _constants = constants;
        _transactSourceSchema = transactSourceSchema;
        _transactTargetSchema = transactTargetSchema;
    }

    public String getName()
    {
        return _name;
    }

    public String getDescription()
    {
        return _description;
    }

    public String getModuleName()
    {
        return _moduleName;
    }

    public String getId()
    {
        return _id;
    }

    public boolean isGatedByStep()
    {
        return _gatedByStep;
    }

    public List<StepMeta> getStepMetaDatas()
    {
        return Collections.unmodifiableList(_stepMetaDatas);
    }

    public int getVersion()
    {
        // TODO - add config for real version number
        return 1;
    }

    public Map<ParameterDescription,Object> getDeclaredVariables()
    {
        return Collections.unmodifiableMap(_declaredVariables);
    }

    public FilterStrategy.Factory getDefaultFilterFactory()
    {
        return _defaultFactory;
    }

    @Override
    public boolean isStandalone()
    {
        return _standalone;
    }

    @Override
    public boolean isSiteScope()
    {
        return _siteScope;
    }

    public String getTransactSourceSchema()
    {
        return _transactSourceSchema;
    }

    public String getTransactTargetSchema()
    {
        return _transactTargetSchema;
    }

    @Override
    public String toString()
    {
        return "ETLDescriptor: " + _name + " (" + getScheduleDescription() + ", " + _stepMetaDatas.get(0).toString() + ")";
    }


    public ScheduleBuilder getScheduleBuilder()
    {
        if (null != _interval)
        {
            return SimpleScheduleBuilder.simpleSchedule()
                              .withIntervalInMilliseconds(_interval)
                              .repeatForever();
        }
        else if (null != _cron)
        {
            return CronScheduleBuilder.cronSchedule(_cron);
        }
        else
        {
            return SimpleScheduleBuilder.repeatHourlyForever();
        }
    }


    public String getScheduleDescription()
    {
        if (null != _interval)
        {
            return DateUtil.formatDuration(_interval);
        }
        else if (null != _cron)
        {
            return _cron.getCronExpression();
        }
        else
            return "1h";
    }


    @Override
    public Class<? extends Job> getQuartzJobClass()
    {
        return TransformQuartzJobRunner.class;
    }


    @Override
    public ScheduledPipelineJobContext getJobContext(Container c, User user, Map<ParameterDescription, Object> params)
    {
        return new TransformJobContext(this, c, user, params);
    }

    @Override
    public boolean checkForWork(ScheduledPipelineJobContext context, boolean background, boolean verbose)
    {
        if (null == context.getContainer())
            throw new IllegalArgumentException("container context is null");

        Exception x = null;
        String errorVerbose = null;

        try
        {
            LOG.debug("Running" + this.getClass().getSimpleName() + " " + this.toString());

            Container c = context.getContainer();
            if (null == c)
                return false;
            // Call this just to create the TransformConfiguration record if it doesn't exist
            TransformManager.get().getTransformConfiguration(c, this);
            validate(context);
            for (StepMeta stepMeta : _stepMetaDatas)
            {
                TransformTask step = stepMeta.getProvider().createStepInstance(null, null, stepMeta, (TransformJobContext)context);
                if (step.hasWork())
                    return true;
            }
        }
        catch (Exception ex)
        {
            x = ex;
            StringBuilder sb = new StringBuilder();
            StringBuilderWriter sbw = new StringBuilderWriter(sb);
            ex.printStackTrace(new PrintWriter(sbw));
            errorVerbose = sb.toString();
        }

        if (null != x || verbose || context.isVerbose())
        {
            TransformRun run = new TransformRun();
            run.setContainer(context.getContainer());
            run.setTransformId(getId());
            run.setTransformVersion(getVersion());
            run.setStartTime(new Date());
            if (null == x)
                run.setTransformRunStatusEnum(TransformRun.TransformRunStatus.NO_WORK);
            else
                run.setTransformRunStatusEnum(TransformRun.TransformRunStatus.ERROR);
            if (null != errorVerbose)
                run.setTransformRunLog(errorVerbose);
            TransformManager.get().insertTransformRun(context.getUser(),run);
        }

        if (null != x)
            wrapException(x);

        return false;
    }

    private void validate(ScheduledPipelineJobContext context)
    {
        if (null != getTransactSourceSchema())
        {
            DbScope sourceScope = Optional.ofNullable(DefaultSchema
                    .get(context.getUser(), context.getContainer(), getTransactSourceSchema()))
                    .orElseThrow(() -> new ConfigurationException("transactSourceSchema not found for this container: " + getTransactSourceSchema()))
                    .getDbSchema()
                    .getScope();

            if (!sourceScope.getSqlDialect().isPostgreSQL())
                throw new ConfigurationException("Transacting the source scope is only available on Postgres data sources.");
        }

        if (null != getTransactTargetSchema())
        {
            Optional.ofNullable(DefaultSchema
                    .get(context.getUser(), context.getContainer(), getTransactTargetSchema()))
                    .orElseThrow(() -> new ConfigurationException("transactDestinationSchema not found for this container: " + getTransactTargetSchema()));
        }
    }


    private void wrapException(Exception x)
    {
        try
        {
            throw x;
        }
        catch (RuntimeException ex)
        {
            throw ex;
        }
        catch (SQLException ex)
        {
            throw new RuntimeSQLException(ex);
        }
        catch (Exception ex)
        {
            throw new UnexpectedException(ex);
        }
    }


    @Override
    public PipelineJob getPipelineJob(ScheduledPipelineJobContext context) throws PipelineJobException
    {
        TransformPipelineJob job = new TransformPipelineJob((TransformJobContext)context, this);
        try
        {
            PipelineService.get().setStatus(job, PipelineJob.TaskStatus.waiting.toString(), null, true);
        }
        catch (Exception e)
        {
            LOG.error("Unable to queue ETL job", e);
            return null;
        }

        TransformRun run = new TransformRun();
        run.setTransformRunStatusEnum(TransformRun.TransformRunStatus.PENDING);
        run.setStartTime(new Date());
        run.setTransformId(getId());
        run.setTransformVersion(getVersion());
        run.setContainer(context.getContainer());

        PipelineStatusFile statusFile = PipelineService.get().getStatusFile(job.getLogFile());
        run.setJobId(statusFile.getRowId());

        try
        {
            TransformManager.get().insertTransformRun(context.getUser(), run);
        }
        catch (RuntimeSQLException e)
        {
            throw new PipelineJobException(e);
        }

        job.setRunId(run.getTransformRunId());
        ((TransformJobContext) context).setPipelineJob(job);
        return job;
    }


    PipelineJob.Task createTask(TransformTaskFactory factory, TransformPipelineJob job, TransformJobContext context, int i)
    {
        if (i != 0)
            throw new IllegalArgumentException();

        StepMeta meta = getTransformStepMetaFromTaskId(factory.getId());

        if (meta == null)
        {
            throw new IllegalArgumentException("Bad transform task factory " + factory.getId() + ", no matching task found in config file.");
        }
        return meta.getProvider().createStepInstance(factory, job, meta, context);

    }


/*
    public Map<String, Object> toJSON(@Nullable Map<String,Object> map)
    {
        if (null == map)
            map = new JSONObject();
        map.put("id", getId());
        map.put("description", getDescription());
        map.put("name", getName());
        map.put("moduleName", getModuleName());
        map.put("scheduleDescription", getScheduleDescription());
        map.put("version", getVersion());
        return map;
    }
*/


    private StepMeta getTransformStepMetaFromTaskId(TaskId tid)
    {
        // step ids are guaranteed to be unique
        for (StepMeta meta : _stepMetaDatas)
        {
            if (StringUtils.equals(getFullTaskName(meta), tid.getName()))
                return meta;
        }

        return null;
    }

    // get/build the task pipeline specific to this descriptor
    public TaskPipeline getTaskPipeline()
    {
        TaskId pipelineId = new TaskId(getModuleName(),TaskId.Type.pipeline, getId(), 0);
        TaskPipeline pipeline = PipelineJobService.get().getTaskPipeline(pipelineId);

        if (null == pipeline)
        {
            try
            {
                registerTaskPipeline(pipelineId);
                pipeline = PipelineJobService.get().getTaskPipeline(pipelineId);
                assert pipeline != null;
            }
            catch(CloneNotSupportedException ignore) {}
        }

        return pipeline;
    }

    private void registerTaskPipeline(TaskId pipelineId) throws CloneNotSupportedException
    {
        Module declaringModule = ModuleLoader.getInstance().getModule(getModuleName());
        ArrayList<Object> progressionSpec = new ArrayList<>();
        TaskPipelineSettings settings = new TaskPipelineSettings(pipelineId);
        settings.setDeclaringModule(declaringModule);

        // Register all the tasks that are associated with this transform and
        // associate the correct stepMetaData with the task via the index
        int i = 0;
        for (StepMeta meta : _stepMetaDatas)
        {
            i++;
            if ((meta instanceof ExternalPipelineTaskMeta))
            {
                // If the first step of an etl is a pipeline command task, the logic on whether or not to queue a
                // second job if one is already pending does not apply.
                if (i == 1)
                    _allowMultipleQueuing = true;
                progressionSpec.add(((ExternalPipelineTaskMeta) meta).getExternalTaskId());

                // Register the task to generate an experiment run to track this transform.
                if (_loadReferencedFiles)
                {
                    XarGeneratorFactorySettings factorySettings = new XarGeneratorFactorySettings(getFullTaskName(meta));
                    factorySettings.setOutputExt(".step" + i + XAR_EXT);
                    PipelineJobService.get().addTaskFactory(factorySettings);
                    progressionSpec.add(factorySettings.getId());
                }
                // Optionally set the workflowProcessKey from the parent pipeline
                TaskId parentPipelineTaskId = ((ExternalPipelineTaskMeta) meta).getParentPipelineTaskId();
                if (null != parentPipelineTaskId)
                {
                    TaskPipeline tp = PipelineJobService.get().getTaskPipeline(parentPipelineTaskId);
                    if (null != tp)
                    {
                        settings.setWorkflowProcessKey(tp.getWorkflowProcessKey());
                        settings.setWorkflowProcessModule(tp.getWorkflowProcessModule());
                    }
                }
            }
            else
            {
                String taskName = getFullTaskName(meta);
                Class taskClass = meta.getProvider().getStepClass();
                TaskId taskId = new TaskId(getModuleName(), TaskId.Type.task, taskName, 0);
                // check to see if this class is part of our known transform tasks
                if (TransformTask.class.isAssignableFrom(taskClass))
                {
                    TransformTaskFactory factory = new TransformTaskFactory(taskId);
                    factory.setDeclaringModule(declaringModule);
                    PipelineJobService.get().addLocalTaskFactory(pipelineId, factory);
                }
                else
                {
                    //
                    // be sure to update if any new task factories are added
                    //
                    assert false;
                    continue;
                }

                progressionSpec.add(taskId);
            }
        }

        XarGeneratorFactorySettings factorySettings = new XarGeneratorFactorySettings("expGen");
        factorySettings.setOutputExt(".expGen" + XAR_EXT);
        factorySettings.setLoadFiles(_loadReferencedFiles);
        PipelineJobService.get().addTaskFactory(factorySettings);
        progressionSpec.add(factorySettings.getId());

        // add the pipeline
        settings.setTaskProgressionSpec(progressionSpec.toArray());
        PipelineJobService.get().addTaskPipeline(settings);
    }

    private String getFullTaskName(StepMeta meta)
    {
        return getId() + TaskPipelineRegistry.LOCAL_TASK_PREFIX + meta.getId();
    }

    @Override
    public ScheduledPipelineJobDescriptor getDescriptorFromCache()
    {
        return TransformManager.get().getDescriptor(getId());
    }

    @Override
    public boolean isPending(ContainerUser context)
    {
        return TransformManager.get().transformIsPending(context, getId(), getFullTaskName(_stepMetaDatas.get(0)));
    }

    @Override
    public boolean isAllowMultipleQueuing()
    {
        return _allowMultipleQueuing;
    }

    @Override
    public Map<String, String> getPipelineParameters()
    {
        return Collections.unmodifiableMap(_pipelineParameters);
    }

    public Map<ParameterDescription, Object> getConstants()
    {
        return Collections.unmodifiableMap(_constants);
    }

    public static class TestCase extends Assert
    {
        private static final String BASE_PATH = "dataintegration/etls/";
        private static final String ONE_TASK = "one.xml";
        private static final String UNIT_TASKS = "unit.xml";
        private static final String FOUR_TASKS = "four.xml";
        private static final String NO_ID = "noid.xml";
        private static final String DUP_ID = "duplicate.xml";
        private static final String UNKNOWN_CLASS = "unknown.xml";
        private static final String INVALID_CLASS = "invalid.xml";
        private static final String NO_CLASS = "noclass.xml";
        private static final String BAD_SOURCE_OPT = "badsourceopt.xml";
        private static final String BAD_TARGET_OPT = "badtargetopt.xml";
        private static final String BAD_SOURCE = "badsource.xml";
        private static final String BAD_TARGET = "badtarget.xml";
        private static final String BAD_PROCEDURE = "badprocedure.xml";
        private static final String TASKREF_BAD_SETTING = "taskrefbadsetting.xml";
        private static final int TRY_QUANTA = 100; // ms
        private static final int NUM_TRIES = 100; // retry for a maximum of 10 seconds
        private static final Module module = ModuleLoader.getInstance().getModule("DataIntegration");

        class EtlResource implements Resource
        {
            private File _f;
            public EtlResource(File f) { _f = f;}
            public Resolver getResolver() { return null; }
            public Path getPath()
            {
                // this will break if we ever try to retry the job but
                // we want the short path name to uniquely identify this
                // pipeline id
                return new Path(_f.getName());
            }
            public String getName() { return null; }
            public boolean exists() { return _f.exists();}
            public boolean isCollection() { return false; }
            public Resource find(String name) { return null ;}
            public boolean isFile() { return true; }
            public Collection<String> listNames() { return null; }
            public Collection<? extends Resource> list() { return null; }
            public Resource parent() { return null; }
            public long getVersionStamp() { return 0; }
            public long getLastModified()
            {
                return _f.lastModified();
            }
            public InputStream getInputStream() throws IOException
            {
                return new FileInputStream(_f);
            }
            public String toString() { return _f.toString();}
        }

        private File getFile(String file) throws IOException
        {
            return JunitUtil.getSampleData(null, BASE_PATH + file);
        }

        @Test
        public void descriptorSyntax() throws Exception
        {
            //
            // does a sanity check on ETL config file parsing and verifies that invalid ETL configuration options are
            // correctly caught with the appropriate error messages
            //

            TransformDescriptor d;

            d = checkValidSyntax(getFile(ONE_TASK));
            assertEquals(1, d._stepMetaDatas.size());

            d = checkValidSyntax(getFile(FOUR_TASKS));
            assertEquals(4, d._stepMetaDatas.size());

            d = checkValidSyntax(getFile("interval1sec.xml"));
            assertEquals("1s", d.getScheduleDescription());
            d = checkValidSyntax(getFile("interval2.xml"));
            assertEquals("2m", d.getScheduleDescription());
            d = checkValidSyntax(getFile("interval5m.xml"));
            assertEquals("5m", d.getScheduleDescription());
            d = checkValidSyntax(getFile("cron5m.xml"));
            assertEquals("0 0/5 * * * ?", d.getScheduleDescription());
            d = checkValidSyntax(getFile("cron1h.xml"));
            assertEquals("0 0 * * * ?", d.getScheduleDescription());

            d = checkValidSyntax(getFile(NO_CLASS));
            assertEquals(1, d._stepMetaDatas.size());

            checkInvalidSyntax(getFile(NO_ID), TransformManager.ID_REQUIRED);
            checkInvalidSyntax(getFile(DUP_ID), TransformManager.DUPLICATE_ID);
            checkInvalidSyntax(getFile(UNKNOWN_CLASS), TransformManager.INVALID_TYPE);
            checkInvalidSyntax(getFile(INVALID_CLASS), TransformManager.INVALID_TYPE);
            checkInvalidSyntax(getFile(BAD_SOURCE_OPT), TransformManager.INVALID_SOURCE_OPTION);
            checkInvalidSyntax(getFile(BAD_TARGET_OPT), TransformManager.INVALID_TARGET_OPTION);
            checkInvalidSyntax(getFile(BAD_SOURCE), TransformManager.INVALID_SOURCE);
            checkInvalidSyntax(getFile(BAD_TARGET), TransformManager.INVALID_DESTINATION);
            checkInvalidSyntax(getFile(BAD_PROCEDURE), TransformManager.INVALID_PROCEDURE);
            checkInvalidSyntax(getFile(TASKREF_BAD_SETTING), TaskRefTransformStepMeta.TASKREF_MISSING_REQUIRED_SETTING + "\nsetting1\n");
        }

        @Test
        public void registerTaskPipeline() throws Exception
        {
            //
            // verifies that the correct task pipeline is setup for ONE_TASK and FOUR_TASK ETL configurations
            //

            TransformDescriptor d1 = TransformManager.get().parseETLThrow(new EtlResource(getFile(ONE_TASK)), module);
            TaskPipeline p1 = d1.getTaskPipeline();
            assertNotNull(p1);

            // calling twice should be just fine
            TaskPipeline p2 = d1.getTaskPipeline();
            assertNotNull(p2);
            assertEquals(p1, p2);
            verifyTaskPipeline(p2, d1);

            TransformDescriptor d4 =  TransformManager.get().parseETLThrow(new EtlResource(getFile(FOUR_TASKS)), module);
            TaskPipeline p4 = d4.getTaskPipeline();
            assertNotNull(p4);
            verifyTaskPipeline(p4, d4);
        }

        @Test
        public void runValidEtl() throws Exception
        {
            final User u = TestContext.get().getUser();
            final Container c = JunitUtil.getTestContainer();

            try
            {
                TransformPipelineJob job = runEtl(c, u, -1);
                verifyEtl(job, true );
            }
            finally
            {
                cleanup(c,u);

            }
        }

        @Test
        public void runInvalidEtl() throws Exception
        {
            final User u = TestContext.get().getUser();
            final Container c = JunitUtil.getTestContainer();

            try
            {
                TransformPipelineJob job = runEtl(c, u, 1);
                verifyEtl(job, false);
            }
            finally
            {
                cleanup(c,u);
            }
        }

        private void cleanup(Container c, User u)
        {
            try
            {
                ContainerManager.deleteAll(c, u);
            }
            catch(UnauthorizedException ignore){}
        }


        private TransformPipelineJob runEtl(Container c, User u, int failStep) throws Exception
        {
            //
            // runs an ETL job (junit.xml) with two test tasks.  Tests the end to end scenario of running a checker,
            // executing a multi-step job, and logging the ETL as an experiment run
            //
            TransformDescriptor d =  TransformManager.get().parseETLThrow(new EtlResource(getFile(UNIT_TASKS)), module);
            TransformJobContext context = new TransformJobContext(d, c, u, null);
            TransformPipelineJob job = (TransformPipelineJob) d.getPipelineJob(context);

            VariableMap map = job.getVariableMap();
            assertEquals("Expected The transient 'RunStep1' flag added at initialization.", 1, map.keySet().size());

            // add a transient variable (non-persisted) to the variable map
            // and ensure it doesn't show up in the protocol application properties
            map.put(TestTask.Transient, "don't persist!");

            if (failStep > 0)
            {
                // fail the step passed in; note that the index is 0 based
                map.put(TestTask.FailStep, d.getFullTaskName(d._stepMetaDatas.get(failStep)));
            }

            // put in a property we want persisted at the job level
            map.put(TransformProperty.RecordsInserted.getPropertyDescriptor(), TestTask.recordsInsertedJob);

            PipelineService.get().queueJob(job);
            waitForJobToFinish(job);

            return job;
        }

        private void verifyEtl(TransformPipelineJob transformJob, boolean isSuccess) throws Exception
        {
            TransformRun transformRun = verifyTransformRun(transformJob, isSuccess);
            if (isSuccess)
                verifyTransformExp(transformRun, transformJob);
        }

        private TransformRun verifyTransformRun(TransformPipelineJob job, boolean isSuccess)
        {
            int runId = job.getTransformRunId();
            TransformDescriptor d = job.getTransformDescriptor();
            TransformRun run = TransformManager.get().getTransformRun(job.getContainer(), runId);
            String status = run.getStatus();
            Integer expId = TransformManager.get().getExpRunIdForTransformRun(run);
            Integer jobId = run.getJobId();

            assertNotNull(run.getStartTime());
            assertNotNull(run.getEndTime());
            assertNotNull(jobId);
            assertTrue(String.format("Expected transform ids to match: expected '%s', got '%s'", run.getTransformId(), d.getId()),
                    StringUtils.equalsIgnoreCase(run.getTransformId(), d.getId()));

            if (isSuccess)
            {
                // number of records affected == number of steps * total number of record operations in each step
                int numSteps = d._stepMetaDatas.size();
                int totalRecordCount = numSteps * (TestTask.recordsDeleted + TestTask.recordsInserted + TestTask.recordsModified);

                assertNotNull(run.getRecordCount());
                assertEquals(totalRecordCount, run.getRecordCount().intValue());
                assertTrue(PipelineJob.TaskStatus.complete.matches(status));
                assertNotNull(expId);
            }
            else
            {
                assertTrue("Got status: " + status, PipelineJob.TaskStatus.error.matches(status));
                assertNull(expId);
            }

            return run;
        }

        // check that we logged the transform run experiment correctly
        private void verifyTransformExp(TransformRun transformRun, TransformPipelineJob transformJob)
        {
            TransformDescriptor d = transformJob.getTransformDescriptor();
            ExpRun expRun = ExperimentService.get().getExpRun(TransformManager.get().getExpRunIdForTransformRun(transformRun));

            //
            // verify run standard properties
            //
            String expectedRunName = TransformPipelineJob.ETL_PREFIX + d.getDescription();
            assertTrue(String.format("Expected run name didn't match: expected '%s', got '%s'", expectedRunName, expRun.getName()),
                    expRun.getName().equalsIgnoreCase(expectedRunName));

            //
            // verify custom propeties
            //
            Map<String, ObjectProperty> mapProps = expRun.getObjectProperties();
            assertEquals(1, mapProps.size());
            ObjectProperty prop = mapProps.get(TransformProperty.RecordsInserted.getPropertyDescriptor().getPropertyURI());
            assert TestTask.recordsInsertedJob ==  prop.getFloatValue();

            // verify the variable map generated is correct from the TransformManager helper function
            verifyVariableMap(TransformManager.get().getVariableMapForTransformJob(transformRun), mapProps);

            //
            // verify data inputs: test job has two source inputs
            //
            List<? extends ExpData> datas = expRun.getInputDatas(TransformTask.INPUT_ROLE, null);
            verifyDatas(d, datas, 2, true );

            //
            // verify data outputs: test job has two target outputs
            //
            datas = expRun.getOutputDatas(ExperimentService.get().getDataType(TransformDataType.TRANSFORM_DATA_PREFIX));
            verifyDatas(d, datas, 2, false);

            //
            //  verify protocol for this job
            //
            ExpProtocol protocol = expRun.getProtocol();
            String expectedProtocol = d.getModuleName() + ":" + TaskId.Type.pipeline + ":" + d.getId();
            Assert.assertTrue(String.format("Expected protocols to match: expected '%s', got '%s'", expectedProtocol, protocol.getName()),
                    protocol.getName().equalsIgnoreCase(expectedProtocol));

            List<? extends ExpProtocolAction> actions = protocol.getSteps();
            // ignore input and output actions in count
            assertEquals(d._stepMetaDatas.size(), actions.size() - 2);

            //
            // verify protocol applications:  we have two steps that map to this
            //
            List<? extends ExpProtocolApplication> apps = expRun.getProtocolApplications();
            assertEquals(d._stepMetaDatas.size(), apps.size() - 2);

            for (ExpProtocolApplication app : apps)
            {
                verifyProtocolApplication(d, app, transformRun);
            }
       }


        private void verifyProtocolApplication(TransformDescriptor d, ExpProtocolApplication app, TransformRun transformRun)
        {
            if (app.getName().equalsIgnoreCase("Run inputs") || app.getName().equalsIgnoreCase("Run outputs"))
                return;

            //
            // verify inputs, outputs, standard props, custom properties
            //
            assertTrue(isValidStep(d, app.getName()));

            List<? extends ExpData> datas = app.getInputDatas();
            verifyDatas(d, datas, 1, true);

            datas = app.getOutputDatas();
            verifyDatas(d, datas, 1, false);

            // verify our step start/end times are within the bounds of the entire run start/end times
            assertTrue(transformRun.getStartTime().getTime() <= app.getStartTime().getTime());
            assertTrue(transformRun.getEndTime().getTime() >= app.getEndTime().getTime());
            // verify step recordcount is one step's worth of work
            assertEquals(TestTask.recordsDeleted + TestTask.recordsInserted + TestTask.recordsModified, app.getRecordCount().intValue());
            // verify we have object properties and they are found in the variable map
            Map<String, ObjectProperty> mapProps = app.getObjectProperties();

            // we should only have 3 custom properties for the test task
            assertEquals(3, mapProps.size());
            ObjectProperty prop = mapProps.get(TransformProperty.RecordsDeleted.getPropertyDescriptor().getPropertyURI());
            assertEquals(TestTask.recordsDeleted, prop.getFloatValue().intValue());
            prop = mapProps.get(TransformProperty.RecordsInserted.getPropertyDescriptor().getPropertyURI());
            assertEquals(TestTask.recordsInserted, prop.getFloatValue().intValue());
            prop = mapProps.get(TransformProperty.RecordsModified.getPropertyDescriptor().getPropertyURI());
            assertEquals(TestTask.recordsModified, prop.getFloatValue().intValue());

            // finally, verify that the VariableMap we build out of the protocol application properties is correct
            verifyVariableMap(TransformManager.get().getVariableMapForTransformStep(transformRun, app.getName()), mapProps);
       }

        private void verifyVariableMap(VariableMap varMap, Map<String, ObjectProperty> propMap)
        {
            assertEquals(varMap.keySet().size(), propMap.keySet().size());
            for (String key : propMap.keySet())
            {
                ObjectProperty p = propMap.get(key);
                Integer var = (Integer)varMap.get(p.getName());
                assertEquals(var.intValue(), p.getFloatValue().intValue());
            }
        }

        private boolean isValidStep(TransformDescriptor d, String stepName)
        {
            for (StepMeta meta : d._stepMetaDatas)
            {
                if (stepName.equalsIgnoreCase(d.getFullTaskName(meta)))
                    return true;
            }

            return false;
        }

        private void verifyDatas(TransformDescriptor d, List<? extends ExpData> datas, int expectedCount, boolean isInput)
        {
            assertEquals(expectedCount, datas.size());

            for (ExpData data : datas)
            {
                verifyData(d, data.getName(), isInput);
            }
        }

        // find the data name in the descriptor for this step
        private void verifyData(TransformDescriptor d, String dataName, boolean isInput)
        {
            boolean found = false;
            for(StepMeta meta : d._stepMetaDatas)
            {
                String name;

                if (isInput)
                    name = meta.getSourceSchema() + "." + meta.getSourceQuery();
                else
                    name = meta.getTargetSchema() + "." + meta.getTargetQuery();

                if (dataName.equalsIgnoreCase(name))
                {
                    found = true;
                    break;
                }
            }

            assertTrue(found);
        }


        private void waitForJobToFinish(PipelineJob job) throws Exception
        {
            SimpleFilter f = new SimpleFilter();
            TransformPipelineJob tj = (TransformPipelineJob) job;
            TableInfo ti = DataIntegrationQuerySchema.getTransformRunTableInfo();
            f.addCondition(new FieldKey(null, "transformrunid"), tj.getTransformRunId(), CompareType.EQUAL);

            for (int i = 0; i < NUM_TRIES; i++)
            {
                Thread.sleep(TRY_QUANTA);

                if (isDone(job))
                {
                    // wait for us to finish updating transformrun table before continuing
                    Date endTime = new TableSelector(ti.getColumn("EndTime"), f, null).getObject(Date.class);
                    if (null != endTime)
                        break;
                }
            }

            assertTrue("Job did not finish", isDone(job));
        }

        private boolean isDone(PipelineJob job)
        {
            PipelineStatusFile statusFile = PipelineService.get().getStatusFile(job.getLogFile());
            if (statusFile == null)
            {
                return false;
            }
            for (PipelineJob.TaskStatus status : PipelineJob.TaskStatus.values())
            {
                if (status.matches(statusFile.getStatus()))
                {
                    return !status.isActive();
                }
            }
            return false;
        }


        private void verifyTaskPipeline(TaskPipeline p, TransformDescriptor d)
        {
            TaskId[] steps = p.getTaskProgression();

            // we should always have one more task than steps to account for the
            // XarGenerator task
            assertEquals(d._stepMetaDatas.size() + 1, steps.length);

            TaskId expStep = steps[steps.length - 1];
            assertEquals(XarGeneratorId.class, expStep.getNamespaceClass());
        }

        private TransformDescriptor checkValidSyntax(File file) throws XmlException, XmlValidationException, IOException
        {
            EtlResource etl = new EtlResource(file);
            return TransformManager.get().parseETLThrow(etl, module);
        }

        private void checkInvalidSyntax(File file, String expected) throws IOException
        {
            EtlResource etl = new EtlResource(file);
            try
            {
                TransformManager.get().parseETLThrow(etl, module);
                fail("Expected error: " + expected);
            }
            catch (XmlValidationException | XmlException x)
            {
                assertEquals(expected, x.getMessage());
            }
        }
    }
}
