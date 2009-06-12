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
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import javax.servlet.ServletContextEvent;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Date;
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

    static
    {
        ContextListener.addShutdownListener(new ShutdownListener() {
            public void shutdownStarted(ServletContextEvent servletContextEvent)
            {
                SCHEDULER.shutdown();
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
            future = SCHEDULER.scheduleAtFixedRate(new ReloadTask(containerId), reasonableInterval, reasonableInterval, TimeUnit.SECONDS);
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


    private static class ReloadTask implements Runnable
    {
        private final String _studyContainerId;

        private ReloadTask(String studyContainerId)
        {
            _studyContainerId = studyContainerId;
        }

        public void run()
        {
            Container c = ContainerManager.getForId(_studyContainerId);

            if (null == c)
            {
                // Container must have been deleted
                cancelTimer(_studyContainerId);
            }
            else
            {
                StudyImpl study = StudyManager.getInstance().getStudy(c);

                if (null == study)
                {
                    // Study must have been deleted
                    cancelTimer(_studyContainerId);
                }
                else
                {
                    boolean shouldReload = study.isAllowReload() && null != study.getReloadInterval() && study.getReloadInterval().intValue() > 0;

                    assert shouldReload : "Shouldn't be attempting reload on a study set for no reload or manual reload";

                    File root = getPipelineRoot(c);

                    if (null == root)
                        return;

                    File studyload = new File(root, "studyload.txt");

                    LOG.info("Checking timestamp on file " + studyload.getAbsolutePath());

                    if (studyload.exists() && studyload.isFile())
                    {
                        long lastModified = studyload.lastModified();

                        if (null == study.getLastReload() || studyload.lastModified() > (study.getLastReload().getTime() + 1000))  // Add a second since SQL Server rounds datetimes
                        {
                            LOG.info("I'm pretending to load the study named " + study.getLabel() + " in folder " + c.getPath());

                            // TODO: queue up a reload here

                            study = study.createMutable();
                            study.setLastReload(new Date(lastModified));
                            
                            try
                            {
                                StudyManager.getInstance().updateStudy(null, study);
                            }
                            catch (SQLException e)
                            {
                                LOG.error("Failed to update last reload timestamp for study " + study.getLabel() + " in folder " + c.getPath());
                            }
                        }
                    }
                }
            }
        }
    }


    // Use a factory so we can name the thread something meaningful
    private static class ReloadThreadFactory implements ThreadFactory
    {
        public Thread newThread(Runnable r)
        {
            return new Thread(r, "Study Reload");
        }
    }
}
