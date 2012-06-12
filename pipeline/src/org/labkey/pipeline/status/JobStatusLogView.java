/*
 * Copyright (c) 2012 LabKey Corporation
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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ReaderView;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

/**
 * User: cnathe
 * Date: Feb 17, 2012
 */
public class JobStatusLogView extends ReaderView
{
    boolean _highlightingError = false;
    private final boolean _showDetails;
    private String _previousLine;

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
            if (!_showDetails && line.startsWith("\tat "))
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

            String[] tokens = line.split(" ");
            // for a normal log file INFO, ERROR, etc. line, the type will be the 5th token
            if (tokens.length > 4)
            {
                String type = tokens[4];
                if (!_highlightingError && (type.equals("ERROR:") || type.equals("FATAL:")))
                {
                    _highlightingError = true;
                    out.write("<span class=\"labkey-error\">");
                }
                else if (_highlightingError && (type.equals("INFO") || type.equals("DEBUG:") || type.equals("WARN")))
                {
                    _highlightingError = false;
                    out.write("</span>");
                }

                if (type.equals("DEBUG:") && !_showDetails)
                {
                    return;
                }
            }
            super.outputLine(out, line);
        }
        finally
        {
            _previousLine = line;
        }
    }
}
