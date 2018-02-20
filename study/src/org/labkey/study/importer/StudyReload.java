/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.study.importer;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Study;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.validation.BindException;

import java.io.File;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/*
* User: adam
* Date: Jun 10, 2009
* Time: 5:36:01 PM
*/
public class StudyReload
{
    private static final Logger LOG = Logger.getLogger(StudyReload.class);
    private static final BlockingQueue<ImportOptions> QUEUE = new ArrayBlockingQueue<>(100);       // Container IDs instead?
    private static final Thread RELOAD_THREAD = new ReloadThread();

    private static final String JOB_GROUP_NAME = "org.labkey.study.importer.StudyReload";

    static
    {
        ContextListener.addShutdownListener(new ShutdownListener()
        {
            @Override
            public String getName()
            {
                return RELOAD_THREAD.getName();
            }

            public void shutdownPre()
            {
                RELOAD_THREAD.interrupt();
            }

            public void shutdownStarted()
            {
            }
        });

        RELOAD_THREAD.start();
    }

    public enum ReloadInterval
    {
        Never(0, "<Never>", "never"),
        Hours24(24 * 60 * 60, "24 Hours", "once a day"),
        Hours1(60 * 60, "1 Hour", "once an hour"),
        Minutes5(5 * 60, "5 Minutes", "every five minutes"),
        Seconds10(10, true, "10 Seconds", "every 10 seconds");   // for dev mode purposes only

        private final String _dropDownLabel;
        private final String _description;
        private final Integer _seconds;
        private final boolean _devOnly;

        ReloadInterval(Integer seconds, String dropDownLabel, String description)
        {
            this(seconds, false, dropDownLabel, description);
        }

        ReloadInterval(Integer seconds, boolean devOnly, String dropDownLabel, String description)
        {
            _seconds = seconds;
            _devOnly = devOnly;
            _dropDownLabel = dropDownLabel;
            _description = description;
        }

        public String getDropDownLabel()
        {
            return _dropDownLabel;
        }

        public String getDescription()
        {
            return _description;
        }

        public Integer getSeconds()
        {
            return _seconds;
        }

        public boolean shouldDisplay()
        {
            return !_devOnly || AppProps.getInstance().isDevMode();
        }

        @NotNull
        public static ReloadInterval getForSeconds(Integer seconds)
        {
            for (ReloadInterval interval : values())
                if (interval.getSeconds().equals(seconds))
                    return interval;

            return Never;
        }
    }


    private static String getDescription(Study study)
    {
        return study.getLabel();
    }


    public static void initializeTimer(StudyImpl study)
    {
        initializeTimer(study.getContainer().getId(), study.isAllowReload(), study.getReloadInterval());
    }


    public static void initializeAllTimers()
    {
        TableInfo tinfo = StudySchema.getInstance().getTableInfoStudy();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("AllowReload"), true);
        filter.addCondition(FieldKey.fromParts("ReloadInterval"), 0, CompareType.GT);

        new TableSelector(tinfo, tinfo.getColumns("Container", "ReloadInterval"), filter, null)
            .forEach(rs -> initializeTimer(rs.getString(1), true, rs.getInt(2)));
    }


    public static void cancelTimer(Container c)
    {
        cancelTimer(c.getId());
    }


    private static void cancelTimer(String containerId)
    {
        initializeTimer(containerId, false, 0);
    }


    private static void initializeTimer(String containerId, boolean allowReload, Integer secondsInterval)
    {
        try
        {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            TriggerKey triggerKey = TriggerKey.triggerKey(containerId, JOB_GROUP_NAME);
            JobKey jobKey = JobKey.jobKey(containerId, JOB_GROUP_NAME);

            // Unschedule any existing triggers
            if (scheduler.checkExists(triggerKey))
            {
                scheduler.unscheduleJob(triggerKey);
            }

            // Check if we should schedule a new trigger
            if (allowReload && null != secondsInterval && 0 != secondsInterval)
            {
                int reasonableInterval = Math.max(secondsInterval.intValue(), 10);

                // Reuse the existing detail object if we've already submitted it in the past
                JobDetail job = scheduler.getJobDetail(jobKey);
                boolean existingJob = job != null;
                if (!existingJob)
                {
                    job = JobBuilder.newJob(ReloadTask.class).
                            withIdentity(jobKey).
                            storeDurably().
                            build();
                    job.getJobDataMap().put(CONTAINER_ID_KEY, containerId);
                }

                // Run it on the user-specified schedule
                Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
//                    .startNow()
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInSeconds(reasonableInterval)
                            .repeatForever())
                    .usingJobData(job.getJobDataMap())
                    .build();

                // Submit it
                if (existingJob)
                {
                    scheduler.scheduleJob(trigger);
                }
                else
                {
                    scheduler.scheduleJob(job, trigger);
                }
            }
        }
        catch (SchedulerException e)
        {
            throw new UnexpectedException(e);
        }
    }


    @Nullable
    public static PipeRoot getPipelineRoot(Container c)
    {
        PipeRoot root = PipelineService.get().findPipelineRoot(c);
        if (root == null || !root.isValid())
        {
            throw new NotFoundException("No valid pipeline root found");
        }
        return root;
    }

    private static final String CONTAINER_ID_KEY = "StudyContainerId";

    public static class ReloadTask implements Job
    {
        private static final String STUDY_LOAD_FILENAME = "studyload.txt";

        public void execute(JobExecutionContext context)
        {
            String studyContainerId = (String)context.getJobDetail().getJobDataMap().get(CONTAINER_ID_KEY);
            try
            {
                ImportOptions options = new ImportOptions(studyContainerId, null);
                attemptScheduledReload(options, "a configured automatic reload timer");    // Ignore success messages
            }
            catch (ImportException ie)
            {
                Container c = ContainerManager.getForId(studyContainerId);
                String message = null != c ? " in folder " + c.getPath() : "";

                LOG.error("Study reload failed: " + message, ie);
            }
            catch (Throwable t)
            {
                // Throwing from run() will kill the reload task, suppressing all future attempts; log to mothership and continue, so we retry later.
                ExceptionUtil.logExceptionToMothership(null, t);
            }
        }

        public StudyImpl validateStudyForReload(ImportOptions options, Container c, boolean isScheduled) throws ImportException
        {
                StudyImpl study = StudyManager.getInstance().getStudy(c);

                if (null == study)
                {
                    // Study must have been deleted
                    if (isScheduled)
                        cancelTimer(options.getContainerId());
                    throw new ImportException("Study does not exist in folder " + c.getPath());
                }
                else
                {
                    assert study.isAllowReload() : "Can't reload a study set for no reload";
                    return study;
                }
        }

        public PipeRoot validatePipeRoot(Container c) throws ImportException
        {
            PipeRoot root = PipelineService.get().findPipelineRoot(c);

            if (null == root)
                throw new ImportException("Pipeline root is not set in folder " + c.getPath());

            if (!root.isValid())
                throw new ImportException("Pipeline root does not exist in folder " + c.getPath());

            return root;
        }

        public ReloadStatus attemptTriggeredReload(ImportOptions options, String source) throws ImportException
        {
            Container c = ContainerManager.getForId(options.getContainerId());
            if (null == c)
                throw new ImportException("Container " + options.getContainerId() + " does not exist");
            else
            {
                StudyImpl study = validateStudyForReload(options, c, false);
                return reloadStudy(study, options, source, null, study.getLastReload());
            }
        }

        public ReloadStatus attemptScheduledReload(ImportOptions options, String source) throws ImportException
        {
            Container c = ContainerManager.getForId(options.getContainerId());

            if (null == c)
            {
                // Container must have been deleted
                cancelTimer(options.getContainerId());
                throw new ImportException("Container " + options.getContainerId() + " does not exist");
            }
            else
            {
                StudyImpl study = validateStudyForReload(options, c, false);
                PipeRoot root = validatePipeRoot(c);
                File studyload = root.resolvePath(STUDY_LOAD_FILENAME);

                if (studyload != null && studyload.isFile())
                {
                    Long lastModified = studyload.lastModified();
                    Date lastReload = study.getLastReload();

                    if (null == lastReload || studyload.lastModified() > (lastReload.getTime() + 1000))  // Add a second since SQL Server rounds datetimes
                    {
                        return reloadStudy(study, options, source, lastModified, lastReload);
                    }
                }
                else
                {
                    throw new ImportException("Could not find file " + STUDY_LOAD_FILENAME + " in the pipeline root for " + getDescription(study));
                }
            }

            return new ReloadStatus("Reload failed", false);
        }

        public ReloadStatus reloadStudy(StudyImpl study, ImportOptions options, String source, Long lastModified, Date lastReload) throws ImportException
        {
            options.addMessage("Study reload was initiated by " + source);
            options.setSkipQueryValidation(!study.isValidateQueriesAfterImport());

            // Check for valid reload user
            User reloadUser = options.getUser();

            if (null == reloadUser)
            {
                Integer reloadUserId = study.getReloadUser();

                if (null != reloadUserId)
                    reloadUser = UserManager.getUser(reloadUserId);

                if (null == reloadUser)
                    throw new ImportException("Reload user is not set to a valid user. Update the reload settings on this study to ensure a valid reload user.");

                options.setUser(reloadUser);
                options.addMessage("User \"" + reloadUser.getDisplayName(null) + "\" is configured as the reload user for this study. This can be changed by visiting the \"Manage Reloading\" page.");
            }

            // TODO: Check for inactive user and not sufficient permissions

            // Try to add this study to the reload queue; if it's full, wait until next time
            // We could submit reload pipeline jobs directly, but:
            //  1) we need a way to throttle automatic reloads and
            //  2) the initial import steps happen synchronously; they aren't part of the pipeline job

            // TODO: Better throttling behavior (e.g., prioritize studies that check infrequently)

            // Careful: Naive approach would be to offer the container to the queue and set last reload
            // time on the study only if successful. This will introduce a race condition, since the
            // import job and the update are likely to be updating the study at roughly the same time.
            // Instead, we optimistically update the last reload time before offering the container and
            // back out that change if the queue is full.
            study = study.createMutable();
            study.setLastReload(lastModified == null ? new Date() : new Date(lastModified));
            StudyManager.getInstance().updateStudy(null, study);

            if (QUEUE.offer(options))
            {
                return new ReloadStatus("Reloading " + getDescription(study), true);
            }
            else
            {
                // Restore last reload so we'll try this again later
                study.setLastReload(lastReload);
                StudyManager.getInstance().updateStudy(null, study);
                throw new ImportException("Skipping reload of " + getDescription(study) + ": reload queue is full");
            }
        }
    }


    public static class ReloadStatus
    {
        private final String _message;
        private final boolean _reloadQueued;

        private ReloadStatus(String message, boolean reloadQueued)
        {
            _message = message;
            _reloadQueued = reloadQueued;
        }

        public String getMessage()
        {
            return _message;
        }

        public boolean isReloadQueued()
        {
            return _reloadQueued;
        }
    }


    private static class ReloadThread extends Thread
    {
        private ReloadThread()
        {
            super("Study Reload Handler");
        }

        @Override
        public void run()
        {
            StudyManager manager = StudyManager.getInstance();

            while (true)
            {
                StudyImpl study = null;
                Container c = null;

                try
                {
                    ImportOptions options = QUEUE.take();
                    c = ContainerManager.getForId(options.getContainerId());
                    User reloadUser = options.getUser();
                    PipeRoot root = StudyReload.getPipelineRoot(c);
                    study = manager.getStudy(c);
                    //noinspection ThrowableInstanceNeverThrown
                    BindException errors = new NullSafeBindException(c, "reload");
                    ActionURL manageStudyURL = new ActionURL(StudyController.ManageStudyAction.class, c);

                    LOG.info("Handling " + c.getPath());

                    File studyXml = root.resolvePath("study.xml");

                    // issue 15681: if there is a folder archive instead of a study archive, see if the folder.xml exists to point to the study root dir
                    if (!studyXml.exists())
                    {
                        File folderXml = root.resolvePath("folder.xml");
                        if (folderXml.exists())
                        {
                            FolderImportContext folderCtx = new FolderImportContext(reloadUser, c, folderXml, null, new StaticLoggerGetter(LOG), null);
                            FolderDocument folderDoc = folderCtx.getDocument();
                            if (folderDoc.getFolder().getStudy() != null && folderDoc.getFolder().getStudy().getDir() != null)
                            {
                                studyXml = root.resolvePath("/" + folderDoc.getFolder().getStudy().getDir() + "/study.xml");
                            }
                        }
                    }

                    PipelineService.get().queueJob(new StudyImportJob(c, reloadUser, manageStudyURL, studyXml, studyXml.getName(), errors, root, options));
                }
                catch (InterruptedException e)
                {
                    break;
                }
                catch (Throwable t)
                {
                    String studyDescription = (null != study ? " \"" + getDescription(study) + "\"" : "");
                    String folderPath = (null != c ? " in folder " + c.getPath() : "");
                    LOG.error("Error while reloading study" + studyDescription + folderPath, t);
                }
            }
        }
    }
}
