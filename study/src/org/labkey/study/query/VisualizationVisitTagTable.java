/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;

public class VisualizationVisitTagTable extends VirtualTable
{
    private final StudyImpl _study;
    private final User _user;
    private final boolean _useProtocolDay;
    private final String _visitTagName;
    private final String _dayString;
    private final String _altQueryName;

    public VisualizationVisitTagTable(StudyImpl study, User user, String visitTagName, boolean useProtocolDay, String altQueryName)
    {
        super(StudySchema.getInstance().getSchema(), "VizVisitTag");
        _study = study;
        _user = user;
        _visitTagName = visitTagName;
        _useProtocolDay = useProtocolDay;
        if (_useProtocolDay)
            _dayString = "ProtocolDay";
        else
            _dayString = "Day";
        _altQueryName = altQueryName;

        addColumn(new ExprColumn(this, StudyService.get().getSubjectColumnName(_study.getContainer()),
                  new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + "ParticipantId"), JdbcType.VARCHAR));

        // 20546: need to expose Container for use in StudyVisualizationProvider.getJoinColumns
        addColumn(new ExprColumn(this, "Container", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + "Container"), JdbcType.VARCHAR));

        addColumn(new ExprColumn(this, "ZeroDay", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + _dayString), JdbcType.INTEGER));
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        if (_altQueryName == null)
        {
            String innerAlias = alias + "_SP";
            String joinString;
            if (_useProtocolDay)
            {
                joinString = "\nJOIN study.Visit ON " + innerAlias + ".VisitId = study.Visit.RowId AND " +
                        innerAlias + ".VisitId IS NOT NULL";
            }
            else
            {
                joinString = "\nJOIN study.ParticipantVisit ON " + innerAlias + ".VisitId = study.ParticipantVisit.VisitRowId AND " +
                        innerAlias + ".ParticipantId = study.ParticipantVisit.ParticipantId";
            }

            SQLFragment from = new SQLFragment("(SELECT VisitId, " + _dayString + ", " + innerAlias + ".ParticipantId, Container " + " FROM (SELECT ParticipantId, ");
            from.append("COALESCE(CohortVisitTag.VisitId, NoCohortTag.VisitId) As VisitId, CurrentCohortId FROM study.Participant\n")
                    .append("LEFT OUTER JOIN study.VisitTagMap CohortVisitTag ON study.Participant.CurrentCohortId = CohortVisitTag.CohortID AND CohortVisitTag.VisitTag=")
                    .append("'" + _visitTagName + "'\n")
                    .append("AND CohortVisitTag.Container = Participant.Container");
            from.append("\nLEFT OUTER JOIN study.VisitTagMap NoCohortTag ON NoCohortTag.VisitTag =")
                    .append("'" + _visitTagName + "' AND NoCohortTag.CohortID IS NULL\n")
                    .append("AND NoCohortTag.Container = Participant.Container");
            from.append("\nWHERE ");

            // TODO: this is a temp fix for the Dataspace usecase
            from.append(new ContainerFilter.AllInProject(_user).getSQLFragment(getSchema(), new SQLFragment("study.Participant.Container"), _study.getContainer()));

            from.append(") ").append(innerAlias);
            from.append(joinString);
            from.append("\n) ").append(alias);
            return from;
        }
        else
        {
            // allow caller to pass in their own query to use for the SQL to get the participant-to-zero day map (used for CDS study axis alignment by visit tag)
            // NOTE: it is assumed that the query will have the expected columns plus a column for VisitTagMap to filter on
            return new SQLFragment("(SELECT * FROM " + _altQueryName + " WHERE VisitTagName = '" + _visitTagName + "')").append(alias);
        }
    }
}
