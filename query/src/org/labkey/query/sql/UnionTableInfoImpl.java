/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.query.sql;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.UnionTableInfo;

import java.util.ArrayList;
import java.util.List;

/*
* User: Karl Lum
* Date: Mar 5, 2009
* Time: 3:46:41 PM
*/
public class UnionTableInfoImpl extends QueryTableInfo implements UnionTableInfo
{
    protected List<ColumnInfo> _unionColumns = new ArrayList<>();

    public UnionTableInfoImpl(QueryRelation r, String name)
    {
        super(r, name);
    }

    public void addUnionColumn(ColumnInfo col)
    {
        _unionColumns.add(col);
    }

    public List<ColumnInfo> getUnionColumns()
    {
        return _unionColumns;
    }
}