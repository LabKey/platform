/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

package org.labkey.api.data.dialect;

import javax.servlet.ServletException;

public class StandardJdbcHelper implements JdbcHelper
{
    private final String _prefix;

    public StandardJdbcHelper(String prefix)
    {
        _prefix = prefix;
    }

    @Override
    public String getDatabase(String url) throws ServletException
    {
        if (!url.startsWith(_prefix))
            throw new ServletException("Unsupported connection url: " + url);

        return parseDatabase(url.substring(_prefix.length()));
    }

    protected String parseDatabase(String url)
    {
        // Database name ends with '?' or end of the URL
        int dbEnd = url.indexOf('?');

        if (-1 == dbEnd)
            dbEnd = url.length();

        // Database name starts after the last '/' or ':' 
        int slash = url.lastIndexOf('/', dbEnd);
        int colon = url.lastIndexOf(':', dbEnd);

        int dbDelimiter = Math.max(slash, colon);

        return url.substring(dbDelimiter + 1, dbEnd);
    }
}
