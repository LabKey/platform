/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.*;
import org.apache.log4j.spi.HierarchyEventListener;
import org.apache.log4j.spi.LoggerFactory;
import org.apache.log4j.spi.LoggerRepository;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.*;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ActionURL;

import java.io.*;
import java.net.URI;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

abstract public class PipelineJob extends Job implements Serializable
{
    public static final FileType FT_LOG = new FileType(".log");
    public static final FileType FT_CLUSTER_STATUS = new FileType(".status");

    private static Logger _log = Logger.getLogger(PipelineJob.class);

    public static Logger getJobLogger(Class clazz)
    {
        return Logger.getLogger(PipelineJob.class.getName() + ".." + clazz.getName());
    }

    public enum TaskStatus { waiting, running, complete, error }
    
    /**
     * <code>Task</code> implements a runnable to complete a part of the
     * processing associated with a particular <code>PipelineJob</code>.
     */
    abstract static public class Task implements Runnable
    {
        private PipelineJob _job;

        protected String getExecutablePath(String executable)
        {
            String toolsDir = PipelineJobService.get().getAppProperties().getToolsDirectory();
            if (toolsDir == null || toolsDir.trim().equals(""))
            {
                return executable;
            }

            File f = new File(toolsDir);
            if (!NetworkDrive.exists(f) || !f.isDirectory())
            {
                return executable;
            }
            
            if (!toolsDir.endsWith(File.separator))
            {
                toolsDir = toolsDir + File.separatorChar;
            }

            return toolsDir + executable;
        }

        public Task(PipelineJob job)
        {
            _job = job;
        }

        public PipelineJob getJob()
        {
            return _job;
        }
    }

    /*
     * Status strings
     */
    public static final String WAITING_STATUS = TaskStatus.waiting.toString().toUpperCase();
    public static final String COMPLETE_STATUS = TaskStatus.complete.toString().toUpperCase();
    public static final String ERROR_STATUS = TaskStatus.error.toString().toUpperCase();
    public static final String CANCELLED_STATUS = "CANCELLED";
    public static final String INTERRUPTED_STATUS = "INTERRUPTED";
    public static final String RESTARTED_STATUS = "RESTARTED";

    /*
     * JMS message header names
     */
    private static String HEADER_PREFIX = "LABKEY_";
    public static final String LABKEY_JOBTYPE_PROPERTY = HEADER_PREFIX + "JOBTYPE";
    public static final String LABKEY_JOBID_PROPERTY = HEADER_PREFIX + "JOBID";
    public static final String LABKEY_CONTAINERID_PROPERTY = HEADER_PREFIX + "CONTAINERID";
    public static final String LABKEY_TASKPIPELINE_PROPERTY = HEADER_PREFIX + "TASKPIPELINE";
    public static final String LABKEY_TASKID_PROPERTY = HEADER_PREFIX + "TASKID";
    public static final String LABKEY_TASKSTATUS_PROPERTY = HEADER_PREFIX + "TASKSTATUS";

    private String _provider;
    private ViewBackgroundInfo _info;
    private String _jobGUID;
    private String _parentGUID;
    private TaskId _activeTaskId;
    private TaskStatus _activeTaskStatus;
    private URI _rootURI;
    private File _logFile;
    private File _statusFile;
    private boolean _started;
    private boolean _interrupted;
    private boolean _submitted;
    private boolean _settingStatus;
    private int _errors;

    private String _loggerLevel = Level.DEBUG.toString();
    protected transient Logger _logger;

    private transient PipelineQueue _queue;

    public PipelineJob(String provider, ViewBackgroundInfo info) throws SQLException
    {
        _info = info;
        _provider = provider;
        _jobGUID = GUID.makeGUID();
        _activeTaskStatus = TaskStatus.waiting;

        // TODO: Fix TestJob for mini-pipeline using job with no PipeRoot.
        if (info.getUrlHelper() != null)
        {
            Container c = info.getContainer();
            PipeRoot pr = PipelineService.get().findPipelineRoot(c);
            if (pr == null)
                throw new SQLException("Failed to find pipeline root for " + c.getPath());
            _rootURI = pr.getUri();
        }
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
        _rootURI = job._rootURI;
        _logFile = job._logFile;
        _statusFile = job._statusFile;
        _interrupted = job._interrupted;
        _submitted = job._submitted;
        _errors = job._errors;
        _loggerLevel = job._loggerLevel;
        _logger = job._logger;

        _activeTaskId = job._activeTaskId;
        _activeTaskStatus = job._activeTaskStatus;

    }

    public boolean isStarted()
    {
        return _started;
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

    public Map<String, String> getParameters()
    {
        return new HashMap<String, String>();
    }

    public String getJobGUID()
    {
        return _jobGUID;
    }

    public String getParentGUID()
    {
        return _parentGUID;
    }

    public TaskId getActiveTaskId()
    {
        return _activeTaskId;
    }

    public void setActiveTaskId(TaskId activeTaskId)
    {
        _activeTaskId = activeTaskId;
        if (_activeTaskId == null)
            _activeTaskStatus = TaskStatus.complete;
        else
            _activeTaskStatus = TaskStatus.waiting;
        updateStatusForTask();
    }

    public TaskStatus getActiveTaskStatus()
    {
        return _activeTaskStatus;
    }

    public void setActiveTaskStatus(TaskStatus activeTaskStatus)
    {
        _activeTaskStatus = activeTaskStatus;
        updateStatusForTask();
    }

    public TaskFactory getActiveTaskFactory()
    {
        return PipelineJobService.get().getTaskFactory(getActiveTaskId());
    }

    public File getRootDir()
    {
        return new File(_rootURI);
    }

    public void setLogFile(File fileLog)
    {
        setLogFile(fileLog, false);
    }

    public void setLogFile(File fileLog, boolean append)
    {
        _logFile = fileLog;
        _logger = null;

        // Truncate the log.
        if (!append)
        {
            FileOutputStream fos = null;
            try
            {
                fos = new FileOutputStream(_logFile);
            }
            catch (FileNotFoundException e)
            {
            }
            finally
            {
                if (fos != null)
                {
                    try { fos.close(); }
                    catch (IOException e) {}
                }
            }
        }
    }

    public File getLogFile()
    {
        return _logFile;
    }

    public void setStatusFile(File statusFile)
    {
        this._statusFile = statusFile;
    }

    public static File getSerializedFile(File statusFile)
    {
        if (statusFile == null)
        {
            return null;
        }

        String name = statusFile.getName();
        int index = name.indexOf('.');
        if (index != -1)
        {
            name = name.substring(0, index);
        }
        return new File(statusFile.getParentFile(), name + ".job.ser");
    }

    public static File getClusterOutputFile(File statusFile)
    {
        if (statusFile == null)
        {
            return null;
        }

        String name = statusFile.getName();
        int index = name.indexOf('.');
        if (index != -1)
        {
            name = name.substring(0, index);
        }
        return new File(statusFile.getParentFile(), name + ".job.log");
    }

    public File getStatusFile()
    {
        return (_statusFile == null ? _logFile : _statusFile);
    }

    public void updateStatusForTask()
    {
        updateStatusForTask(null);
    }

    public void updateStatusForTask(String info)
    {
        TaskFactory factory = getActiveTaskFactory();
        TaskStatus status = getActiveTaskStatus();

        if (factory != null && !TaskStatus.error.equals(status))
            setStatus(factory.getStatusName() + " " + status.toString().toUpperCase(), info);
        else
            setStatus(status.toString().toUpperCase(), info);
    }

    public void setStatus(String status)
    {
        setStatus(status, null);
    }

    public void setStatus(String status, String info)
    {
        if (_settingStatus)
            return;
        
        _settingStatus = true;
        try
        {
            PipelineJobService.get().getStatusWriter().setStatusFile(getInfo(), this, status, info);
        }
        catch (Exception e)
        {
            File f = getStatusFile();
            error("Failed to set status to '" + status + "' for '" +
                    (f == null ? "" : f.getPath()) + "'.", e);
        }
        finally
        {
            _settingStatus = false;
        }
    }

    public void restoreQueue(PipelineQueue queue)
    {
        if (null != _queue)
            throw new IllegalStateException();
        _queue = queue;
    }
    
    public void setQueue(PipelineQueue queue, String initialState)
    {
        restoreQueue(queue);
        
        // Initialize the task pipeline
        if (getTaskPipeline() != null && getActiveTaskId() == null)
            runStateMachine();

        // Initialize status.
        if (_logFile != null)
            setStatus(initialState);
    }

    public void clearQueue()
    {
        _queue = null;
    }

    abstract public ActionURL getStatusHref();

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
    public TaskPipeline getTaskPipeline()
    {
        return null;
    }

    public boolean isActiveTaskLocal()
    {
        TaskFactory factory = getActiveTaskFactory();
        return (factory != null &&
                TaskFactory.WEBSERVER.equals(factory.getExecutionLocation()));
    }

    public void runActiveTask()
    {
        try
        {
            TaskFactory factory = getActiveTaskFactory();
            if (factory == null)
                return;

            if (!factory.isJobComplete(this))
            {
                Task task = factory.createTask(this);
                if (task == null)
                    return; // Bad task key.

                setActiveTaskStatus(TaskStatus.running);
                task.run();

                _started = true;

                if (getErrors() > 0)
                    return;
            }

            setActiveTaskStatus(TaskStatus.complete);
        }
        catch (SQLException e)
        {
            error(e.getMessage(), e);
        }
        catch (IOException e)
        {
            error(e.getMessage(), e);
        }
    }

    public boolean runStateMachine()
    {
        TaskPipeline pipeline = getTaskPipeline();

        if (pipeline == null)
        {
            assert false : "Either override getTaskPipeline(), or run()";

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
            case running:
            default:
                return false;   // Do not run the active task.
        }
    }

    private int indexOfActiveTask(TaskId[] progression)
    {
        for (int i = 0; i < progression.length; i++)
        {
                TaskFactory factory = PipelineJobService.get().getTaskFactory(progression[i]);
                if (factory.getActiveId(this).equals(_activeTaskId))
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
                if ((factory.isJoin() && isSplit()) || (!factory.isJoin() && isSplittable()))
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
            catch (SQLException e)
            {
                error(e.getMessage());
                return false;
            }

            i++;
        }

        if (i < progression.length)
        {
            assert factory != null : "Factory not found.";

            // Set next task to be run
            setActiveTaskId(factory.getActiveId(this));

            if (factory.isJoin() && isSplit())
            {
                join();
                return false;
            }
            else if (!factory.isJoin() && isSplittable())
            {
                split();
                return false;
            }

            // If it is local, then it can be run
            return isActiveTaskLocal();
        }
        else
        {
            // Job is complete
            setActiveTaskId(null);
            return false;
        }
    }

    public void run()
    {
        while (runStateMachine())
            runActiveTask();
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
     * Returns true if all tasks in this progression to be run are split with no
     * potential joins.
     *
     * @return true if all tasks are split
     */
    public boolean isAllSplit()
    {
        if (!isSplittable())
            return false;

        TaskPipeline tp = getTaskPipeline();
        if (tp == null)
            return false;

        boolean seenSplit = false;
        boolean seenId = (getActiveTaskId() == null);
        for (TaskId id : tp.getTaskProgression())
        {
            // Skip everything up to the active TaskId
            if (!seenId)
            {
                if (id == getActiveTaskId())
                    seenId = true;
                else
                    continue;
            }
            
            TaskFactory factory = PipelineJobService.get().getTaskFactory(id);
            if (factory.isJoin())
            {
                try
                {
                    if (factory.isParticipant(this))
                        return false;
                }
                catch (Exception e)
                {
                    // If participant check fails, assume it is.
                    return false;
                }
            }
            else
            {
                seenSplit = true;
            }
        }

        return seenSplit;
    }

    /**
     * Returns true if this is a split job, as determined by whether it has a parent.
     *
     * @return true if this is a split job
     */
    public boolean isSplit()
    {
        return getParentGUID() != null;
    }

    /**
     * Override and return instances of sub-jobs for a splittable job.
     *
     * @return sub-jobs requiring separate processing
     */
    public PipelineJob[] createSplitJobs()
    {
        return new PipelineJob[] { this };
    }

    public void store() throws IOException, SQLException
    {
        PipelineJobService.get().getJobStore().storeJob(getInfo(), this);
    }

    public void split()
    {
        try
        {
            PipelineJobService.get().getJobStore().split(getInfo(), this);
        }
        catch (IOException e)
        {
            error(e.getMessage(), e);
        }
        catch (SQLException e)
        {
            error(e.getMessage(), e);            
        }
    }
    
    public void join()
    {
        try
        {
            PipelineJobService.get().getJobStore().join(getInfo(), this);
        }
        catch (IOException e)
        {
            error(e.getMessage(), e);
        }
        catch (SQLException e)
        {
            error(e.getMessage(), e);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Support for running processes
    
    public static class RunProcessException extends Exception
    {
        public RunProcessException()
        {
            super();
        }

        public RunProcessException(Throwable cause)
        {
            super(cause);
        }

        public RunProcessException(String message)
        {
            super(message);
        }
    }

    public void runSubProcess(ProcessBuilder pb, File dirWork)
            throws RunProcessException, InterruptedException
    {
        runSubProcess(pb, dirWork, null, 0);
    }

    public void runSubProcess(ProcessBuilder pb, File dirWork, File outputFile)
            throws RunProcessException, InterruptedException
    {
        runSubProcess(pb, dirWork, outputFile, 0);
    }

    public void runSubProcess(ProcessBuilder pb, File dirWork, File outputFile, int logLineInterval)
            throws RunProcessException, InterruptedException
    {
        Process proc;

        header(pb.command().get(0) + " output");

        PrintWriter fileWriter = null;
        try
        {
            try
            {
                if(outputFile != null)
                {
                    fileWriter = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
                }
            }
            catch(IOException e)
            {
                error("Could not create the " + outputFile + " file.",e);
                throw new RunProcessException(e);
            }

            String toolDir = PipelineJobService.get().getAppProperties().getToolsDirectory();
            if (toolDir != null && !"".equals(toolDir))
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

                pb.environment().put("PATH", path);
            }

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
                error("Failed starting process '" + pb.command() + "'. Permissions do not allow execution.", se);
                throw new RunProcessException(se);
            }
            catch (IOException eio)
            {
                Map<String, String> env = pb.environment();
                String path = env.get("PATH");
                if(path == null) path = env.get("Path");
                error("Failed starting process '" + pb.command() + "'. " +
                        "Must be on server path. (PATH=" + path + ")", eio);
                throw new RunProcessException(eio);
            }

            BufferedReader procReader = null;

            try
            {
                procReader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()));
                String line;
                int count = 0;
                while ((line = procReader.readLine()) != null)
                {
                    count++;
                    if(fileWriter == null)
                        info(line);
                    else
                    {
                        fileWriter.println(line);
                        if (logLineInterval > 0 && (count % logLineInterval == 0))
                            info(count + " lines");
                    }
                }
                if (fileWriter != null)
                    info(count + " lines written total");
            }
            catch (IOException eio)
            {
                error("Failed writing output for process in '" + dirWork.getPath() + "'.", eio);
                throw new RunProcessException(eio);
            }
            finally
            {
                if (procReader != null)
                {
                    try
                    {   procReader.close(); }
                    catch (IOException eio)
                    { }
                }
            }
        }
        finally
        {
            if (fileWriter != null)
                fileWriter.close();
        }

        try
        {
            int result = proc.waitFor();
            if (result != 0)
            {
                error("Failed running " + pb.command().get(0) + ", exit code " + result);
                throw new RunProcessException("Exit code " + result);
            }
        }
        catch (InterruptedException ei)
        {
            info("Interrupted process for '" + dirWork.getPath() + "'.", ei);
            throw ei;
        }
    }

    /////////////////////////////////////////////////////////////////////////
    //  Logging
    
    /**
     * Log4J Logger subclass to allow us to create loggers that are not cached
     * in the Log4J repository for the life of the webapp.  This logger also
     * logs to the weblog for the PipelineJob class, allowing administrators
     * to collect whatever level of logging they want from PipelineJobs.
     */
    private class OutputLogger extends Logger
    {
        private boolean _isSettingStatus;

        protected OutputLogger(String name)
        {
            super(name);

            repository = new OutputLoggerRepository(name, this);
        }

        public void debug(Object message)
        {
            debug(message, null);
        }

        public void debug(Object message, Throwable t)
        {
            getClassLogger().debug(message, t);
            super.debug(message, t);
        }

        public void info(Object message)
        {
            info(message, null);
        }

        public void info(Object message, Throwable t)
        {
            getClassLogger().info(message, t);
            super.info(message, t);
        }

        public void warn(Object message)
        {
            warn(message, null);
        }

        public void warn(Object message, Throwable t)
        {
            getClassLogger().warn(message, t);
            super.warn(message, t);
        }

        public void error(Object message)
        {
            error(message, null);
        }

        public void error(Object message, Throwable t)
        {
            getClassLogger().error(message, t);
            super.error(message, t);
            setErrorStatus(message);
        }

        public void fatal(Object message)
        {
            fatal(message, null);
        }

        public void fatal(Object message, Throwable t)
        {
            getClassLogger().error(message, t);
            super.fatal(message, t);
            setErrorStatus(message);
        }

        public void setErrorStatus(Object message)
        {
            if (_isSettingStatus)
                return;

            _isSettingStatus = true;
            try
            {
                setStatus(PipelineJob.ERROR_STATUS, message.toString());
            }
            finally
            {
                _isSettingStatus = false;
            }
        }
    }

    private static class OutputLoggerRepository implements LoggerRepository
    {
        private String _name;
        private OutputLogger _outputLogger;

        protected OutputLoggerRepository(String name, OutputLogger logger)
        {
            _name = name;
            _outputLogger = logger;
        }

        public void addHierarchyEventListener(HierarchyEventListener listener)
        {
        }

        public boolean isDisabled(int level)
        {
            return false;
        }

        public void setThreshold(Level level)
        {
        }

        public void setThreshold(String val)
        {
        }

        public void emitNoAppenderWarning(Category cat)
        {
        }

        public Level getThreshold()
        {
            return null;
        }

        public Logger getLogger(String name)
        {
            if (_name.equals(name))
                return _outputLogger;
            return null;
        }

        public Logger getLogger(String name, LoggerFactory factory)
        {
            throw new UnsupportedOperationException();
        }

        public Logger getRootLogger()
        {
            return _outputLogger;
        }

        public Logger exists(String name)
        {
            if (_name.equals(name))
                return _outputLogger;
            return null;
        }

        public void shutdown()
        {
        }

        public Enumeration getCurrentLoggers()
        {
            Vector<OutputLogger> v = new Vector<OutputLogger>();
            v.add(_outputLogger);
            return v.elements();
        }

        public Enumeration getCurrentCategories()
        {
            return getCurrentLoggers();
        }

        public void fireAddAppenderEvent(Category logger, Appender appender)
        {
        }

        public void resetConfiguration()
        {
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

    public Logger getLogger()
    {
        if (_logger == null)
        {
            // Create appending logger.
            _logger = new OutputLogger(PipelineJob.class.getSimpleName() + ".Logger." + _logFile);
            _logger.removeAllAppenders();
            SafeFileAppender appender = new SafeFileAppender(_logFile);
            appender.setLayout(new PatternLayout("%d{DATE} %-5p: %m%n"));
            _logger.addAppender(appender);
            _logger.setLevel(Level.toLevel(_loggerLevel));
        }

        return _logger;
    }

    public void error(String message)
    {
        error(message, null);
    }

    public void error(String message, Throwable t)
    {
        setErrors(getErrors() + 1);
        if (getLogger() != null)
            getLogger().error(message, t);
    }

    public void warn(String message)
    {
        warn(message, null);
    }

    public void warn(String message, Throwable t)
    {
        if (getLogger() != null)
            getLogger().warn(message, t);
    }

    public void info(String message)
    {
        info(message, null);
    }

    public void info(String message, Throwable t)
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
    //  ViewBrackgroundInfo access
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
        return getInfo().getUrlHelper();
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
            catch (InterruptedException e)
            {
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // JobRunner.Job interface
    
    public Object get() throws InterruptedException, ExecutionException
    {
        waitUntilSubmitted();
        return super.get();
    }

    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        return get();
    }

    protected void starting(Thread thread)
    {
        _queue.starting(this, thread);
    }

    public boolean cancel(boolean mayInterruptIfRunning)
    {
        if (isSubmitted())
            return super.cancel(mayInterruptIfRunning);
        return true;
    }

    public boolean isDone()
    {
        if (!isSubmitted())
            return false;
        return super.isDone();
    }

    public boolean isCancelled()
    {
        if (!isSubmitted())
            return false;
        return super.isCancelled();
    }

    @Override
    protected void done(Throwable throwable)
    {
        if (null != throwable)
        {
            try
            {
                error("Uncaught exception in PiplineJob: " + this.toString(), throwable);
            }
            catch (Exception x)
            {
            }
        }
        _queue.done(this);
    }
}
