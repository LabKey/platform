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
package org.labkey.api.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;

import static org.labkey.api.util.StringUtilsLabKey.DEFAULT_CHARSET;

/**
 * Custom Log4J appender that opens the log file and closes it for each logging operation, thus ensuring
 * that the file does not stay locked.
 * Created: Oct 18, 2005
 * @author bmaclean
 */
@Plugin(name = FileAppender.PLUGIN_NAME, category = Core.CATEGORY_NAME, elementType = FileAppender.ELEMENT_TYPE, printObject = true)
public class SafeFileAppender extends AbstractAppender
{
    private static Logger _log = LogManager.getLogger(SafeFileAppender.class);
    private final String LINE_SEP = System.getProperty("line.separator");
    private static File _file;
    private PipelineJob _job;
    private Logger _jobLogger;
    private boolean _isSettingStatus;

    public SafeFileAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties, PipelineJob job)
    {
        super(name, filter, layout, ignoreExceptions, properties);
        _job = job;
        _jobLogger = job.getClassLogger();
    }

    @PluginFactory
    public static SafeFileAppender createAppender(@PluginAttribute("name") String name,
                                                  @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
                                                  @PluginElement("Layout") Layout<? extends Serializable> layout,
                                                  @PluginElement("Filters") Filter filter,
                                                  File file,
                                                  PipelineJob job)
    {
        _file = file;

        // Make sure that we try to mount the drive (if needed) before using the file
        NetworkDrive.exists(_file);
        return new SafeFileAppender(name, filter, layout, ignoreExceptions, null, job);
    }

    @Override
    public void append(LogEvent loggingEvent)
    {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(_file, true)))
        {
            logJobMessage(loggingEvent, loggingEvent.getThrown());
            writer.write(new String(getLayout().toByteArray(loggingEvent), DEFAULT_CHARSET));
            writer.write(LINE_SEP);
            if (null != loggingEvent.getThrown())
            {
                StackTraceElement[] stackTraceElements = loggingEvent.getThrown().getStackTrace();
                if (stackTraceElements != null)
                {
                    for (StackTraceElement stackTraceElement : stackTraceElements)
                    {
                        writer.write(stackTraceElement.toString());
                        writer.write(LINE_SEP);
                    }
                }
            }
        }
        catch (IOException e)
        {
            File parentFile = _file.getParentFile();
            if (parentFile != null && !NetworkDrive.exists(parentFile) && parentFile.mkdirs())
                append(loggingEvent);
            else
                _log.error("Failed appending to file.", e);
        }
    }

    private void logJobMessage(LogEvent logEvent, @Nullable Throwable t)
    {
        if (logEvent.getLevel().compareTo(Level.DEBUG) == 0)
        {
            _jobLogger.debug(getSystemLogMessage(logEvent.getMessage()), t);
        }

        if (logEvent.getLevel().compareTo(Level.INFO) == 0)
        {
            _jobLogger.info(getSystemLogMessage(logEvent.getMessage()), t);
        }

        if (logEvent.getLevel().compareTo(Level.WARN) == 0)
        {
            _jobLogger.warn(getSystemLogMessage(logEvent.getMessage()), t);
        }

        if (logEvent.getLevel().compareTo(Level.ERROR) == 0)
        {
            _jobLogger.error(getSystemLogMessage(logEvent.getMessage()), t);
            setErrorStatus(logEvent.getMessage());
        }

        if (logEvent.getLevel().compareTo(Level.FATAL) == 0)
        {
            _jobLogger.fatal(getSystemLogMessage(logEvent.getMessage()), t);
            setErrorStatus(logEvent.getMessage());
        }
    }

    private String getSystemLogMessage(Object message)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("(from pipeline job log file ");
        sb.append(_file.getPath());
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
            _job.setStatus(PipelineJob.TaskStatus.error, message == null ? "ERROR" : message.toString());
        }
        finally
        {
            _isSettingStatus = false;
        }
    }

}
