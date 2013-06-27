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
import org.apache.xmlbeans.XmlOptions;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.ScheduledPipelineJobContext;
import org.labkey.api.di.ScheduledPipelineJobDescriptor;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.pipeline.ExpGeneratorId;
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
import org.labkey.api.query.SchemaKey;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.di.DataIntegrationDbSchema;
import org.labkey.di.VariableMap;
import org.labkey.di.data.TransformDataType;
import org.labkey.di.data.TransformProperty;
import org.labkey.di.filters.FilterStrategy;
import org.labkey.di.filters.ModifiedSinceFilterStrategy;
import org.labkey.di.filters.RunFilterStrategy;
import org.labkey.di.filters.SelectAllFilterStrategy;
import org.labkey.di.steps.SimpleQueryTransformStep;
import org.labkey.di.steps.SimpleQueryTransformStepMeta;
import org.labkey.etl.xml.EtlDocument;
import org.labkey.etl.xml.EtlType;
import org.labkey.etl.xml.FilterType;
import org.labkey.etl.xml.SchemaQueryType;
import org.labkey.etl.xml.TransformType;
import org.labkey.etl.xml.TransformsType;
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
import java.text.ParseException;
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

    /** How often to check if the definition has changed */
    private static final int UPDATE_CHECK_FREQUENCY = 2000;

    // errors
    private static final String INVALID_TYPE = "Invalid transform type specified";
    private static final String TYPE_REQUIRED = "Transform type attribute is required";
    private static final String ID_REQUIRED = "Id attribute is required";
    private static final String DUPLICATE_ID = "Id attribute must be unique for each Transform";
    private static final String INVALID_TARGET_OPTION = "Invaild targetOption attribute value specified";
    private static final String INVALID_SOURCE_OPTION = "Invaild sourceOption attribute value specified";

    private transient Resource _resource;
    private Path _resourcePath;
    private long _lastUpdateCheck;
    private long _lastModified;

    private String _id;
    private String _name;
    private String _description;
    private String _moduleName;

    // schedule
    private Long _interval = null;
    private CronExpression _cron = null;

    // steps
    private FilterStrategy.Factory _defaultFactory = null;
    private ArrayList<SimpleQueryTransformStepMeta> _stepMetaDatas = new ArrayList<>();
    private CaseInsensitiveHashSet _stepIds = new CaseInsensitiveHashSet();


    public TransformDescriptor(Resource resource, String moduleName) throws XmlException, IOException
    {
        _resource = resource;
        _resourcePath = resource.getPath();
        _moduleName = moduleName;
        String name;
        if ("config.xml".equals(_resourcePath.getName().toLowerCase()))
            name = _resourcePath.getParent().getName();
        else
            name = FileUtil.getBaseName(_resourcePath.getName());
        _id = "{" + moduleName + "}/" + name;
        parse();
    }


    private void parse() throws IOException, XmlException
    {
        InputStream inputStream = null;
        try
        {
            Resource resource = ensureResource();
            inputStream = resource.getInputStream();
            if (inputStream == null)
            {
                throw new IOException("Unable to get InputStream from " + resource);
            }
            _lastModified = resource.getLastModified();

            XmlOptions options = new XmlOptions();
            options.setValidateStrict();
            EtlDocument document = EtlDocument.Factory.parse(inputStream, options);
            EtlType etlXML = document.getEtl();

            _name = etlXML.getName();
            _description = etlXML.getDescription();

            // handle default transform
            FilterType ft = etlXML.getIncrementalFilter();
            if (null != ft)
                _defaultFactory = createFilterFactory(ft);
            if (null == _defaultFactory)
                _defaultFactory = new ModifiedSinceFilterStrategy.Factory(this, null);

            // schedule
            if (null != etlXML.getSchedule())
            {
                if (null != etlXML.getSchedule().getPoll())
                {
                    String s = etlXML.getSchedule().getPoll().getInterval();
                    if (StringUtils.isNumeric(s))
                        _interval = Long.parseLong(s) * 60 * 1000;
                    else
                        _interval = DateUtil.parseDuration(s);
                }
                else if (null != etlXML.getSchedule().getCron())
                {
                    try
                    {
                        _cron = new CronExpression(etlXML.getSchedule().getCron().getExpression());
                    }
                    catch (ParseException x)
                    {
                        throw new XmlException("Could not parse cron expression: " + etlXML.getSchedule().getCron().getExpression(), x);
                    }
                }
            }

            TransformsType transforms = etlXML.getTransforms();
            if (null != transforms)
            {
                TransformType[] transformTypes = transforms.getTransformArray();
                for (TransformType t : transformTypes)
                {
                    SimpleQueryTransformStepMeta meta = buildSimpleQueryTransformStepMeta(t);
                    _stepMetaDatas.add(meta);
                }
            }
        }
        finally
        {
            if (inputStream != null) { try { inputStream.close(); } catch (IOException ignored) {} }
        }

    }


    private FilterStrategy.Factory createFilterFactory(FilterType filterTypeXML)
    {
        String className = StringUtils.defaultString(filterTypeXML.getClassName(), ModifiedSinceFilterStrategy.class.getName());
        if (!className.contains("."))
            className = "org.labkey.di.filters." + className;
        if (className.equals(ModifiedSinceFilterStrategy.class.getName()))
            return new ModifiedSinceFilterStrategy.Factory(this, filterTypeXML);
        else if (className.equals(RunFilterStrategy.class.getName()))
            return new RunFilterStrategy.Factory(this, filterTypeXML);
        else if (className.equals(SelectAllFilterStrategy.class.getName()))
            return new SelectAllFilterStrategy.Factory();
        throw new IllegalArgumentException("Class is not a recognized filter strategy: " + className);
    }


    private SimpleQueryTransformStepMeta buildSimpleQueryTransformStepMeta(TransformType transformXML) throws XmlException
    {
        SimpleQueryTransformStepMeta meta = new SimpleQueryTransformStepMeta();

        if (null == transformXML.getId())
            throw new XmlException(ID_REQUIRED);

        if (_stepIds.contains(transformXML.getId()))
            throw new XmlException(DUPLICATE_ID);

        _stepIds.add(transformXML.getId());
        meta.setId(transformXML.getId());

        if (null != transformXML.getDescription())
        {
            meta.setDescription(transformXML.getDescription());
        }

        String className = transformXML.getType();
        if (null == className)
        {
            className = TransformTask.class.getName();
        }

        try
        {
            Class taskClass = Class.forName(className);
            if (isValidTaskClass(taskClass))
            {
                meta.setTaskClass(taskClass);
            }
            else
            {
                throw new XmlException(INVALID_TYPE);
            }
        }
        catch (ClassNotFoundException e)
        {
            throw new XmlException(INVALID_TYPE);
        }

        SchemaQueryType source = transformXML.getSource();
        if (null != source)
        {
            meta.setSourceSchema(SchemaKey.fromString(source.getSchemaName()));
            meta.setSourceQuery(source.getQueryName());
            if (null != source.getTimestampColumnName())
                meta.setSourceTimestampColumnName(source.getTimestampColumnName());
            if (null != source.getSourceOption())
            {
                try
                {
                    meta.setSourceOptions(CopyConfig.SourceOptions.valueOf(source.getSourceOption()));
                }
                catch (IllegalArgumentException x)
                {
                    throw new XmlException(INVALID_SOURCE_OPTION);
                }
            }
        }
        SchemaQueryType destination = transformXML.getDestination();
        if (null != destination)
        {
            meta.setTargetSchema(SchemaKey.fromString(destination.getSchemaName()));
            meta.setTargetQuery(destination.getQueryName());
            if (null != destination.getTargetOption())
            {
                try
                {
                    meta.setTargetOptions(CopyConfig.TargetOptions.valueOf(destination.getTargetOption()));
                }
                catch (IllegalArgumentException x)
                {
                    throw new XmlException(INVALID_TARGET_OPTION);
                }
            }
        }

        return meta;
    }

    private Resource ensureResource()
    {
        if (_resource == null)
        {
            _resource = ModuleLoader.getInstance().getResource(_resourcePath);
            if (_resource == null)
            {
                throw new IllegalStateException("Could not resolve resource for " + _resourcePath + ", perhaps the ETL descriptor is no longer available?");
            }
        }
        return _resource;
    }

    public String getName()
    {
        checkForUpdates();
        return _name;
    }

    public String getDescription()
    {
        checkForUpdates();
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
        checkForUpdates();
        // TODO - add config for real version number
        return 1;
    }


    public FilterStrategy.Factory getDefaultFilterFactory()
    {
        return _defaultFactory;
    }


//    public void setDefaultFilterFactory(FilterStrategy.Factory defaultFactory)
//    {
//        _defaultFactory = defaultFactory;
//    }


    private void checkForUpdates()
    {
        long currentTime = System.currentTimeMillis();
        if (_lastUpdateCheck + UPDATE_CHECK_FREQUENCY < currentTime)
        {
            _lastUpdateCheck = currentTime;
            if (_lastModified != ensureResource().getLastModified())
            {
                // XML has changed, time to reload
                try
                {
                    parse();
                }
                catch (IOException e)
                {
                    LOG.warn("Unable to parse " + ensureResource(), e);
                }
                catch (XmlException e)
                {
                    LOG.warn("Unable to parse " + ensureResource(), e);
                }
            }
        }
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
        for (int i = 0; i < _stepMetaDatas.size(); i++)
        {
            SimpleQueryTransformStepMeta meta = _stepMetaDatas.get(i);
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
        for (int i = 0; i < _stepMetaDatas.size(); i++)
        {
            SimpleQueryTransformStepMeta meta = _stepMetaDatas.get(i);
            String taskName = meta.getId();
            Class taskClass = meta.getTaskClass();
            // check to see if this class is part of our known transform tasks
            if (org.labkey.di.pipeline.TransformTask.class.isAssignableFrom(taskClass))
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

    private boolean isValidTaskClass(Class taskClass)
    {
        if (org.labkey.di.pipeline.TransformTask.class.isAssignableFrom(taskClass))
            return true;

        return false;
    }

    public static class TestCase extends Assert
    {
        String BASE_PATH = "sampledata/dataintegration/etls/";
        String ONE_TASK = "one.xml";
        String UNIT_TASKS = "unit.xml";
        String FOUR_TASKS = "four.xml";
        String NO_ID = "noid.xml";
        String DUP_ID = "duplicate.xml";
        String UNKNOWN_CLASS = "unknown.xml";
        String INVALID_CLASS = "invalid.xml";
        String NO_CLASS = "noclass.xml";
        String BAD_SOURCE_OPT = "badsourceopt.xml";
        String BAD_TARGET_OPT = "badtargetopt.xml";
        int TRY_QUANTA = 100; // ms
        int NUM_TRIES = 100; // retry for a maximum of 10 seconds

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

            checkInvalidSyntax(getFile(NO_ID), TransformDescriptor.ID_REQUIRED);
            checkInvalidSyntax(getFile(DUP_ID), TransformDescriptor.DUPLICATE_ID);
            checkInvalidSyntax(getFile(NO_CLASS), TransformDescriptor.TYPE_REQUIRED);
            checkInvalidSyntax(getFile(UNKNOWN_CLASS), TransformDescriptor.INVALID_TYPE);
            checkInvalidSyntax(getFile(INVALID_CLASS), TransformDescriptor.INVALID_TYPE);
            checkInvalidSyntax(getFile(BAD_SOURCE_OPT), TransformDescriptor.INVALID_SOURCE_OPTION);
            checkInvalidSyntax(getFile(BAD_TARGET_OPT), TransformDescriptor.INVALID_TARGET_OPTION);
        }

        @Test
        public void registerTaskPipeline() throws XmlException, IOException, CloneNotSupportedException
        {
            //
            // verifies that the correct task pipeline is setup for ONE_TASK and FOUR_TASK ETL configurations
            //

            TransformDescriptor d1 = new TransformDescriptor(new EtlResource(getFile(ONE_TASK)), "junit");
            TaskPipeline p1 = d1.getTaskPipeline();
            assert null != p1;

            // calling twice should be just fine
            p1 = d1.getTaskPipeline();
            assert null != p1;
            verifyTaskPipeline(p1, d1);

            TransformDescriptor d4 = new TransformDescriptor(new EtlResource(getFile(FOUR_TASKS)), "junit");
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
            TransformDescriptor d = new TransformDescriptor(new EtlResource(getFile(UNIT_TASKS)), "junit");
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
            verifyDatas(d, datas.toArray(new ExpData[0]), 1, true);

            datas = app.getOutputDatas();
            verifyDatas(d, datas.toArray(new ExpData[0]), 1, false);

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

            assert(job.isDone());
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
            return new TransformDescriptor(etl, "junit");
        }

        private void checkInvalidSyntax(File file, String expected) throws IOException
        {
            EtlResource etl = new EtlResource(file);
            try
            {
                new TransformDescriptor(etl, "junit");
            }
            catch (XmlException x)
            {
                assert StringUtils.equalsIgnoreCase(x.getMessage(), expected);
            }
        }
    }
}
