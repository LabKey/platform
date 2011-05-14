/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.query;

import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.query.persist.QueryManager;

import java.sql.SQLException;
import java.util.Collections;

/*
* User: Karl Lum
* Date: Dec 10, 2008
* Time: 9:51:32 AM
*/
public class QueryUpgradeCode implements UpgradeCode
{
    // Invoked from script query-9.20-9.21.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void populateDataSourceColumn(ModuleContext ctx) throws SQLException
    {
        if (!ctx.isNewInstall())
        {
            // Get the DataSource name associated with the core schema (in most cases, "labkeyDataSource")
            String dataSourceName = DbScope.getLabkeyScope().getDataSourceName();

            // Update all existing DbUserSchema rows with labkey DataSource name
            // NOTE: Use old name for this table, since we're in the middle of upgrading
            SQLFragment sql = new SQLFragment("UPDATE query.DbUserSchema SET DataSource = ?", Collections.<Object>singletonList(dataSourceName));
            Table.execute(QueryManager.get().getDbSchema(), sql);
        }
    }
}
