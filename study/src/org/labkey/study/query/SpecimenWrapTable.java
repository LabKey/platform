/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.*;
import org.labkey.api.util.Path;
import org.labkey.study.StudySchema;


public class SpecimenWrapTable extends BaseStudyTable
{
    private Path _notificationKey;
    private SpecimenWrapTable _unionTable;

    public SpecimenWrapTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenDetail(schema.getContainer()), true, true);

        for (ColumnInfo columnInfo : _rootTable.getColumns())
            if (!"container".equalsIgnoreCase(columnInfo.getName()))
                addWrapColumn(columnInfo);

        addContainerColumn(true);
        _notificationKey = new Path("study", getClass().getName(), getName());
    }

    public void setUnionTable(SpecimenWrapTable unionTable)
    {
        _unionTable = unionTable;
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        TableInfo vialTI = StudySchema.getInstance().getTableInfoVial(getContainer());
        TableInfo specimenTI = StudySchema.getInstance().getTableInfoSpecimen(getContainer());

        SQLFragment sqlf = new SQLFragment();
        if (null != _unionTable)
        {
            sqlf.append("(").append(_unionTable.getFromSQL("")).append(" UNION ");
        }
        sqlf.append("(SELECT vial.rowid, vial.globaluniqueid, vial.volume, vial.specimenhash, \n" +
                " vial.requestable, vial.currentlocation, vial.atrepository, vial.lockedinrequest, vial.available, vial.processinglocation, \n" +
                " vial.specimenid, vial.primaryvolume, vial.primaryvolumeunits, vial.firstprocessedbyinitials, vial.availabilityreason,\n" +
                "  vial.totalcellcount, vial.tubetype, vial.latestcomments, vial.latestqualitycomments, vial.latestdeviationcode1, \n" +
                "  vial.latestdeviationcode2, vial.latestdeviationcode3, vial.latestconcentration, vial.latestintegrity, vial.latestratio,\n" +
                "   vial.latestyield, vial.freezer, vial.fr_container, vial.fr_position, vial.fr_level1, vial.fr_level2,\n" +
                "    specimen.ptid, specimen.participantsequencenum, specimen.totalvolume, specimen.availablevolume, \n" +
                "    specimen.visitdescription, specimen.visitvalue, specimen.volumeunits, specimen.primarytypeid, specimen.additivetypeid, \n" +
                "    specimen.derivativetypeid, specimen.derivativetypeid2, specimen.subadditivederivative, specimen.drawtimestamp, \n" +
                "    specimen.salreceiptdate, specimen.classid, specimen.protocolnumber, specimen.originatinglocationid, specimen.vialcount, \n" +
                "    specimen.lockedinrequestcount, specimen.atrepositorycount, specimen.availablecount, specimen.expectedavailablecount,\n\t");
        sqlf.append(SpecimenDetailTable.getContainerSql(getSchema())).append("\n   FROM ").add(getContainer());
        sqlf.append(vialTI.getFromSQL("vial"));
        sqlf.append("\n  JOIN ");
        sqlf.append(specimenTI.getFromSQL("specimen"));
        sqlf.append(" ON vial.specimenid = specimen.rowid) ");
        if (null != _unionTable)
            sqlf.append(") ");
        sqlf.append(alias);
        return sqlf;
    }

    public Path getNotificationKey()
    {
        return _notificationKey;
    }
}
