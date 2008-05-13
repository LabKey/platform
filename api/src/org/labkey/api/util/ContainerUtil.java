/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

//import org.apache.log4j.Logger;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;

import java.sql.SQLException;


/**
 * User: arauch
 * Date: Nov 17, 2004
 * Time: 3:15:54 PM
 */

public class ContainerUtil
{
//    private static Logger _log = Logger.getLogger(ContainerUtil.class);

    public static int purgeTable(TableInfo tinfo, String key) throws SQLException
    {
        assert tinfo.getTableType() != TableInfo.TABLE_TYPE_NOT_IN_DB;

        TableInfo tinfoContainers = CoreSchema.getInstance().getTableInfoContainers();

        if (null == key)
            key = "Container";

        String delete = "DELETE FROM " + tinfo + " WHERE " + key + " NOT IN (SELECT EntityId FROM " + tinfoContainers + ")";
        return Table.execute(tinfo.getSchema(), delete, null);
    }


    public static int purgeTable(TableInfo tinfo, Container c, String key) throws SQLException
    {
        assert tinfo.getTableType() != TableInfo.TABLE_TYPE_NOT_IN_DB;

        if (null == key)
            key = "Container";

        String delete = "DELETE FROM " + tinfo + " WHERE " + key + " = ?";
        return Table.execute(tinfo.getSchema(), delete, new Object[]{c.getId()});
    }
}
