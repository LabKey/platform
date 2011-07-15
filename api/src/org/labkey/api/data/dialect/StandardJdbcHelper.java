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
/*
        int dbEnd = url.indexOf('?');
        int oraCredsSepIndex = url.indexOf("@");
        int dbDelimiter = -1;

        if (-1 == dbEnd) {
            dbEnd = url.length();
        }

        // To get the oracle db name and avoid reading the labkey.xml file,
        // set the delimiter to be the last index of ':' before the @ seperator
        // and the end being the forward slash (where the password appears after)

        // Oracle JDBC URL syntax can be seen here: http://www.orafaq.com/wiki/JDBC

        if (oraCredsSepIndex != -1 && url.indexOf(":@") == -1) {
            dbEnd = url.indexOf("/");
            dbDelimiter = url.substring(0, oraCredsSepIndex).lastIndexOf(":");

        } else {
            dbDelimiter = url.lastIndexOf('/', dbEnd);
        }


        if (-1 == dbDelimiter) {
            dbDelimiter = url.lastIndexOf(':', dbEnd);
        }


        return url.substring(dbDelimiter + 1, dbEnd);

        // Old Code left incase my code is no good
*/
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
