/*
 * Copyright (c) 2009 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyImportException;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;

import javax.servlet.ServletContextEvent;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.*;

/*
* User: adam
* Date: Jun 10, 2009
* Time: 5:36:01 PM
*/
public class StudyReload
{
    private static final Logger LOG = Logger.getLogger(StudyReload.class);
    private static final Map<String, Future> FUTURES = new ConcurrentHashMap<String, Future>();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1, new ReloadThreadFactory());
    private static final BlockingQueue<Container> QUEUE = new ArrayBlockingQueue<Container>(100);       // Container IDs instead?
    private static final Thread RELOAD_THREAD = new ReloadThread();

    static
    {
        RELOAD_THREAD.start();

        ContextListener.addShutdownListener(new ShutdownListener() {
            public void shutdownStarted(ServletContextEvent servletContextEvent)
            {
                SCHEDULER.shutdown();
                RELOAD_THREAD.interrupt();
            }
        });
    }

    public enum ReloadInterval
    {
        Never(0, "<Never>", "never"),
        Hours24(24 * 60 * 60, "24 Hours", "once a day"),
        Hours1(60 * 60, "1 Hour", "once an hour"),
        Minutes5(5 * 60, "5 Minutes", "every five minutes"),
        Seconds10(10, true, "10 Seconds", "every 10 seconds");   // for dev mode purposes only

        private String _dropDownLabel;
        private String _description;
        private Integer _seconds;
        private boolean _devOnly;

        private ReloadInterval(Integer seconds, String dropDownLabel, String description)
        {
            this(seconds, false, dropDownLabel, description);
        }

        private ReloadInterval(Integer seconds, boolean devOnly, String dropDownLabel, String description)
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
        filter.addCondition("AllowReload", true);
        filter.addCondition("ReloadInterval", 0, CompareType.GT);

        ResultSet rs = null;

        try
        {
            rs = Table.select(tinfo, tinfo.getColumns("Container, ReloadInterval"), filter, null);

            while (rs.next())
            {
                initializeTimer(rs.getString(1), true, rs.getInt(2));
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
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
        Future future = FUTURES.get(containerId);

        if (null != future)
        {
            future.cancel(true);
            FUTURES.remove(containerId);
        }

        if (allowReload && null != secondsInterval && 0 != secondsInterval.intValue())
        {
            int reasonableInterval = Math.max(secondsInterval.intValue(), 10);
            future = SCHEDULER.scheduleAtFixedRate(new ReloadTask(containerId), reasonableInterval /* TODO: Randomize this? */, reasonableInterval, TimeUnit.SECONDS);
            FUTURES.put(containerId, future);
        }
    }


    @Nullable
    public static File getPipelineRoot(Container c)
    {
        PipeRoot root = PipelineService.get().findPipelineRoot(c);

        if (null != root)
            return root.getRootPath();
        else
            return null;
    }


    public static class ReloadTask implements Runnable
    {
        private static final String STUDY_LOAD_FILENAME = "studyload.txt";

        private final String _studyContainerId;

        public ReloadTask(String studyContainerId)
        {
            _studyContainerId = studyContainerId;
        }

        public void run()
        {
            try
            {
                attemptReload();    // Ignore success messages
            }
            catch (SQLException e)
            {
                LOG.error("SQLException saving study reload state", e);
            }
            catch (StudyImportException e)
            {
                LOG.error("Error reloading study: " + e.getMessage());
            }
        }

        public ReloadStatus attemptReload() throws SQLException, StudyImportException
        {
            Container c = ContainerManager.getForId(_studyContainerId);

            if (null == c)
            {
                // Container must have been deleted
                cancelTimer(_studyContainerId);
                throw new StudyImportException("Container " + _studyContainerId + " does not exist");
            }
            else
            {
                StudyImpl study = StudyManager.getInstance().getStudy(c);

                if (null == study)
                {
                    // Study must have been deleted
                    cancelTimer(_studyContainerId);
                    throw new StudyImportException("Study does not exist in folder " + c.getPath());
                }
                else
                {
                    assert study.isAllowReload() : "Can't reload a study set for no reload";

                    File root = getPipelineRoot(c);

                    if (null == root)
                        throw new StudyImportException("Pipeline root is not set in folder " + c.getPath());

                    if (!root.exists())
                        throw new StudyImportException("Pipeline root does not exist in folder " + c.getPath());

                    File studyload = new File(root, STUDY_LOAD_FILENAME);

                    if (studyload.exists() && studyload.isFile())
                    {
                        long lastModified = studyload.lastModified();
                        Date lastReload = study.getLastReload();

                        if (null == lastReload || studyload.lastModified() > (lastReload.getTime() + 1000))  // Add a second since SQL Server rounds datetimes
                        {
                            // Try to add this study to the reload queue; if it's full, wait until next time
                            // We could submit reload pipeline jobs directly, but:
                            //  1) we need a way to throttle automatic reloads and
                            //  2) the initial import steps happen synchronously; they aren't part of the pipeline job

                            // TODO: Better throttling behavior (e.g., prioritize studies that check infrequently)

                            // Careful: Naive approach would be to offer the container to the queue and set last reload
                            // time on the study only if successful.  This will introduce a race condition, since the
                            // import job and the update are likely to be updating the study at roughly the same time.
                            // Instead, we optimistically update the last reload time before offering the container and
                            // back out that change if the queue is full.
                            study = study.createMutable();
                            study.setLastReload(new Date(lastModified));
                            StudyManager.getInstance().updateStudy(null, study);

                            if (QUEUE.offer(c))
                            {
                                return new ReloadStatus("Reloading " + getDescription(study), true);
                            }
                            else
                            {
                                // Restore last reload so we'll try this again later
                                study.setLastReload(lastReload);
                                StudyManager.getInstance().updateStudy(null, study);
                                throw new StudyImportException("Skipping reload of " + getDescription(study) + ": reload queue is full");
                            }
                        }
                        else
                        {
                            return new ReloadStatus("Skipping reload of " + getDescription(study) + ": this study is up-to-date", false);
                        }
                    }
                    else
                    {
                        throw new StudyImportException("Could not find file " + STUDY_LOAD_FILENAME + " in the pipeline root for " + getDescription(study));
                    }
                }
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


    // Use a factory so we can name the thread something meaningful
    private static class ReloadThreadFactory implements ThreadFactory
    {
        public Thread newThread(Runnable r)
        {
            return new Thread(r, "Study Reload Scheduler");
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
                    c = QUEUE.take();
                    File root = StudyReload.getPipelineRoot(c);
                    study = manager.getStudy(c);
                    //noinspection ThrowableInstanceNeverThrown
                    BindException errors = new BindException(c, "reload");
                    ActionURL manageStudyURL = new ActionURL(StudyController.ManageStudyAction.class, c);

                    User reloadUser = null;
                    Integer reloadUserId = study.getReloadUser();

                    if (null != reloadUserId)
                        reloadUser = UserManager.getUser(reloadUserId.intValue());

                    if (null == reloadUser)
                        throw new StudyImportException("Reload user is not set to a valid user. Update the reload settings on this study to ensure a valid reload user.");

                    LOG.info("Handling " + c.getPath());
                    StudyImporter importer = new StudyImporter(c, reloadUser, manageStudyURL, new File(root, "study.xml"), errors);
                    importer.process();
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
