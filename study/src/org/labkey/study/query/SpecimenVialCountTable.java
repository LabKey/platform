/*
 * Copyright (c) 2009-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.ExprColumn;
import org.labkey.study.SpecimenManager;
import org.labkey.study.StudySchema;

/**
 * User: klum
 * Date: Jun 2, 2009
 */
public class SpecimenVialCountTable extends BaseStudyTable
{
    public SpecimenVialCountTable(StudyQuerySchema schema)
    {
/*        super(schema, StudySchema.getInstance().getTableInfoSpecimenVialCount());

        addWrapColumn(_rootTable.getColumn("Container")).setHidden(true);
        addWrapColumn(_rootTable.getColumn("SpecimenHash")).setHidden(true);

        addColumn(new AliasedColumn(this, "TotalCount", _rootTable.getColumn("VialCount")));

        boolean enableSpecimenRequest = SampleManager.getInstance().getRepositorySettings(getContainer()).isEnableRequests();

        addColumn(new AliasedColumn(this, "LockedInRequest", _rootTable.getColumn("LockedInRequestCount"))).setHidden(!enableSpecimenRequest);
        addColumn(new AliasedColumn(this, "AtRepository", _rootTable.getColumn("AtRepositoryCount")));
        addColumn(new AliasedColumn(this, "Available", _rootTable.getColumn("AvailableCount"))).setHidden(!enableSpecimenRequest);
        addColumn(new AliasedColumn(this, "ExpectedAvailable", _rootTable.getColumn("ExpectedAvailableCount"))).setHidden(!enableSpecimenRequest);
*/
        super(schema, StudySchema.getInstance().getTableInfoVial(schema.getContainer()));
        setTitle("VialCounts");

        addContainerColumn(true).setHidden(true);
        addWrapColumn(_rootTable.getColumn("SpecimenHash")).setHidden(true);

        addColumn(new ExprColumn(this, "TotalCount", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + "VialCount"), JdbcType.INTEGER));

        boolean enableSpecimenRequest = SpecimenManager.getInstance().getRepositorySettings(getContainer()).isEnableRequests();

        addColumn(new ExprColumn(this, "LockedInRequest", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + "LockedInRequestCount"), JdbcType.INTEGER)).setHidden(!enableSpecimenRequest);
        addColumn(new ExprColumn(this, "AtRepository", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + "AtRepositoryCount"), JdbcType.INTEGER));
        addColumn(new ExprColumn(this, "Available", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + "AvailableCount"), JdbcType.INTEGER)).setHidden(!enableSpecimenRequest);
        addColumn(new ExprColumn(this, "ExpectedAvailable", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + "ExpectedAvailableCount"), JdbcType.INTEGER)).setHidden(!enableSpecimenRequest);
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(getContainer());
        if (null == tableInfoVial)
            throw new IllegalStateException("Vial table not found.");

        SQLFragment sql = new SQLFragment("(SELECT ? As Container, Vial.specimenhash, sum(Vial.volume) AS totalvolume,\n" +
                "        sum(CASE Vial.available\n" +
                "            WHEN ? THEN Vial.volume\n" +
                "            ELSE 0\n" +
                "        END) AS availablevolume, count(Vial.globaluniqueid) AS vialcount, sum(\n" +
                "        CASE Vial.lockedinrequest\n" +
                "            WHEN ? THEN 1\n" +
                "            ELSE 0\n" +
                "        END) AS lockedinrequestcount, sum(\n" +
                "        CASE Vial.atrepository\n" +
                "            WHEN ? THEN 1\n" +
                "            ELSE 0\n" +
                "        END) AS atrepositorycount, sum(\n" +
                "        CASE Vial.available\n" +
                "            WHEN ? THEN 1\n" +
                "            ELSE 0\n" +
                "        END) AS availablecount, count(Vial.globaluniqueid) - sum(\n" +
                "        CASE \n" +
                "            CASE Vial.lockedinrequest\n" +
                "                WHEN ? THEN 1\n" +
                "                ELSE 0\n" +
                "            END | \n" +
                "            CASE Vial.requestable\n" +
                "                WHEN ? THEN 1\n" +
                "                ELSE 0\n" +
                "            END\n" +
                "            WHEN 1 THEN 1\n" +
                "            ELSE 0\n" +
                "        END) AS expectedavailablecount\n" +
                "   FROM ");
        sql.append(tableInfoVial.getFromSQL("Vial"))
                .append("\n  GROUP BY Vial.specimenhash) ")
                .append(alias);
        sql.add(getContainer());
        sql.add(Boolean.TRUE);
        sql.add(Boolean.TRUE);
        sql.add(Boolean.TRUE);
        sql.add(Boolean.TRUE);
        sql.add(Boolean.TRUE);
        sql.add(Boolean.FALSE);
        return sql;
    }
}
