/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.experiment.api.data;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AbstractMethodInfo;
import org.labkey.api.sql.LabKeySql;

/**
 * method takes 3 params: fieldKey, lsid, depth (optional)
 * <code>
 * SELECT
 *   d.*
 * FROM assay.General.MyAssay.Data d
 * WHERE
    ExpChildOf(d.resultsample.LSID, 'urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522', 1) OR ExpChildOf(d.Run.runsample.LSID, 'urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522', 0)
 * </code>
 */

public class ChildOfMethod extends AbstractMethodInfo
{
    public static final String NAME = "ExpChildOf";

    public ChildOfMethod()
    {
        super(JdbcType.BOOLEAN);
    }


    @Override
    public SQLFragment getSQL(SqlDialect dialect, SQLFragment[] arguments)
    {
        SQLFragment fieldKeyFrag = arguments[0];
        SQLFragment lsidFrag = arguments[1];
        int depth = 0;
        if (arguments.length > 2)
            depth = Integer.parseInt(arguments[2].getRawSQL());

        // ChildOfMethod currently only expects simple strings as second argument, usually we would validate that in
        // SqlParser.convertNode(), but that code doesn't know about this MethodInfo.
        // Unfortunately, this code doesn't 100% guarantee that the second arg was a simple string.
        String raw = lsidFrag.getRawSQL();
        if (!lsidFrag.getParams().isEmpty() || !raw.startsWith("'") || !raw.endsWith("'"))
            throw new IllegalArgumentException("ExpChildOf expects a string as its second argument");
        String lsid = LabKeySql.unquoteString(lsidFrag.getRawSQL());

        return LineageHelper.createInSQL(fieldKeyFrag, lsid, LineageHelper.createChildOfOptions(depth));
    }
}
