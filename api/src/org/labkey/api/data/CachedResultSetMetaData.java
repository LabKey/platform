/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.api.data;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/*
* User: adam
* Date: Jun 23, 2011
* Time: 1:56:05 AM
*/

// Proactively reads the ResultSetMetaData and holds onto it, allowing us to close the underlying ResultSet. This is
// needed on database servers that don't allow access to meta data after the result has been closed, such as Oracle.
public class CachedResultSetMetaData extends ResultSetMetaDataImpl
{
    public CachedResultSetMetaData(ResultSetMetaData md) throws SQLException
    {
        super(md.getColumnCount());

        addAllColumns(md);
    }
}
