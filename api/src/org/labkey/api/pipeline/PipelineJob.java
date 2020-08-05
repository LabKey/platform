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
package org.labkey.api.pipeline;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Job;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.PrintWriters;
import org.quartz.CronExpression;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A job represents the invocation of a pipeline on a certain set out inputs. It can be monolithic (a single run() method)
 * or be comprised of multiple tasks ({@link Task}) that can be checkpointed and restarted individually.
 */
abstract public class PipelineJob extends Job implements Serializable
{
    public static final FileType FT_LOG = new FileType(Arrays.asList(".log"), ".log", Arrays.asList("text/plain"));

    public static final String PIPELINE_EMAIL_ADDRESS_PARAM = "pipeline, email address";
    public static final String PIPELINE_USERNAME_PARAM = "pipeline, username";
    public static final String PIPELINE_PROTOCOL_NAME_PARAM = "pipeline, protocol name";
    public static final String PIPELINE_PROTOCOL_DESCRIPTION_PARAM = "pipeline, protocol description";
    public static final String PIPELINE_LOAD_FOLDER_PARAM = "pipeline, load folder";
    public static final String PIPELINE_JOB_INFO_PARAM = "pipeline, jobInfo";
    public static final String PIPELINE_TASK_INFO_PARAM = "pipeline, taskInfo";
    public static final String PIPELINE_TASK_OUTPUT_PARAMS_PARAM = "pipeline, taskOutputParams";

    protected static Logger _log = LogManager.getLogger(PipelineJob.class);
    // Send start/stop messages to a separate logger because the default logger for this class is set to
    // only write ERROR level events to the system log
    private static final Logger _logJobStopStart = LogManager.getLogger(Job.class);

    public static Logger getJobLogger(Class clazz)
    {
        return LogManager.getLogger(PipelineJob.class.getName() + ".." + clazz.getName());
    }

    public RecordedActionSet getActionSet()
    {
        return _actionSet;
    }

    /**
     * Clear out the set of recorded actions
     * @param run run that represents the previous set of recorded actions
     */
    public void clearActionSet(ExpRun run)
    {
        _actionSet = new RecordedActionSet();
    }

    public enum TaskStatus
    {
        /** Job is in the queue, waiting for its turn to run */
        waiting
        {
            @Override
            public boolean isActive() { return true; }

            @Override
            public boolean matches(String statusText)
            {
                if (statusText == null)
                    return false;
                else if (!TaskStatus.splitWaiting.matches(statusText) && statusText.toLowerCase().endsWith("waiting"))
                    return true;
                return super.matches(statusText);
            }
        },
        /** Job is doing its work */
        running
        {
            @Override
            public boolean isActive() { return true; }
        },
        /** Terminal state, job is finished and completed without errors */
        complete
        {
            @Override
            public boolean isActive() { return false; }
        },
        /** Terminal state (but often retryable), job is done running and completed with error(s) */
        error
        {
            @Override
            public boolean isActive() { return false; }
        },
        /** Job is in the process of being cancelled, but may still be running or queued at the moment */
        cancelling
        {
            @Override
            public boolean isActive() { return true; }
        },
        /** Terminal state, indicating that a user cancelled the job before it completed or errored */
        cancelled
        {
            @Override
            public boolean isActive() { return false; }
        },
        waitingForFiles
        {
            @Override
            public boolean isActive() { return false; }

            @Override
            public String toString() { return "WAITING FOR FILES"; }
        },
        splitWaiting
        {
            @Override
            public boolean isActive() { return false; }

            @Override
            public String toString() { return "SPLIT WAITING"; }
        };

        /** @return whether this step is considered to be actively running */
        public abstract boolean isActive();

        public String toString()
        {
            return super.toString().toUpperCase();
        }

        public boolean matches(String statusText)
        {
            return toString().equalsIgnoreCase(statusText);
        }

        public final String getNotificationType()
        {
            return getClass().getName() + "." + name();
        }
    }
    
    /**
     * Implements a runnable to complete a part of the
     * processing associated with a particular <code>PipelineJob</code>. This is often the execution of an external tool,
     * the importing of files into the database, etc.
     */
    abstract static public class Task<FactoryType extends TaskFactory>
    {
        private final PipelineJob _job;
        protected FactoryType _factory;

        public Task(FactoryType factory, PipelineJob job)
        {
            _job = job;
            _factory = factory;
        }

        public PipelineJob getJob()
        {
            return _job;
        }

        /**
         * Do the work of the task.
         * @return the files used as inputs and generated as outputs, and the steps that operated on them
         */
        @NotNull
        public abstract RecordedActionSet run() throws PipelineJobException;
    }

    /*
     * JMS message header names
     */
    private static final String HEADER_PREFIX = "LABKEY_";
    public static final String LABKEY_JOBTYPE_PROPERTY = HEADER_PREFIX + "JOBTYPE";
    public static final String LABKEY_JOBID_PROPERTY = HEADER_PREFIX + "JOBID";
    public static final String LABKEY_CONTAINERID_PROPERTY = HEADER_PREFIX + "CONTAINERID";
    public static final String LABKEY_TASKPIPELINE_PROPERTY = HEADER_PREFIX + "TASKPIPELINE";
    public static final String LABKEY_TASKID_PROPERTY = HEADER_PREFIX + "TASKID";
    public static final String LABKEY_TASKSTATUS_PROPERTY = HEADER_PREFIX + "TASKSTATUS";
    /** The execution location to which the job's current task is assigned */
    public static final String LABKEY_LOCATION_PROPERTY = HEADER_PREFIX + "LOCATION";

    private String _provider;
    private ViewBackgroundInfo _info;
    private String _jobGUID;
    private String _parentGUID;
    private TaskId _activeTaskId;
    @NotNull
    private TaskStatus _activeTaskStatus;
    private int _activeTaskRetries;
    @NotNull
    private PipeRoot _pipeRoot;
    private String _logFilePathName;        // .nio.Path as String; Path does not serialize well.
    volatile private boolean _interrupted;
    private boolean _submitted;
    private int _errors;
    private RecordedActionSet _actionSet = new RecordedActionSet();

    private String _loggerLevel = Level.DEBUG.toString();

    // Don't save these
    protected transient Logger _logger;
    private transient boolean _settingStatus;
    private transient PipelineQueue _queue;

    private File _logFile;
    private LocalDirectory _localDirectory;

    // Default constructor for serialization
    protected PipelineJob()
    {
    }

    /** Although having a null provider is legal, it is recommended that one be used
     * so that it can respond to events as needed */ 
    public PipelineJob(@Nullable String provider, ViewBackgroundInfo info, @NotNull PipeRoot root)
    {
        _info = info;
        _provider = provider;
        _jobGUID = GUID.makeGUID();
        _activeTaskStatus = TaskStatus.waiting;

        _pipeRoot = root;

        _actionSet = new RecordedActionSet();
    }

    public PipelineJob(PipelineJob job)
    {
        // Not yet queued
        _queue = null;

        // New ID
        _jobGUID = GUID.makeGUID();

        // Copy everything else
        _info = job._info;
        _provider = job._provider;
        _parentGUID = job._jobGUID;
        _pipeRoot = job._pipeRoot;
        _logFilePathName = job._logFilePathName;
        _interrupted = job._interrupted;
        _submitted = job._submitted;
        _errors = job._errors;
        _loggerLevel = job._loggerLevel;
        _logger = job._logger;
        _logFile = job._logFile;

        _activeTaskId = job._activeTaskId;
        _activeTaskStatus = job._activeTaskStatus;

        _actionSet = new RecordedActionSet(job.getActionSet());
        _localDirectory = job._localDirectory;
    }

    public String getProvider()
    {
        return _provider;
    }

    @Deprecated
    public void setProvider(String provider)
    {
        _provider = provider;
    }

    public int getErrors()
    {
        return _errors;
    }

    public void setErrors(int errors)
    {
        if (errors > 0)
            _activeTaskStatus = TaskStatus.error;
        
        _errors = errors;
    }

    /**
     * This job has been restored from a checkpoint for the purpose of
     * a retry.  Record retry information before it is checkpointed again.
     */
    public void retryUpdate()
    {
        _errors++;
        _activeTaskRetries++;
    }

    public Map<String, String> getParameters()
    {
        return Collections.emptyMap();
    }

    public String getJobGUID()
    {
        return _jobGUID;
    }

    public String getParentGUID()
    {
        return _parentGUID;
    }

    @Nullable
    public TaskId getActiveTaskId()
    {
        return _activeTaskId;
    }

    public boolean setActiveTaskId(@Nullable TaskId activeTaskId)
    {
        return setActiveTaskId(activeTaskId, true);
    }
    
    public boolean setActiveTaskId(@Nullable TaskId activeTaskId, boolean updateStatus)
    {
        if (activeTaskId == null || !activeTaskId.equals(_activeTaskId))
        {
            _activeTaskId = activeTaskId;
            _activeTaskRetries = 0;
        }
        if (_activeTaskId == null)
            _activeTaskStatus = TaskStatus.complete;
        else
            _activeTaskStatus = TaskStatus.waiting;

        return !updateStatus || updateStatusForTask();
    }

    @NotNull
    public TaskStatus getActiveTaskStatus()
    {
        return _activeTaskStatus;
    }

    /** @return whether or not the status was set successfully */
    public boolean setActiveTaskStatus(@NotNull TaskStatus activeTaskStatus)
    {
        _activeTaskStatus = activeTaskStatus;
        return updateStatusForTask();
    }

    public TaskFactory getActiveTaskFactory()
    {
        if (getActiveTaskId() == null)
            return null;
        
        return PipelineJobService.get().getTaskFactory(getActiveTaskId());
    }

    @NotNull
    public PipeRoot getPipeRoot()
    {
        return _pipeRoot;
    }

    public void setLogFilePath(Path logFile)
    {
        _logFilePathName = FileUtil.pathToString(logFile);
//        _logJobStopStart.info("LogFilePath set to " + _logFilePathName);
        _logger = null;
        _logFile = null;

        // Intentionally leave any existing log output in the file
    }

    public void setLogFile(File logFile)
    {
        setLogFilePath(logFile.toPath());
        _logFile = logFile;
    }

    public File getLogFile()
    {
        Path logFilePath =  getLogFilePath();
        if (null != logFilePath && !FileUtil.hasCloudScheme(logFilePath))
            return logFilePath.toFile();
        return null;
    }

    public Path getLogFilePath()
    {
        if (_logFilePathName == null)
            return null;

        return FileUtil.hasCloudScheme(_logFilePathName) ? FileUtil.stringToPath(getContainer(), _logFilePathName) : new File(FileUtil.createUri(_logFilePathName)).toPath();
    }

    public LocalDirectory getLocalDirectory()
    {
        return _localDirectory;
    }

    protected void setLocalDirectory(LocalDirectory localDirectory)
    {
        _localDirectory = localDirectory;
    }

    public static PipelineJob readFromFile(File file) throws IOException, PipelineJobException
    {
        StringBuilder serializedJob = new StringBuilder();
        try (InputStream fIn = new FileInputStream(file))
        {
            BufferedReader reader = Readers.getReader(fIn);
            String line;
            while ((line = reader.readLine()) != null)
            {
                serializedJob.append(line);
            }
        }

        PipelineJob job = PipelineJob.deserializeJob(serializedJob.toString());
        if (null == job)
        {
            throw new PipelineJobException("Unable to deserialize job");
        }
        return job;
    }


    public void writeToFile(File file) throws IOException
    {
        File newFile = new File(file.getPath() + ".new");
        File origFile = new File(file.getPath() + ".orig");

        String serializedJob = PipelineJob.serializeJob(this, true);

        try (FileOutputStream fOut = new FileOutputStream(newFile))
        {
            PrintWriter writer = PrintWriters.getPrintWriter(fOut);
            writer.write(serializedJob);
            writer.flush();
        }

        if (NetworkDrive.exists(file))
        {
            if (origFile.exists())
            {
                // Might be left over from some bad previous run
                origFile.delete();
            }
            // Don't use File.renameTo() because it doesn't always work depending on the underlying file system
            FileUtils.moveFile(file, origFile);
            FileUtils.moveFile(newFile, file);
            origFile.delete();
        }
        else
        {
            FileUtils.moveFile(newFile, file);
        }
        PipelineJobService.get().getWorkDirFactory().setPermissions(file);
    }

    public boolean updateStatusForTask()
    {
        TaskFactory factory = getActiveTaskFactory();
        TaskStatus status = getActiveTaskStatus();

        if (factory != null && !TaskStatus.error.equals(status) && !TaskStatus.cancelled.equals(status))
            return setStatus(factory.getStatusName() + " " + status.toString().toUpperCase());
        else
            return setStatus(status);
    }

    /** Used for setting status to one of the standard states */
    public boolean setStatus(@NotNull TaskStatus status)
    {
        return setStatus(status.toString());
    }

    /**
     * Used for setting status to a custom state, which is considered to be equivalent to TaskStatus.running
     * unless it matches one of the standard states
     * @throws CancelledException if the job was cancelled by a user and should stop execution
     */
    public boolean setStatus(@NotNull String status)
    {
        return setStatus(status, null);
    }

    /**
     * Used for setting status to one of the standard states
     * @param info more verbose detail on the job's status, such as a percent complete
     * @throws CancelledException if the job was cancelled by a user and should stop execution
     */
    public boolean setStatus(@NotNull TaskStatus status, @Nullable String info)
    {
        return setStatus(status.toString(), info);
    }

    /**
     * @param info more verbose detail on the job's status, such as a percent complete
     * @throws CancelledException if the job was cancelled by a user and should stop execution
     */
    public boolean setStatus(@NotNull String status, @Nullable String info)
    {
        return setStatus(status, info, false);
    }

    /**
     * Used for setting status to a custom state, which is considered to be equivalent to TaskStatus.running
     * unless it matches one of the standard states
     * @throws CancelledException if the job was cancelled by a user and should stop execution
     */
    public boolean setStatus(@NotNull String status, @Nullable String info, boolean allowInsert)
    {
        if (_settingStatus)
            return true;
        
        _settingStatus = true;
        try
        {
            boolean statusSet = PipelineJobService.get().getStatusWriter().setStatus(this, status, info, allowInsert);
            if (!statusSet)
            {
                setActiveTaskStatus(TaskStatus.error);
            }
            return statusSet;
        }
        // Rethrow so it doesn't get handled like other RuntimeExceptions
        catch (CancelledException e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            File f = this.getLogFile();
            error("Failed to set status to '" + status + "' for '" +
                    (f == null ? "" : f.getPath()) + "'.", e);
            throw e;
        }
        catch (Exception e)
        {
            File f = this.getLogFile();
            error("Failed to set status to '" + status + "' for '" +
                    (f == null ? "" : f.getPath()) + "'.", e);
        }
        finally
        {
            _settingStatus = false;
        }
        return false;
    }

    public void restoreQueue(PipelineQueue queue)
    {
        // Recursive split and join combinations may cause the queue
        // to be restored to a job with a queue already.  Would be good
        // to have better safe-guards against double-queueing of jobs.
        if (queue == _queue)
            return;
        if (null != _queue)
            throw new IllegalStateException();
        _queue = queue;
    }

    public void restoreLocalDirectory()
    {
        if (null != _localDirectory)
            setLogFile(_localDirectory.restore());
    }

    public void validateParameters() throws PipelineValidationException
    {
        TaskPipeline taskPipeline = getTaskPipeline();
        if (taskPipeline != null)
        {
            for (TaskId taskId : taskPipeline.getTaskProgression())
            {
                TaskFactory taskFactory = PipelineJobService.get().getTaskFactory(taskId);
                if (taskFactory == null)
                    throw new PipelineValidationException("Task '" + taskId + "' not found");
                taskFactory.validateParameters(this);
            }
        }
    }

    public boolean setQueue(PipelineQueue queue, TaskStatus initialState)
    {
        return setQueue(queue, initialState.toString());
    }

    public boolean setQueue(PipelineQueue queue, String initialState)
    {
        restoreQueue(queue);
        
        // Initialize the task pipeline
        TaskPipeline taskPipeline = getTaskPipeline();
        if (taskPipeline != null)
        {
            // Save the current job state marshalled to XML, in case of error.
            String serializedJob = PipelineJob.serializeJob(this, true);

            // Note runStateMachine returns false, if the job cannot be run locally.
            // The job may still need to be put on a JMS queue for remote processing.
            // Therefore, the return value cannot be used to determine whether the
            // job should be queued.
            runStateMachine();

            // If an error occurred trying to find the first runnable state, then
            // store the original job state to allow retry.
            if (getActiveTaskStatus() == TaskStatus.error)
            {
                try
                {
                    PipelineJob originalJob = PipelineJob.deserializeJob(serializedJob);
                    if (null != originalJob)
                        originalJob.store();
                    else
                        warn("Failed to checkpoint '" + getDescription() + "' job.");

                }
                catch (Exception e)
                {
                    warn("Failed to checkpoint '" + getDescription() + "' job.", e);
                }
                return false;
            }

            // If initialization put this job into a state where it is
            // waiting, then it should not be put on the queue.
            return !isSplitWaiting();
        }
        // Initialize status for non-task pipeline jobs.
        else if (_logFilePathName != null)
        {
            setStatus(initialState);
            try
            {
                store();
            }
            catch (Exception e)
            {
                warn("Failed to checkpoint '" + getDescription() + "' job before queuing.", e);
            }
        }

        return true;
    }

    public void clearQueue()
    {
        _queue = null;
    }

    abstract public URLHelper getStatusHref();

    abstract public String getDescription();

    public String toString()
    {
        return super.toString() + " " + StringUtils.trimToEmpty(getDescription());
    }

    public <T> T getJobSupport(Class<T> inter)
    {
        if (inter.isInstance(this))
            return (T) this;
        
        throw new UnsupportedOperationException("Job type " + getClass().getName() +
                " does not implement " + inter.getName());
    }

    /**
     * Override to provide a <code>TaskPipeline</code> with the option of
     * running some tasks remotely. Override the <code>run()</code> function
     * to implement the job as a single monolithic task.
     *
     * @return a task pipeline to run for this job
     */
    @Nullable
    public TaskPipeline getTaskPipeline()
    {
        return null;
    }

    public boolean isActiveTaskLocal()
    {
        TaskFactory factory = getActiveTaskFactory();
        return (factory != null &&
                TaskFactory.WEBSERVER.equalsIgnoreCase(factory.getExecutionLocation()));
    }

    public void runActiveTask() throws IOException, PipelineJobException
    {
        TaskFactory factory = getActiveTaskFactory();
        if (factory == null)
            return;

        if (!factory.isJobComplete(this))
        {
            Task<?> task = factory.createTask(this);
            if (task == null)
                return; // Bad task key.

            if (!setActiveTaskStatus(TaskStatus.running))
            {
                // The user has deleted (cancelled) the job.
                // Throwing this exception will cause the job to go to the ERROR state and stop running
                throw new PipelineJobException("Job no longer in database - aborting");
            }

            WorkDirectory workDirectory = null;
            RecordedActionSet actions;

            boolean success = false;
            try
            {
                logStartStopInfo("Starting to run task '" + factory.getId() + "' for job '" + toString() + "' with log file " + getLogFile());
                getLogger().info("Starting to run task '" + factory.getId() + "' at location '" + factory.getExecutionLocation() + "'");
                if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
                {
                    PipelineJobService.RemoteServerProperties remoteProps = PipelineJobService.get().getRemoteServerProperties();
                    if (remoteProps != null)
                    {
                        getLogger().info("on host: '" + remoteProps.getHostName() + "'");
                    }
                }

                if (task instanceof WorkDirectoryTask)
                {
                    workDirectory = factory.createWorkDirectory(getJobGUID(), getJobSupport(FileAnalysisJobSupport.class), getLogger());
                    ((WorkDirectoryTask)task).setWorkDirectory(workDirectory);
                }
                actions = task.run();
                success = true;
            }
            finally
            {
                getLogger().info((success ? "Successfully completed" : "Failed to complete") + " task '" + factory.getId() + "'");
                logStartStopInfo((success ? "Successfully completed" : "Failed to complete") + " task '" + factory.getId() + "' for job '" + toString() + "' with log file " + getLogFile());

                try
                {
                    if (workDirectory != null)
                    {
                        workDirectory.remove(success);
                        ((WorkDirectoryTask)task).setWorkDirectory(null);
                    }
                }
                catch (IOException e)
                {
                    // Don't let this cleanup error mask an original error that causes the job to fail
                    if (success)
                    {
                        // noinspection ThrowFromFinallyBlock
                        throw e;
                    }
                    else
                    {
                        if (e.getMessage() != null)
                        {
                            error(e.getMessage());
                        }
                        else
                        {
                            error("Failed to clean up work directory after error condition, see full error information below.", e);
                        }
                    }
                }
            }
            _actionSet.add(actions);

            // An error occurred running the task. Do not complete.
            if (TaskStatus.error.equals(getActiveTaskStatus()))
                return;
        }
        else
        {
            logStartStopInfo("Skipping already completed task '" + factory.getId() + "' for job '" + toString() + "' with log file " + getLogFile());
            getLogger().info("Skipping already completed task '" + factory.getId() + "' at location '" + factory.getExecutionLocation() + "'");
        }

        setActiveTaskStatus(TaskStatus.complete);
    }

    public static void logStartStopInfo(String message)
    {
        _logJobStopStart.info(message);
    }

    public boolean runStateMachine()
    {
        TaskPipeline pipeline = getTaskPipeline();

        if (pipeline == null)
        {
            assert false : "Either override getTaskPipeline() or run() for " + getClass();

            // Best we can do is to complete the job.
            setActiveTaskId(null);
            return false;
        }

        TaskId[] progression = pipeline.getTaskProgression();
        int i = 0;
        if (_activeTaskId != null)
        {
            i = indexOfActiveTask(progression);
            if (i == -1)
            {
                error("Active task " + _activeTaskId + " not found in task pipeline.");
                return false;                
            }
        }

        switch (_activeTaskStatus)
        {
            case waiting:
                return findRunnableTask(progression, i);

            case complete:
                // See if the job has already completed.
                if (_activeTaskId == null)
                    return false;
                
                return findRunnableTask(progression, i + 1);

            case error:
                // Make sure the status is in error state, so that any auto-retry that
                // may occur will record the error.  And, if no retry occurs, then this
                // job must be in error state.
                try
                {
                    PipelineJobService.get().getStatusWriter().ensureError(this);
                }
                catch (Exception e)
                {
                    warn("Failed to ensure error status on task error.", e);
                }

                // Run auto-retry, and retry if appropriate.
                autoRetry();
                return false;
            
            case running:
            case cancelled:
            case cancelling:
            default:
                return false;   // Do not run the active task.
        }
    }

    private int indexOfActiveTask(TaskId[] progression)
    {
        for (int i = 0; i < progression.length; i++)
        {
            TaskFactory factory = PipelineJobService.get().getTaskFactory(progression[i]);
            if (factory.getId().equals(_activeTaskId) ||
                    factory.getActiveId(this).equals(_activeTaskId))
                return i;
        }
        return -1;
    }

    private boolean findRunnableTask(TaskId[] progression, int i)
    {
        // Search for next task that is not already complete
        TaskFactory factory = null;
        while (i < progression.length)
        {
            try
            {
                factory = PipelineJobService.get().getTaskFactory(progression[i]);
                // Stop, if this task requires a change in join state
                if ((factory.isJoin() && isSplitJob()) || (!factory.isJoin() && isSplittable()))
                    break;
                // Stop, if this task is part of processing this job, and not complete
                if (factory.isParticipant(this) && !factory.isJobComplete(this))
                    break;
            }
            catch (IOException e)
            {
                error(e.getMessage());
                return false;
            }

            i++;
        }

        if (i < progression.length)
        {
            assert factory != null : "Factory not found.";

            if (factory.isJoin() && isSplitJob())
            {
                setActiveTaskId(factory.getId(), false);   // ID is just a marker for state machine
                join();
                return false;
            }
            else if (!factory.isJoin() && isSplittable())
            {
                setActiveTaskId(factory.getId(), false);   // ID is just a marker for state machine
                split();
                return false;
            }

            // Set next task to be run
            if (!setActiveTaskId(factory.getActiveId(this)))
            {
                return false;
            }

            // If it is local, then it can be run
            return isActiveTaskLocal();
        }
        else
        {
            // Job is complete
            if (isSplitJob())
            {
                setActiveTaskId(null, false);
                join();
            }
            else
            {
                setActiveTaskId(null);
            }
            return false;
        }
    }

    public boolean isAutoRetry()
    {
        TaskFactory factory = getActiveTaskFactory();
        return null != factory && _activeTaskRetries < factory.getAutoRetry() && factory.isAutoRetryEnabled(this);
    }

    public boolean autoRetry()
    {
        try
        {
            if (isAutoRetry())
            {
                info("Attempting to auto-retry");
                PipelineJobService.get().getJobStore().retry(getJobGUID());
                // Retry has been queued
                return true;
            }
        }
        catch (IOException | NoSuchJobException e)
        {
            warn("Failed to start automatic retry.", e);
        }
        return false;
    }

    @Override
    public void run()
    {
        try
        {
            // The act of queueing the job runs the state machine for the first time.
            do
            {
                try
                {
                    runActiveTask();
                }
                catch (IOException | PipelineJobException e)
                {
                    error(e.getMessage(), e);
                }
                catch (CancelledException e)
                {
                    throw e;
                }
                catch (RuntimeException e)
                {
                    error(e.getMessage(), e);
                    ExceptionUtil.logExceptionToMothership(null, e);
                    // Rethrow to let the standard Mule exception handler fire and deal with the job state
                    throw e;
                }
            }
            while (runStateMachine());
        }
        catch (CancelledException e)
        {
            _activeTaskStatus = TaskStatus.cancelled;
            // Don't need to do anything else, job has already been set to CANCELLED
        }
        finally
        {
            finallyCleanUpLocalDirectory();
        }
    }

    // Should be called in run()'s finally by any class that overrides run(), if class uses LocalDirectory
    protected void finallyCleanUpLocalDirectory()
    {
        if (null != _localDirectory)
        {
            try
            {
                Path remoteLogFilePath = _localDirectory.cleanUpLocalDirectory();
                if (null != remoteLogFilePath)
                {
                    setLogFilePath(remoteLogFilePath);
                    setStatus(getActiveTaskStatus());       // Force writing to statusFiles
                }
            }
            catch (Exception e)
            {
                error("Error trying to move log file", e);
            }
        }
    }

    /**
     * Override and return true for job that may be split.  Also, override
     * the <code>createSplitJobs()</code> method to return the sub-jobs.
     *
     * @return true if the job may be split
     */
    public boolean isSplittable()
    {
        return false;
    }

    /**
     * @return true if this is a split job, as determined by whether it has a parent.
     */
    public boolean isSplitJob()
    {
        return getParentGUID() != null;
    }

    /**
     * @return true if this is a join job waiting for split jobs to complete.
     */
    public boolean isSplitWaiting()
    {
        // Return false, if this job cannot be split.
        if (!isSplittable())
            return false;

        // A join job with an active task that is not a join task,
        // is waiting for a split to complete.
        TaskFactory factory = getActiveTaskFactory();
        return (factory != null && !factory.isJoin());
    }

    /**
     * Override and return instances of sub-jobs for a splittable job.
     *
     * @return sub-jobs requiring separate processing
     */
    public List<PipelineJob> createSplitJobs()
    {
        return Collections.singletonList(this);
    }

    /**
     * Handles merging accumulated changes from split jobs into this job, which
     * is a joined job.
     *
     * @param job the split job that has run to completion
     */
    public void mergeSplitJob(PipelineJob job)
    {
        // Add experiment actions recorded.
        _actionSet.add(job.getActionSet());

        // Add any errors that happened in the split job.
        _errors += job._errors;
    }

    public void store() throws NoSuchJobException
    {
        PipelineJobService.get().getJobStore().storeJob(this);
    }

    private void split()
    {
        try
        {
            PipelineJobService.get().getJobStore().split(this);
        }
        catch (IOException e)
        {
            error(e.getMessage(), e);
        }
    }
    
    private void join()
    {
        try
        {
            PipelineJobService.get().getJobStore().join(this);
        }
        catch (IOException | NoSuchJobException e)
        {
            error(e.getMessage(), e);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Support for running processes

    @Nullable
    private PrintWriter createPrintWriter(@Nullable File outputFile, boolean append) throws PipelineJobException
    {
        if (outputFile == null)
            return null;

        try
        {
            return new PrintWriter(new BufferedWriter(new FileWriter(outputFile, append)));
        }
        catch (IOException e)
        {
            throw new PipelineJobException("Could not create the " + outputFile + " file.", e);
        }
    }
    
    public void runSubProcess(ProcessBuilder pb, File dirWork) throws PipelineJobException
    {
        runSubProcess(pb, dirWork, null, 0, false);
    }

    /**
     * If logLineInterval is greater than 1, the first logLineInterval lines of output will be written to the
     * job's main log file.
     */
    public void runSubProcess(ProcessBuilder pb, File dirWork, File outputFile, int logLineInterval, boolean append)
            throws PipelineJobException
    {
        runSubProcess(pb, dirWork, outputFile, logLineInterval, append, 0, null);
    }

    public void runSubProcess(ProcessBuilder pb, File dirWork, File outputFile, int logLineInterval, boolean append, long timeout, TimeUnit timeoutUnit)
            throws PipelineJobException
    {
        Process proc;

        String commandName = pb.command().get(0);
        commandName = commandName.substring(
                Math.max(commandName.lastIndexOf('/'), commandName.lastIndexOf('\\')) + 1);
        header(commandName + " output");

        // Update PATH environment variable to make sure all files in the tools
        // directory and the directory of the executable or on the path.
        String toolDir = PipelineJobService.get().getAppProperties().getToolsDirectory();
        if (!StringUtils.isEmpty(toolDir))
        {
            String path = System.getenv("PATH");
            if (path == null)
            {
                path = toolDir;
            }
            else
            {
                path = toolDir + File.pathSeparatorChar + path;
            }

            // If the command has a path, then prepend its parent directory to the PATH
            // environment variable as well.
            String exePath = pb.command().get(0);
            if (exePath != null && !"".equals(exePath) && exePath.indexOf(File.separatorChar) != -1)
            {
                File fileExe = new File(exePath);
                String exeDir = fileExe.getParent();
                if (!exeDir.equals(toolDir) && fileExe.exists())
                    path = fileExe.getParent() + File.pathSeparatorChar + path;
            }

            pb.environment().put("PATH", path);

            String dyld = System.getenv("DYLD_LIBRARY_PATH");
            if (dyld == null)
            {
                dyld = toolDir;
            }
            else
            {
                dyld = toolDir + File.pathSeparatorChar + dyld;
            }
            pb.environment().put("DYLD_LIBRARY_PATH", dyld);
        }

        // tell more modern TPP tools to run headless (so no perl calls etc) bpratt 4-14-09
        pb.environment().put("XML_ONLY", "1");
        // tell TPP tools not to mess with tmpdirs, we handle this at higher level
        pb.environment().put("WEBSERVER_TMP","");

        try
        {
            pb.directory(dirWork);

            // TODO: Errors should go to log even when output is redirected to a file.
            pb.redirectErrorStream(true);

            info("Working directory is " + dirWork.getAbsolutePath());
            info("running: " + StringUtils.join(pb.command().iterator(), " "));

            proc = pb.start();
        }
        catch (SecurityException se)
        {
            throw new PipelineJobException("Failed starting process '" + pb.command() + "'. Permissions do not allow execution.", se);
        }
        catch (IOException eio)
        {
            Map<String, String> env = pb.environment();
            String path = env.get("PATH");
            if(path == null) path = env.get("Path");
            throw new PipelineJobException("Failed starting process '" + pb.command() + "'", eio);
        }


        // create thread pool for collecting the process output
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try (PrintWriter fileWriter = createPrintWriter(outputFile, append))
        {
            // collect output using separate thread so we can enforce a timeout on the process
            Future<Integer> output = pool.submit(() -> {
                try (BufferedReader procReader = Readers.getReader(proc.getInputStream()))
                {
                    String line;
                    int count = 0;
                    while ((line = procReader.readLine()) != null)
                    {
                        count++;
                        if (fileWriter == null)
                            info(line);
                        else
                        {
                            if (logLineInterval > 0 && count < logLineInterval)
                                info(line);
                            else if (count == logLineInterval)
                                info("Writing additional tool output lines to " + outputFile.getName());
                            fileWriter.println(line);
                        }
                    }
                    return count;
                }
            });

            try
            {
                if (timeout > 0)
                {
                    if (!proc.waitFor(timeout, timeoutUnit))
                    {
                        proc.destroyForcibly().waitFor();

                        error("Process killed after exceeding timeout of " + timeout + " " + timeoutUnit.name().toLowerCase());
                    }
                }
                else
                {
                    proc.waitFor();
                }

                int result = proc.exitValue();
                if (result != 0)
                {
                    throw new ToolExecutionException("Failed running " + pb.command().get(0) + ", exit code " + result, result);
                }

                int count = output.get();
                if (fileWriter != null)
                    info(count + " lines written total to " + outputFile.getName());
            }
            catch (InterruptedException ei)
            {
                throw new PipelineJobException("Interrupted process for '" + dirWork.getPath() + "'.", ei);
            }
            catch (ExecutionException e)
            {
                // Exception thrown in output collecting thread
                Throwable cause = e.getCause();
                if (cause instanceof IOException)
                    throw new PipelineJobException("Failed writing output for process in '" + dirWork.getPath() + "'.", cause);

                throw new PipelineJobException(cause);
            }
        }
        finally
        {
            pool.shutdownNow();
        }

    }

    public String getLogLevel()
    {
        return _loggerLevel;
    }

    public void setLogLevel(String level)
    {
        if (!_loggerLevel.equals(level))
        {
            _loggerLevel = level;
            _logger = null; // Reset the logger
        }
    }

    public Logger getClassLogger()
    {
        return _log;
    }

    private static class OutputLogger extends SimpleLogger
    {
        private final PipelineJob _job;
        private boolean _isSettingStatus;
        private File _file;
        private final String LINE_SEP = System.getProperty("line.separator");
        private final String datePattern = "dd MMM yyyy HH:mm:ss,SSS";

        protected OutputLogger(PipelineJob job, File file, String name, Level level)
        {
            super(name, level, false, false, false, false, "", null, new PropertiesUtil(PropertiesUtil.getSystemProperties()), null);

            _job = job;
            _file = file;

        }

        @Override
        public void debug(String message)
        {
            _job.getClassLogger().debug(getSystemLogMessage(message));
            write(message, null, Level.DEBUG.toString());
        }

        @Override
        public void debug(String message, @Nullable Throwable t)
        {
            _job.getClassLogger().debug(getSystemLogMessage(message), t);
            write(message, t, Level.DEBUG.toString());
        }

        @Override
        public void info(String message)
        {
            _job.getClassLogger().info(getSystemLogMessage(message));
            write(message, null, Level.INFO.toString());
        }

        @Override
        public void info(String message, @Nullable Throwable t)
        {
            _job.getClassLogger().info(getSystemLogMessage(message), t);
            write(message, t, Level.INFO.toString());
        }

        @Override
        public void warn(String message)
        {
            _job.getClassLogger().warn(getSystemLogMessage(message));
            write(message, null, Level.WARN.toString());
        }

        @Override
        public void warn(String message, @Nullable Throwable t)
        {
            _job.getClassLogger().warn(getSystemLogMessage(message), t);
            write(message, t, Level.WARN.toString());
        }

        @Override
        public void error(String message)
        {
            _job.getClassLogger().error(getSystemLogMessage(message));
            write(message, null, Level.ERROR.toString());
        }

        @Override
        public void error(String message, @Nullable Throwable t)
        {
            _job.getClassLogger().error(getSystemLogMessage(message), t);
            write(message, t, Level.ERROR.toString());
            setErrorStatus(message);
        }

        @Override
        public void fatal(String message)
        {
            _job.getClassLogger().fatal(getSystemLogMessage(message));
            write(message, null, Level.FATAL.toString());
        }

        @Override
        public void fatal(String message, Throwable t)
        {
            _job.getClassLogger().fatal(getSystemLogMessage(message), t);
            write(message, t, Level.FATAL.toString());
            setErrorStatus(message);
        }

        private String getSystemLogMessage(Object message)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("(from pipeline job log file ");
            sb.append(_job.getLogFile().getPath());
            if (message != null)
            {
                sb.append(": ");
                String stringMessage = message.toString();
                // Limit the maximum line length
                final int maxLength = 10000;
                if (stringMessage.length() > maxLength)
                {
                    stringMessage = stringMessage.substring(0, maxLength) + "...";
                }
                sb.append(stringMessage);
            }
            sb.append(")");
            return sb.toString();
        }

        public void setErrorStatus(Object message)
        {
            if (_isSettingStatus)
                return;

            _isSettingStatus = true;
            try
            {
                _job.setStatus(TaskStatus.error, message == null ? "ERROR" : message.toString());
            }
            finally
            {
                _isSettingStatus = false;
            }
        }

        public void write(String message, @Nullable Throwable t, String level)
        {
            String formattedDate = DateUtil.formatDateTime(new Date(), datePattern);

            try (PrintWriter writer = new PrintWriter(new FileWriter(_file, true)))
            {
                var line = formattedDate + " " +
                        String.format("%-5s", level) +
                        " : " +
                        message;
                writer.write(line);
                writer.write(LINE_SEP);
                if (null != t)
                {
                    t.printStackTrace(writer);
                }
            }
            catch (IOException e)
            {
                File parentFile = _file.getParentFile();
                if (parentFile != null && !NetworkDrive.exists(parentFile) && parentFile.mkdirs())
                    write(message, t, level);
                else
                    _log.error("Failed appending to file.", e);
            }
        }
    }

    // Multiple threads log messages, so synchronize to make sure that no one gets a partially intitialized logger
    public synchronized Logger getLogger()
    {
        if (_logger == null)
        {
            if (null == _logFilePathName || FileUtil.hasCloudScheme(_logFilePathName))
                throw new IllegalStateException("LogFile null or cloud.");

            File logFile = null != _logFile ? _logFile : new File(_logFilePathName);

            // Create appending logger.
            String loggerName = PipelineJob.class.getSimpleName() + ".Logger." + _logFilePathName;
            _logger = new OutputLogger(this, logFile, loggerName, Level.toLevel(_loggerLevel));
        }

        return _logger;
    }

    public void error(String message)
    {
        error(message, null);
    }

    public void error(String message, @Nullable Throwable t)
    {
        setErrors(getErrors() + 1);
        if (getLogger() != null)
            getLogger().error(message, t);
    }

    public void debug(String message)
    {
        debug(message, null);
    }

    public void debug(String message, @Nullable Throwable t)
    {
        if (getLogger() != null)
            getLogger().debug(message, t);
    }

    public void warn(String message)
    {
        warn(message, null);
    }

    public void warn(String message, @Nullable Throwable t)
    {
        if (getLogger() != null)
            getLogger().warn(message, t);
    }

    public void info(String message)
    {
        info(message, null);
    }

    public void info(String message, @Nullable Throwable t)
    {
        if (getLogger() != null)
            getLogger().info(message, t);
    }

    public void header(String message)
    {
        info(message);
        info("=======================================");
    }

    /////////////////////////////////////////////////////////////////////////
    //  ViewBackgroundInfo access
    //      WARNING: Some access of ViewBackgroundInfo is not supported when
    //               the job is running outside the LabKey Server.

    /**
     * Gets the container ID from the <code>ViewBackgroundInfo</code>.
     *
     * @return the ID for the container in which the job was started
     */
    public String getContainerId()
    {
        return getInfo().getContainerId();
    }

    /**
     * Gets the <code>User</code> instance from the <code>ViewBackgroundInfo</code>.
     * WARNING: Not supported if job is not running in the LabKey Server.
     *
     * @return the user who started the job
     */
    public User getUser()
    {
        return getInfo().getUser();
    }

    /**
     * Gets the <code>Container</code> instance from the <code>ViewBackgroundInfo</code>.
     * WARNING: Not supported if job is not running in the LabKey Server.
     *
     * @return the container in which the job was started
     */
    public Container getContainer()
    {
        return getInfo().getContainer();
    }

    /**
     * Gets the <code>ActionURL</code> instance from the <code>ViewBackgroundInfo</code>.
     * WARNING: Not supported if job is not running in the LabKey Server.
     *
     * @return the URL of the request that started the job
     */
    public ActionURL getActionURL()
    {
        return getInfo().getURL();
    }

    /**
     * Gets the <code>ViewBackgroundInfo</code> associated with this job in its contstructor.
     * WARNING: Although this function is supported outside the LabKey Server, certain
     *          accessors on the <code>ViewBackgroundInfo</code> itself are not.
     *
     * @return information from the starting request, for use in background processing
     */
    public ViewBackgroundInfo getInfo()
    {
        return _info;
    }

    /////////////////////////////////////////////////////////////////////////
    // Scheduling interface
    //      TODO: Figure out how these apply to the Enterprise Pipeline
    
    protected boolean canInterrupt()
    {
        return false;
    }

    public synchronized boolean interrupt()
    {
        if (!canInterrupt())
            return false;
        _interrupted = true;
        return true;
    }

    public synchronized boolean checkInterrupted()
    {
        return _interrupted;
    }

    public boolean allowMultipleSimultaneousJobs()
    {
        return false;
    }

    synchronized public void setSubmitted()
    {
        _submitted = true;
        notifyAll();
    }

    synchronized private boolean isSubmitted()
    {
        return _submitted;
    }

    synchronized private void waitUntilSubmitted()
    {
        while (!_submitted)
        {
            try
            {
                wait();
            }
            catch (InterruptedException ignored) {}
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // JobRunner.Job interface
    
    @Override
    public Object get() throws InterruptedException, ExecutionException
    {
        waitUntilSubmitted();
        return super.get();
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException
    {
        return get();
    }

    @Override
    protected void starting(Thread thread)
    {
        _queue.starting(this, thread);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        if (isSubmitted())
            return super.cancel(mayInterruptIfRunning);
        return true;
    }

    @Override
    public boolean isDone()
    {
        if (!isSubmitted())
            return false;
        return super.isDone();
    }

    @Override
    public boolean isCancelled()
    {
        if (!isSubmitted())
            return false;
        return super.isCancelled();
    }

    @Override
    public void done(Throwable throwable)
    {
        if (null != throwable)
        {
            try
            {
                error("Uncaught exception in PipelineJob: " + this.toString(), throwable);
            }
            catch (Exception ignored) {}
        }
        if (_queue != null)
        {
            _queue.done(this);
        }
    }

    public static String serializeJob(PipelineJob job)
    {
        return serializeJob(job, true);
    }

    public static String serializeJob(PipelineJob job, boolean ensureDeserialize)
    {
        return PipelineJobService.get().getJobStore().serializeToJSON(job, ensureDeserialize);
    }

    public static String getClassNameFromJson(String serialized)
    {
        // Expect [ "org.labkey....", {....
        if (StringUtils.startsWith(serialized, "["))
        {
            return StringUtils.substringBetween(serialized, "\"");
        }
        else
        {
            throw new RuntimeException("Unexpected serialized JSON");
        }
    }

    @Nullable
    public static PipelineJob deserializeJob(@NotNull String serialized)
    {
        try
        {
            String className = PipelineJob.getClassNameFromJson(serialized);
            Object job = PipelineJobService.get().getJobStore().deserializeFromJSON(serialized, Class.forName(className));
            if (job instanceof PipelineJob)
                return (PipelineJob) job;

            _log.error("Deserialized object not instance of PipelineJob: " + job.getClass().getName());
        }
        catch (ClassNotFoundException e)
        {
            _log.error("Deserialized class not found.", e);
        }
        return null;
    }

    public static ObjectMapper createObjectMapper()
    {
        ObjectMapper mapper = new ObjectMapper()
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);

        SimpleModule module = new SimpleModule();
        module.addSerializer(new SqlTimeSerialization.SqlTimeSerializer());
        module.addDeserializer(Time.class, new SqlTimeSerialization.SqlTimeDeserializer());
        module.addDeserializer(AtomicLong.class, new AtomicLongDeserializer());
        module.addSerializer(NullSafeBindException.class, new NullSafeBindExceptionSerializer());
        module.addSerializer(QueryKey.class, new QueryKeySerialization.Serializer());
        module.addDeserializer(SchemaKey.class, new QueryKeySerialization.SchemaKeyDeserializer());
        module.addDeserializer(FieldKey.class, new QueryKeySerialization.FieldKeyDeserializer());
        module.addSerializer(Path.class, new PathSerialization.Serializer());
        module.addDeserializer(Path.class, new PathSerialization.Deserializer());
        module.addSerializer(CronExpression.class, new CronExpressionSerialization.Serializer());
        module.addDeserializer(CronExpression.class, new CronExpressionSerialization.Deserializer());
        module.addSerializer(URI.class, new URISerialization.Serializer());
        module.addDeserializer(URI.class, new URISerialization.Deserializer());
        module.addSerializer(File.class, new FileSerialization.Serializer());
        module.addDeserializer(File.class, new FileSerialization.Deserializer());

        mapper.registerModule(module);
        return mapper;
    }

    public abstract static class TestSerialization extends org.junit.Assert
    {
        public void testSerialize(Object job, @Nullable Logger log)
        {
            PipelineStatusFile.JobStore jobStore = PipelineJobService.get().getJobStore();
            try
            {
                if (null != log)
                    log.info("Hi Logger is here!");
                String json = jobStore.serializeToJSON(job);
                if (null != log)
                    log.info(json);
                Object job2 = jobStore.deserializeFromJSON(json, job.getClass());
                if (null != log)
                    log.info(job2.toString());

                if (job instanceof PipelineJob)
                {
                    assert (job2 instanceof PipelineJob);
                    List<String> errors = ((PipelineJob)job).compareJobs((PipelineJob)job2);
                    if (!errors.isEmpty())
                    {
                        fail("Pipeline objects don't match: " + StringUtils.join(errors, ","));
                    }
                }
            }
            catch (Exception e)
            {
                if (null != log)
                    log.error("Class not found", e);
            }
        }

    }

    @Override
    public boolean equals(Object o)
    {
        // Fix issue 35876: Second run of a split XTandem pipeline job not completing - don't rely on the job being
        // represented in memory as a single object
        if (this == o) return true;
        if (!(o instanceof PipelineJob)) return false;
        PipelineJob that = (PipelineJob) o;
        return Objects.equals(_jobGUID, that._jobGUID);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_jobGUID);
    }

    public List<String> compareJobs(PipelineJob job2)
    {
        PipelineJob job1 = this;
        List<String> errors = new ArrayList<>();
        if (!PropertyUtil.nullSafeEquals(job1._activeTaskId, job2._activeTaskId))
            errors.add("_activeTaskId");
        if (job1._activeTaskRetries != job2._activeTaskRetries)
            errors.add("_activeTaskRetries");
        if (!PropertyUtil.nullSafeEquals(job1._activeTaskStatus, job2._activeTaskStatus))
            errors.add("_activeTaskStatus");
        if (job1._errors != job2._errors)
            errors.add("_errors");
        if (job1._interrupted != job2._interrupted)
            errors.add("_interrupted");
        if (!PropertyUtil.nullSafeEquals(job1._jobGUID, job2._jobGUID))
            errors.add("_jobGUID");
        if (!PropertyUtil.nullSafeEquals(job1._logFile, job2._logFile))
        {
            if (null == job1._logFile || null == job2._logFile)
                errors.add("_logFile");
            else if (!FileUtil.getAbsoluteCaseSensitiveFile(job1._logFile).getAbsolutePath().equalsIgnoreCase(FileUtil.getAbsoluteCaseSensitiveFile(job2._logFile).getAbsolutePath()))
                errors.add("_logFile");
        }
        if (!PropertyUtil.nullSafeEquals(job1._logFilePathName, job2._logFilePathName))
            errors.add("_logFilePathName");
        if (!PropertyUtil.nullSafeEquals(job1._parentGUID, job2._parentGUID))
            errors.add("_parentGUID");
        if (!PropertyUtil.nullSafeEquals(job1._provider, job2._provider))
            errors.add("_provider");
        if (job1._submitted != job2._submitted)
            errors.add("_submitted");

        return errors;
    }

}
