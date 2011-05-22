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

package org.labkey.announcements;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Sort;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.UpgradeUtils;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;

/*
* User: adam
* Date: May 21, 2011
* Time: 5:43:46 PM
*/
public class CommUpgradeCode implements UpgradeCode
{
    /* called at 11.10->11.11, PostgreSQL only, to move to case-insensitive UNIQUE INDEX */
    @SuppressWarnings({"UnusedDeclaration"})
    public void uniquifyWikiNames(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        try
        {
            UpgradeUtils.uniquifyValues(DbSchema.get("comm").getTable("Pages").getColumn("Name"), new Sort("RowId"), false, true);
        }
        catch (SQLException se)
        {
            throw UnexpectedException.wrap(se);
        }
    }
}
