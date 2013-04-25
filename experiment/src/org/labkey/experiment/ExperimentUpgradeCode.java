/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.experiment;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleContext;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 4/9/13
 * Time: 7:12 PM
 */
@SuppressWarnings("UnusedDeclaration")
public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final String LIST_SEQUENCE_NAME = "org.labkey.list.Lists";    // Matches name in ListManager

    public void createListSequences(ModuleContext moduleContext)
    {
        String sql = "SELECT Container, MAX(ListId) AS Max FROM exp.List GROUP BY Container";
        new SqlSelector(ExperimentService.get().getSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                Container c = ContainerManager.getForId(rs.getString(1));

                if (null != c)
                {
                    int max = rs.getInt(2);
                    DbSequence sequence = DbSequenceManager.get(c, LIST_SEQUENCE_NAME);
                    sequence.ensureMinimum(max);
                }
            }
        });
    }
}
