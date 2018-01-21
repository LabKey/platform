/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.data.queryprofiler;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.data.DbScope;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.WebPartView;

import javax.servlet.http.HttpServlet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
* User: jeckels
* Date: 2/13/14
*/
public class Query
{
    private final @Nullable
    DbScope _scope;
    private final String _sql;
    private final @Nullable
    List<Object> _parameters;
    private final long _elapsed;
    private final StackTraceElement[] _stackTrace;
    private final boolean _isRequestThread;
    private Boolean _validSql = null;

    Query(@Nullable DbScope scope, String sql, @Nullable List<Object> parameters, long elapsed, StackTraceElement[] stackTrace, boolean isRequestThread)
    {
        _scope = scope;
        _sql = sql;
        _parameters = null != parameters ? new ArrayList<>(parameters) : null;    // Make a copy... callers might modify the collection
        _elapsed = elapsed;
        _stackTrace = stackTrace;
        _isRequestThread = isRequestThread;
    }

    @Nullable
    public DbScope getScope()
    {
        return _scope;
    }

    public String getSql()
    {
        // Do any transformations on the SQL on the way out, in the background thread
        return transform(_sql);
    }

    public boolean isValidSql()
    {
        if (null == _validSql)
            throw new IllegalStateException("Must call getSql() before calling isValidSql()");

        return _validSql;
    }

    @Nullable
    public List<Object> getParameters()
    {
        return _parameters;  // TODO: Check parameters? Ignore InputStream, BLOBs, etc.?
    }

    public long getElapsed()
    {
        return _elapsed;
    }

    public String getStackTrace()
    {
        StringBuilder sb = new StringBuilder();

        for (int i = 3; i < _stackTrace.length; i++)
        {
            String line = _stackTrace[i].toString();

            // Ignore all the servlet container stuff, #11159
            // Ignore everything before HttpView.render, standard action classes, etc., #13753
            if  (
                    line.startsWith(HttpView.class.getName() + ".render") ||
                    line.startsWith("org.labkey.jsp.compiled.org.labkey.core.view.template.bootstrap.PageTemplate_jsp._jspService") ||
                    line.startsWith(WebPartView.class.getName() + ".renderInternal") ||
                    line.startsWith(JspView.class.getName() + ".renderView") ||
                    line.startsWith(SimpleViewAction.class.getName() + ".handleRequest") ||
                    line.startsWith(FormViewAction.class.getName() + ".handleRequest") ||
                    line.startsWith("org.junit.internal.runners.TestMethodRunner.executeMethodBody") ||
                    line.startsWith("org.apache.catalina.core.ApplicationFilterChain.internalDoFilter") ||
                    line.startsWith(HttpServlet.class.getName() + ".service")
                )
                break;

            sb.append("at ");  // Improves compatibility with IntelliJ "Analyze Stacktrace" feature
            sb.append(line);
            sb.append('\n');
        }

        return sb.toString();
    }

    public boolean isRequestThread()
    {
        return _isRequestThread;
    }


    private static final Pattern TEMP_TABLE_PATTERN = Pattern.compile("([ix_|temp\\.][\\w]+)\\$?\\p{XDigit}{32}");
    private static final Pattern SPECIMEN_TEMP_TABLE_PATTERN = Pattern.compile("(SpecimenUpload)\\d{9}");
    private static final int MAX_SQL_LENGTH = 1000000;  // Arbitrary limit to avoid saving insane SQL, #16646

    // Transform the SQL to improve coalescing, etc.
    private String transform(String in)
    {
        String out;

        if (in.length() > MAX_SQL_LENGTH)
        {
            out = in.substring(0, MAX_SQL_LENGTH);
        }
        else
        {
            // Remove the randomly-generated parts of temp table names
            out = TEMP_TABLE_PATTERN.matcher(in).replaceAll("$1");
            out = SPECIMEN_TEMP_TABLE_PATTERN.matcher(out).replaceAll("$1");
        }

        _validSql = out.equals(in);   // If we changed the SQL then it's no longer valid

        return out;
    }
}
