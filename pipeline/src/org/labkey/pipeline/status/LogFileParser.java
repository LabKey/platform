/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.pipeline.status;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Parse a pipeline job log file.
 * The log format is initialized in {@link org.labkey.api.pipeline.PipelineJob.OutputLogger#write(String, Throwable, String)} and is:
 * <code>%d{DATE} %-5p: %m%n</code>
 */
public class LogFileParser
{
    private static final Set<String> LOG_LEVELS = Collections.unmodifiableSet(new CaseInsensitiveHashSet(PageFlowUtil.set("DEBUG", "INFO", "WARN", "ERROR", "FATAL")));

    public static List<Record> parseLines(String data)
    {
        if (data.isBlank())
            return Collections.emptyList();

        return parseLines(Arrays.asList(data.split("\r|\n|\r\n")));
    }

    public static List<Record> parseLines(List<String> lines)
    {
        if (lines.isEmpty())
            return Collections.emptyList();

        List<Record> records = new ArrayList<>(lines.size());

        Record current = null;
        for (String line : lines)
        {
            // Exception report 20876 - limit length of processed lines to avoid running out of memory
            final int maxLineLength = 10_000_000;
            if (line.length() > maxLineLength)
            {
                line = line.substring(0, maxLineLength);
            }

            var next = parseLine(line);
            if (next != null)
            {
                // We have a new record
                if (current != null)
                    records.add(current);
                current = next;
            }
            else
            {
                if (current == null)
                {
                    // Found a line that doesn't match the log4j format
                    current = new Record();
                    current.setLevel("INFO");
                }
                else
                {
                    // flag records that look like they contain a stacktrace
                    if (line.startsWith("\tat ") || (line.startsWith("\t... ") && line.endsWith(" more")))
                        current.setStackTrace(true);
                }
                current.addLine(line);
            }
        }

        if (current != null)
            records.add(current);

        return records;
    }

    /**
     * Attempt to parse a pipeline log file line.
     * If it matches the expected format, a new Record is returned, otherwise null.
     */
    private static @Nullable Record parseLine(String line)
    {
        // We expect the fields at the following offsets:
        //  0 - 23 : date + time (24 characters)
        //      24 : space
        // 25 - 29 : level (5 characters)
        //      30 : colon
        //      31 : space
        //   >= 32 : message
        if (line.length() >= 32 &&
                ' ' == line.charAt(24) &&
                ':' == line.charAt(30) &&
                ' ' == line.charAt(31))
        {
            var dateTime = line.substring(0, 24);
            var type = line.substring(25, 30).trim();
            if (LOG_LEVELS.contains(type))
            {
                var record = new Record();
                record.setDateTime(dateTime);
                record.setLevel(type);
                record.addLine(line);
                return record;
            }
        }

        return null;
    }

    public static class Record
    {
        private String _level;
        private String _dateTime;
        private StringBuilder _lines;
        private boolean _stackTrace;
        private boolean _multiline;

        public String getLevel()
        {
            return _level;
        }

        public void setLevel(String level)
        {
            _level = level;
        }

        public String getDateTime()
        {
            return _dateTime;
        }

        public void setDateTime(String dateTime)
        {
            _dateTime = dateTime;
        }

        public void addLine(String line)
        {
            if (_lines == null)
            {
                _lines = new StringBuilder(line);
            }
            else
            {
                _multiline = true;
                _lines.append("\n").append(line);
            }
        }

        public String getLines()
        {
            return _lines == null ? null : _lines.toString();
        }

        public boolean isStackTrace()
        {
            return _stackTrace;
        }

        public void setStackTrace(boolean stackTrace)
        {
            _stackTrace = stackTrace;
        }

        public boolean isMultiline()
        {
            return _multiline;
        }
    }
}
