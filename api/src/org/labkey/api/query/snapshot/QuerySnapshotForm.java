/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.query.snapshot;

import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryForm;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.ObjectUtils;

import java.util.List;
import java.util.ArrayList;
/*
 * User: Karl Lum
 * Date: Jul 18, 2008
 * Time: 3:59:21 PM
 */

public class QuerySnapshotForm extends QueryForm
{
    private String _snapshotName;
    private String[] _snapshotColumns = new String[0];
    private int _updateDelay;

    public void init(QuerySnapshotDefinition def)
    {
        if (def != null)
        {
            setSnapshotName(def.getName());
            List<FieldKey> columns = def.getColumns();
            String[] columnNames = new String[columns.size()];
            int i=0;

            for (FieldKey fk : columns)
                columnNames[i++] = fk.toString();

            setSnapshotColumns(columnNames);

            QueryDefinition qDef = def.getQueryDefinition();
            if (qDef != null)
            {
                setQueryName(qDef.getName());
                setSchemaName(qDef.getSchemaName());
            }

            setUpdateDelay(def.getUpdateDelay());
        }
    }

    public String getSnapshotName()
    {
        return _snapshotName;
    }

    public void setSnapshotName(String snapshotName)
    {
        _snapshotName = snapshotName;
    }

    public String[] getSnapshotColumns()
    {
        return _snapshotColumns;
    }

    public void setSnapshotColumns(String[] snapshotColumns)
    {
        _snapshotColumns = snapshotColumns;
    }

    public int getUpdateDelay()
    {
        return _updateDelay;
    }

    public void setUpdateDelay(int updateDelay)
    {
        String type = ObjectUtils.toString(getViewContext().get("updateType"), "");
        if (type.equals("manual"))
            _updateDelay = 0;
        else
            _updateDelay = updateDelay;
    }

    public List<FieldKey> getFieldKeyColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();
        for (String name : getSnapshotColumns())
            columns.add(FieldKey.fromString(name));

        return columns;
    }
}