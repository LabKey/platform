/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.util;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;

/**
 * User: arauch
 * Date: Nov 17, 2004
 */
public class ContainerUtil
{
    public static int purgeTable(TableInfo tinfo, String key)
    {
        assert tinfo.getTableType() != DatabaseTableType.NOT_IN_DB;

        TableInfo tinfoContainers = CoreSchema.getInstance().getTableInfoContainers();

        if (null == key)
            key = "Container";

        String delete = "DELETE FROM " + tinfo + " WHERE " + key + " NOT IN (SELECT EntityId FROM " + tinfoContainers + ")";
        return new SqlExecutor(tinfo.getSchema()).execute(delete);
    }


    public static int purgeTable(TableInfo tinfo, Container c, @Nullable String key)
    {
        assert tinfo.getTableType() != DatabaseTableType.NOT_IN_DB;

        if (null == key)
            key = "Container";

        String delete = "DELETE FROM " + tinfo + " WHERE " + key + " = ?";
        return new SqlExecutor(tinfo.getSchema()).execute(delete, c.getId());
    }
}
