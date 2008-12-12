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
package org.labkey.study;

import org.labkey.api.module.ModuleContext;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.util.UnexpectedException;
import org.labkey.study.importer.SpecimenImporter;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 4:54:36 PM
 */
public class StudyUpgradeCode implements UpgradeCode
{
    // Invoked at version 2.11
    public void upgradeRequirementsTables(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall())
        {
            try
            {
                SampleManager.getInstance().upgradeRequirementsTables();
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
    }

    // Intended for version 8.23, but invoked from afterUpdate(), NOT from script, because the code path relies
    //  too heavily on an up-to-date schema.
    public static void upgradeExtensibleTables_83(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall() && moduleContext.getInstalledVersion() < 8.23)
        {
            try
            {
                StudyUpgrader.upgradeExtensibleTables_83(moduleContext.getUpgradeUser());
            }
            catch (SQLException se)
            {
                throw new RuntimeSQLException(se);
            }
        }
    }

    // Invoked at version 8.29
    public void uniquifyDatasetLabels(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall())
        {
            // We're going to add a unique constraint to dataset labels,
            // so we need to go through and unique-ify any that are not unique.
            ResultSet labelRS = null;
            ResultSet nameRS = null;
            try
            {
                DbSchema schema = StudySchema.getInstance().getSchema();

                String sql = "select num, container, label from (select count(*) as num, container, label\n" +
                    "from study.dataset \n" +
                    "group by container, label) as subselect\n" +
                    "where num > 1";

                labelRS = Table.executeQuery(schema, sql, new Object[0]);
                while(labelRS.next())
                {
                    String container = labelRS.getString("container");
                    String label = labelRS.getString("label");

                    // Now we need to get all the names for those labels
                    sql = "select name \n" +
                        "from study.dataset \n" +
                        "where container = ? AND\n" +
                        "label = ?";

                    nameRS = Table.executeQuery(schema, sql, new Object[]{container, label});

                    while (nameRS.next())
                    {
                        // Make the new label "name: oldLabel"

                        String name = nameRS.getString(1);

                        // We're guaranteed to get two or more of these, so ignore if there's one whose
                        // label matches its name
                        if (name.equals(label))
                            continue;

                        String newLabel = name + ": " + label;

                        sql = "update study.dataset\n" +
                            "set label = ?\n" +
                            "where name = ?\n" +
                            "and container = ?";

                        Table.execute(schema, sql, new Object[]{newLabel, name, container});
                    }
                }
            }
            catch (SQLException se)
            {
                throw UnexpectedException.wrap(se);
            }
            finally
            {
                try {if (labelRS != null) labelRS.close();} catch (SQLException se) {}
                try {if (nameRS != null) nameRS.close();} catch (SQLException se) {}
            }
        }
    }

    // Intended for version 8.291, but invoked from afterUpdate(), NOT from script, because the code path relies
    //  too heavily on an up-to-date schema. 
    public static void updateAllCalculatedSpecimenData(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall() && moduleContext.getInstalledVersion() < 8.291)
        {
            try
            {
                SpecimenImporter.updateAllCalculatedSpecimenData(moduleContext.getUpgradeUser());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
    }
}
