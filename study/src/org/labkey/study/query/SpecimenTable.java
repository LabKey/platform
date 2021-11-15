/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.study.model.StudyImpl;

import java.util.ArrayList;
import java.util.List;

public class SpecimenTable extends AbstractSpecimenTable
{
    private List<SpecimenTable> _studySpecimenTables = null;

    public SpecimenTable(StudyQuerySchema schema, ContainerFilter cf, boolean allStudies)
    {
        super(schema, SpecimenSchema.get().getTableInfoSpecimen(schema.getContainer()), cf, true);

        var ptidColumn = getMutableColumn(StudyService.get().getSubjectColumnName(getContainer()));
//        addWrapColumn(getRealTable().getColumn("RowId"));
//        addContainerColumn(true);
        if (false)                      // If we generate more like VialTable, then we need this
        {
            removeColumn(ptidColumn);
            ptidColumn = new OuterAliasedColumn(this, "ParticipantId", getRealTable().getColumn("PTID"));
            addColumn(ptidColumn);
            addColumn(new OuterAliasedColumn(this, "Date", getRealTable().getColumn("DrawTimeStamp")));
            var aliasVisitColumn = new OuterAliasedColumn(this, "SequenceNum", _rootTable.getColumn("VisitValue"));
            addSpecimenVisitColumn(TimepointType.DATE, aliasVisitColumn, true);
        }
        else
        {
//            addColumn(new AliasedColumn(this, "ParticipantId", getRealTable().getColumn("PTID")));        // Base class already does this.
            addColumn(new AliasedColumn(this, "Date", getRealTable().getColumn("DrawTimeStamp")));
            addSpecimenVisitColumn(TimepointType.DATE, true);
        }
        ptidColumn.clearFk();

        addSpecimenTypeColumns();

        if (allStudies)
        {
            _studySpecimenTables = new ArrayList<>();
            assert null != getUserSchema();
            User user = getUserSchema().getUser();
            for (Study study : StudyService.get().getAllStudies(ContainerManager.getRoot(), user))
            {
                // Commented out lines are for the lie VialTable alternative
//                if (study.getContainer().hasPermission(user, ReadPermission.class))
                if (study.getContainer().hasPermission(user, ReadPermission.class) && study.getContainer().getId() != getContainer().getId())
                {
                    StudyQuerySchema studyQuerySchema = StudyQuerySchema.createSchema((StudyImpl)study, user);
                    SpecimenTable table = new SpecimenTable(studyQuerySchema, null, false);
                    _studySpecimenTables.add(table);
                }
            }
//            _unionColumns = getUnionColumns(_studySpecimenTables);
        }
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        checkReadBeforeExecute();
        SQLFragment sql = new SQLFragment();
        if (null != _studySpecimenTables)
        {
            for (SpecimenTable table : _studySpecimenTables)
                sql.append("(").append(table.getFromSQL("")).append(" UNION ");
        }

        sql.append("\n    SELECT participantsequencenum, additivetypeid, firstprocessedbyinitials, \n")
            .append("       volumeunits, visitdescription, specimenhash, expectedavailablecount, \n")
            .append("       visitvalue, derivativetypeid, rowid, totalvolume, derivativetypeid2, \n")
            .append("       drawtimestamp, vialcount, availablevolume, ptid, primarytypeid, \n")
            .append("       originatinglocationid, atrepositorycount, processinglocation, \n")
            .append("       lockedinrequestcount, subadditivederivative, salreceiptdate, \n")
            .append("       availablecount, classid, protocolnumber,\n")
            .append("       ").append("CAST ('").append(getContainer().getId()).append("' AS ")
            .append(getSchema().getSqlDialect().getGuidType()).append(") AS Container");

        sql.append("\n  FROM ").append(getRealTable().getSelectName());
        if (null != _studySpecimenTables)
        {
            for (SpecimenTable table : _studySpecimenTables)
                sql.append(") ");
        }
        sql.append(alias);
        return sql;
    }

    @Override
    public boolean hasUnionTable()
    {
        return true;
    }
}
