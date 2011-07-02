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

import java.util.ArrayList;
import java.util.List;

/*
* User: adam
* Date: Jun 29, 2011
* Time: 3:02:47 PM
*/
class SchemaColumnMetaData
{
    private List<ColumnInfo> _columns = new ArrayList<ColumnInfo>();

    SchemaColumnMetaData(List<ColumnInfo> columns)
    {
        _columns = columns;
    }

    List<ColumnInfo> getColumns()
    {
        return _columns;
    }

    void addColumn(ColumnInfo column)
    {
        _columns.add(column);
    }
}
