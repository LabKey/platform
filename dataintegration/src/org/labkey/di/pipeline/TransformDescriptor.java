/*
 * Copyright (c) 2013 LabKey Corporation
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
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
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
import org.labkey.api.exp.pipeline.ExpGeneratorId;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.TaskPipelineSettings;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.di.DataIntegrationDbSchema;
import org.labkey.di.VariableMap;
import org.labkey.di.data.TransformDataType;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.steps.SimpleQueryTransformStep;
import org.labkey.di.steps.SimpleQueryTransformStepMeta;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.ScheduleBuilder;
import org.quartz.SimpleScheduleBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

    // schedule
    private final Long _interval;
    private final CronExpression _cron;

    // steps
    private final FilterStrategy.Factory _defaultFactory;
    private final ArrayList<SimpleQueryTransformStepMeta> _stepMetaDatas;


    public TransformDescriptor(String id, String name, String description, String moduleName, Long interval, CronExpression cron, FilterStrategy.Factory defaultFactory, ArrayList<SimpleQueryTransformStepMeta> stepMetaDatas) throws XmlException, IOException
    {
        _id = id;
        _name = name;
        _description = description;
        _moduleName = moduleName;
        _interval = interval;
        _cron = cron;
        _defaultFactory = defaultFactory;
        _stepMetaDatas = stepMetaDatas;
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


    public int getVersion()
    {
        // TODO - add config for real version number
        return 1;
    }


    public FilterStrategy.Factory getDefaultFilterFactory()
    {
        return _defaultFactory;
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
    public TransformJobContext getJobContext(Container c, User user)
    {
        return new TransformJobContext(this, c, user);
    }

    @Override
    public Callable<Boolean> getChecker(ScheduledPipelineJobContext context)
    {
        return new UpdatedRowsChecker(this, context, _stepMetaDatas);
    }


    @Override
    public PipelineJob getPipelineJob(ScheduledPipelineJobContext context) throws PipelineJobException
    {
        TransformPipelineJob job = new TransformPipelineJob((TransformJobContext)context, this);
        try
        {
            PipelineService.get().setStatus(job, PipelineJob.WAITING_STATUS, null, true);
        }
        catch (Exception e)
        {
            LOG.error("Unable to queue ETL job", e);
            return null;
        }

        TransformRun run = new TransformRun();
        run.setStartTime(new Date());
        run.setTransformId(getId());
        run.setTransformVersion(getVersion());
        run.setContainer(context.getContainer());

        PipelineStatusFile statusFile = PipelineService.get().getStatusFile(job.getLogFile());
        run.setJobId(statusFile.getRowId());

        try
        {
            run = Table.insert(context.getUser(), DataIntegrationDbSchema.getTransformRunTableInfo(), run);
        }
        catch (SQLException e)
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

        SimpleQueryTransformStepMeta meta = getTransformStepMetaFromTaskId(factory.getId());

        if (meta.getTaskClass().equals(TransformTask.class))
            return new SimpleQueryTransformStep(factory, job, meta, context);
        else
        if (meta.getTaskClass().equals(TestTask.class))
            return new TestTask(factory, job, meta);

        return null;

//        Class c = meta.getTargetStepClass();
//        try
//        {
//            PipelineJob.Task task = (PipelineJob.Task)c.getConstructor(meta.getClass()).newInstance(meta);
//            return task;
//        }
//        catch (NoSuchMethodException|InstantiationException|IllegalAccessException|InvocationTargetException|ClassCastException x)
//        {
//            throw new RuntimeException(x);
//        }
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


    private SimpleQueryTransformStepMeta getTransformStepMetaFromTaskId(TaskId tid)
    {
        // step ids are guaranteed to be unique
        for (SimpleQueryTransformStepMeta meta : _stepMetaDatas)
        {
            if (StringUtils.equals(meta.getId(), tid.getName()))
                return meta;
        }

        return null;
    }

    // get/build the task pipeline specific to this descriptor
    public TaskPipeline getTaskPipeline()
    {
        TaskId pipelineId = new TaskId(TransformPipelineJob.class, getId());
        TaskPipeline pipeline = PipelineJobService.get().getTaskPipeline(pipelineId);

        if (null == pipeline)
        {
            try
            {
                registerTaskPipeline(pipelineId);
                pipeline = PipelineJobService.get().getTaskPipeline(pipelineId);
            }
            catch(CloneNotSupportedException ignore) {}
        }

        return pipeline;
    }

    private void registerTaskPipeline(TaskId pipelineId) throws CloneNotSupportedException
    {
        ArrayList<Object> progressionSpec = new ArrayList<>();
        TaskPipelineSettings settings = new TaskPipelineSettings(pipelineId);

        // Register all the tasks that are associated with this transform and
        // associate the correct stepMetaData with the task via the index
        for (SimpleQueryTransformStepMeta meta : _stepMetaDatas)
        {
            String taskName = meta.getId();
            Class taskClass = meta.getTaskClass();
            // check to see if this class is part of our known transform tasks
            if (TransformTask.class.isAssignableFrom(taskClass))
            {
                PipelineJobService.get().addTaskFactory(new TransformTaskFactory(taskClass, taskName));
            }
            else
            {
                //
                // be sure to update if any new task factories are added
                //
                assert false;
                continue;
            }

            progressionSpec.add(new TaskId(taskClass, taskName));
        }

        // Register the task to generate an experiment run to track this transform as the last step.
        // The ExpGenerator factory should have already been registered by the Experiment module
        progressionSpec.add(new TaskId(ExpGeneratorId.class));

        // add the pipeline
        settings.setTaskProgressionSpec(progressionSpec.toArray());
        PipelineJobService.get().addTaskPipeline(settings);
    }

    public static class TestCase extends Assert
    {
        private static final String BASE_PATH = "sampledata/dataintegration/etls/";
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

        private File getFile(String file)
        {
            return new File(AppProps.getInstance().getProjectRoot() + "/" + BASE_PATH + file);
        }

        @Test
        public void descriptorSyntax() throws XmlException, IOException
        {
            //
            // does a sanity check on ETL config file parsing and verifies that invalid ETL configuration options are
            // correctly caught with the appropriate error messages
            //

            TransformDescriptor d;

            d = checkValidSyntax(getFile(ONE_TASK));
            assert d._stepMetaDatas.size() == 1;

            d = checkValidSyntax(getFile(FOUR_TASKS));
            assert d._stepMetaDatas.size() == 4;

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

            checkInvalidSyntax(getFile(NO_ID), TransformManager.ID_REQUIRED);
            checkInvalidSyntax(getFile(DUP_ID), TransformManager.DUPLICATE_ID);
            checkInvalidSyntax(getFile(NO_CLASS), TransformManager.TYPE_REQUIRED);
            checkInvalidSyntax(getFile(UNKNOWN_CLASS), TransformManager.INVALID_TYPE);
            checkInvalidSyntax(getFile(INVALID_CLASS), TransformManager.INVALID_TYPE);
            checkInvalidSyntax(getFile(BAD_SOURCE_OPT), TransformManager.INVALID_SOURCE_OPTION);
            checkInvalidSyntax(getFile(BAD_TARGET_OPT), TransformManager.INVALID_TARGET_OPTION);
        }

        @Test
        public void registerTaskPipeline() throws XmlException, IOException, CloneNotSupportedException
        {
            //
            // verifies that the correct task pipeline is setup for ONE_TASK and FOUR_TASK ETL configurations
            //

            TransformDescriptor d1 = TransformManager.get().parseETLThrow(new EtlResource(getFile(ONE_TASK)), module);
            TaskPipeline p1 = d1.getTaskPipeline();
            assert null != p1;

            // calling twice should be just fine
            p1 = d1.getTaskPipeline();
            assert null != p1;
            verifyTaskPipeline(p1, d1);

            TransformDescriptor d4 =  TransformManager.get().parseETLThrow(new EtlResource(getFile(FOUR_TASKS)), module);
            TaskPipeline p4 = d4.getTaskPipeline();
            assert null != p4;
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
            TransformJobContext context = new TransformJobContext(d, c, u);
            TransformPipelineJob job = (TransformPipelineJob) d.getPipelineJob(context);

            VariableMap map = job.getVariableMap();
            assert map.keySet().size() == 0;

            // add a transient variable (non-persisted) to the variable map
            // and ensure it doesn't show up in the protocol application properties
            map.put(TestTask.Transient, "don't persist!");

            if (failStep > 0)
            {
                // fail the step passed in; note that the index is 0 based
                map.put(TestTask.FailStep, d._stepMetaDatas.get(failStep).getId());
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
            TransformRun run = new TableSelector(DataIntegrationDbSchema.getTransformRunTableInfo(), new SimpleFilter(FieldKey.fromParts("TransformRunId"), runId), null).getObject(TransformRun.class);
            String status = run.getStatus();
            Integer expId = run.getExpRunId();
            Integer jobId = run.getJobId();

            assert null != run.getStartTime();
            assert null != run.getEndTime();
            assert null != jobId;
            assert StringUtils.equalsIgnoreCase(run.getTransformId(), d.getId());

            if (isSuccess)
            {
                // number of records affected == number of steps * total number of record operations in each step
                int numSteps = d._stepMetaDatas.size();
                int totalRecordCount = numSteps * (TestTask.recordsDeleted + TestTask.recordsInserted + TestTask.recordsModified);

                assert run.getRecordCount() == totalRecordCount;
                assert StringUtils.equalsIgnoreCase(status, PipelineJob.COMPLETE_STATUS);
                assert null != expId;
            }
            else
            {
                assert StringUtils.equalsIgnoreCase(status, PipelineJob.ERROR_STATUS);
                assert null == expId;
            }

            return run;
        }

        // check that we logged the transform run experiment correctly
        private void verifyTransformExp(TransformRun transformRun, TransformPipelineJob transformJob)
        {
            TransformDescriptor d = transformJob.getTransformDescriptor();
            ExpRun expRun = ExperimentService.get().getExpRun(transformRun.getExpRunId());

            //
            // verify run standard properties
            //
            assert expRun.getJobId().intValue() == transformRun.getJobId().intValue();
            assert expRun.getName().equalsIgnoreCase(TransformPipelineJob.ETL_PREFIX + d.getDescription());

            //
            // verify custom propeties
            //
            Map<String, ObjectProperty> mapProps = expRun.getObjectProperties();
            assert mapProps.size() == 1;
            ObjectProperty prop = mapProps.get(TransformProperty.RecordsInserted.getPropertyDescriptor().getPropertyURI());
            assert TestTask.recordsInsertedJob ==  prop.getFloatValue();

            // verify the variable map generated is correct from the TransformManagager helper function
            verifyVariableMap(TransformManager.get().getVariableMapForTransformJob(transformRun.getExpRunId()), mapProps);

            //
            // verify data inputs: test job has two source inputs
            //
            ExpData[] datas = expRun.getInputDatas(TransformTask.INPUT_ROLE, null);
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
            assert protocol.getName().equalsIgnoreCase(TransformPipelineJob.class.getName() + ":" + d.getId());
            List<ExpProtocolAction> actions = protocol.getSteps();
            // ignore input and output actions in count
            assert (actions.size() - 2) == d._stepMetaDatas.size();

            //
            // verify protocol applications:  we have two steps that map to this
            //
            ExpProtocolApplication[] apps = expRun.getProtocolApplications();
            assert (apps.length - 2) == d._stepMetaDatas.size();

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
            assert isValidStep(d, app.getName());

            List<ExpData> datas = app.getInputDatas();
            verifyDatas(d, datas.toArray(new ExpData[datas.size()]), 1, true);

            datas = app.getOutputDatas();
            verifyDatas(d, datas.toArray(new ExpData[datas.size()]), 1, false);

            // verify our step start/end times are within the bounds of the entire run start/end times
            assert(transformRun.getStartTime().getTime() <= app.getStartTime().getTime());
            assert(transformRun.getEndTime().getTime() >= app.getEndTime().getTime());
            // verify step recordcount is one step's worth of work
            assert(app.getRecordCount() == TestTask.recordsDeleted + TestTask.recordsInserted + TestTask.recordsModified);
            // verify we have object properties and they are found in the variable map
            Map<String, ObjectProperty> mapProps = app.getObjectProperties();

            // we should only have 3 custom properties for the test task
            assert mapProps.size() == 3;
            ObjectProperty prop = mapProps.get(TransformProperty.RecordsDeleted.getPropertyDescriptor().getPropertyURI());
            assert TestTask.recordsDeleted == prop.getFloatValue();
            prop = mapProps.get(TransformProperty.RecordsInserted.getPropertyDescriptor().getPropertyURI());
            assert TestTask.recordsInserted == prop.getFloatValue();
            prop = mapProps.get(TransformProperty.RecordsModified.getPropertyDescriptor().getPropertyURI());
            assert TestTask.recordsModified == prop.getFloatValue();

            // finally, verify that the VariableMap we build out of the protocol application properties is correct
            verifyVariableMap(TransformManager.get().getVariableMapForTransformStep(transformRun.getExpRunId(), app.getName()), mapProps);
       }

        private void verifyVariableMap(VariableMap varMap, Map<String, ObjectProperty> propMap)
        {
            assert varMap.keySet().size() == propMap.keySet().size();
            for (String key : propMap.keySet())
            {
                ObjectProperty p = propMap.get(key);
                assert p.getFloatValue() == ((Integer)varMap.get(p.getName())).doubleValue();
            }
        }

        private boolean isValidStep(TransformDescriptor d, String stepName)
        {
            for (SimpleQueryTransformStepMeta meta : d._stepMetaDatas)
            {
                if (stepName.equalsIgnoreCase(meta.getId()))
                    return true;
            }

            return false;
        }

        private void verifyDatas(TransformDescriptor d, ExpData[] datas, int expectedCount, boolean isInput)
        {
            assert datas.length == expectedCount;

            for (ExpData data : datas)
            {
                verifyData(d, data.getName(), isInput);
            }
        }

        // find the data name in the descriptor for this step
        private void verifyData(TransformDescriptor d, String dataName, boolean isInput)
        {
            boolean found = false;
            for(SimpleQueryTransformStepMeta meta : d._stepMetaDatas)
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

            assert found;
        }

        private void waitForJobToFinish(PipelineJob job) throws Exception
        {
            SimpleFilter f = new SimpleFilter();
            TransformPipelineJob tj = (TransformPipelineJob) job;
            TableInfo ti = DataIntegrationDbSchema.getTransformRunTableInfo();
            f.addCondition(new FieldKey(null, "transformrunid"), tj.getTransformRunId(), CompareType.EQUAL);

            for (int i = 0; i < NUM_TRIES; i++)
            {
                Thread.sleep(TRY_QUANTA);

                if (job.isDone())
                {
                    // wait for us to finish updating transformrun table before continuing
                    Date endTime = new TableSelector(ti.getColumn("EndTime"), f, null).getObject(Date.class);
                    if (null != endTime)
                        break;
                }
            }

            assert(job.isDone()) : "Job did not finish";
        }


        private void verifyTaskPipeline(TaskPipeline p, TransformDescriptor d)
        {
            TaskId[] steps = p.getTaskProgression();

            // we should always have one more task than steps to account for the
            // ExpGenerator task
            assert steps.length == d._stepMetaDatas.size() + 1;

            TaskId expStep = steps[steps.length - 1];
            assert expStep.getNamespaceClass() == ExpGeneratorId.class;
        }

        private TransformDescriptor checkValidSyntax(File file) throws XmlException, IOException
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
            }
            catch (XmlException x)
            {
                assert StringUtils.equalsIgnoreCase(x.getMessage(), expected);
            }
        }
    }
}
