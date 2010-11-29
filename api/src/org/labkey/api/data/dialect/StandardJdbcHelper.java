/*
 * Copyright (c) 2010 LabKey Corporation
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

        int dbEnd = url.indexOf('?');
        if (-1 == dbEnd)
            dbEnd = url.length();
        int dbDelimiter = url.lastIndexOf('/', dbEnd);
        if (-1 == dbDelimiter)
            dbDelimiter = url.lastIndexOf(':', dbEnd);
        return url.substring(dbDelimiter + 1, dbEnd);
    }
}
