/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.labkey.api.view.ReaderView;

import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Set;

/**
 * User: cnathe
 * Date: Feb 17, 2012
 */
public class JobStatusLogView extends ReaderView
{
    boolean _highlightingError = false;
    private final boolean _showDetails;
    private String _previousLine;
    private String _previousLogLevel;

    private static final Set<String> ERROR_LOG_LEVELS = new CaseInsensitiveHashSet(PageFlowUtil.set("ERROR", "FATAL"));
    private static final Set<String> LOG_LEVELS = new CaseInsensitiveHashSet(PageFlowUtil.set("DEBUG", "INFO", "WARN", "ERROR", "FATAL"));

    public JobStatusLogView(InputStream in, boolean showDetails, @Nullable String prefix, @Nullable String suffix)
    {
        super(in, true, prefix, suffix);
        _showDetails = showDetails;
    }

    @Override
    public void outputLine(PrintWriter out, String line)
    {
        try
        {
            if (!_showDetails && (line.startsWith("\tat ") || (line.startsWith("\t... ") && line.endsWith(" more"))))
            {
                // Hide stack traces in summary views
                return;
            }

            if (_previousLine != null && _highlightingError && !_showDetails)
            {
                int index = _previousLine.indexOf("ERROR:");
                if (index != -1)
                {
                    String message = _previousLine.substring(index + "ERROR:".length()).trim();
                    if (line.endsWith(": " + message))
                    {
                        return;
                    }
                }
            }

            String logLevel;
            String[] tokens = line.split(" ");
            // for a normal log file INFO, ERROR, etc. line, the type will be the 5th token
            if (tokens.length > 4)
            {
                String type = tokens[4];
                if (type.endsWith(":"))
                {
                    // Strip off trailing : if present
                    type = type.substring(0, type.indexOf(":"));
                }
                if (LOG_LEVELS.contains(type))
                {
                    if (!_highlightingError && ERROR_LOG_LEVELS.contains(type))
                    {
                        _highlightingError = true;
                        out.write("<span class=\"labkey-error\">");
                    }
                    else if (_highlightingError)
                    {
                        _highlightingError = false;
                        out.write("</span>");
                    }
                    logLevel = type;
                    _previousLogLevel = type;
                }
                else
                {
                    logLevel = _previousLogLevel;
                }                    
            }
            else
            {
                logLevel = _previousLogLevel;
            }

            if ("DEBUG".equalsIgnoreCase(logLevel) && !_showDetails)
            {
                return;
            }
            out.write("<pre class=\"labkey-log-file\">");

            // Exception report 20876 - limit length of processed lines to avoid running out of memory
            final int maxLineLength = 10_000_000;
            if (line.length() > maxLineLength)
            {
                line = line.substring(0, maxLineLength);
            }

            super.outputLine(out, line);
            out.write("</pre>");
        }
        finally
        {
            _previousLine = line;
        }
    }
}
