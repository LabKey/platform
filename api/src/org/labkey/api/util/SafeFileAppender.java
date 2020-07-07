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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;

/**
 * Custom Log4J appender that opens the log file and closes it for each logging operation, thus ensuring
 * that the file does not stay locked.
 * Created: Oct 18, 2005
 * @author bmaclean
 */
@Plugin(name = "SafeFile", category = "Core", elementType = "appender", printObject = true)
public class SafeFileAppender extends AbstractAppender
{
    private static Logger _log = LogManager.getLogger(SafeFileAppender.class);
    private final String LINE_SEP = System.getProperty("line.separator");
    private static File _file;

    public SafeFileAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties)
    {
        super(name, filter, layout, ignoreExceptions, properties);
    }

    @PluginFactory
    public static SafeFileAppender createAppender(@PluginAttribute("name") String name,
                                                  @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
                                                  @PluginElement("Layout") Layout layout,
                                                  @PluginElement("Filters") Filter filter,
                                                  File file)
    {
        _file = file;

        // Make sure that we try to mount the drive (if needed) before using the file
        NetworkDrive.exists(_file);
        return new SafeFileAppender(name, filter, layout, ignoreExceptions, null);
    }

    @Override
    public void append(LogEvent loggingEvent)
    {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(_file, true)))
        {
            writer.write(loggingEvent.getMessage().getFormattedMessage());
            writer.write(LINE_SEP);
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

}
