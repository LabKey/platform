/*
 * Copyright (c) 2010-2016 LabKey Corporation
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

package org.labkey.study.visitmanager;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.study.CohortFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.DatasetTableImpl;

/**
 * Avoid bookkeeping on study-managed tables for continuous-style studies, like EHRs.
 * User: kevink
 * Date: Dec 30, 2009 12:13:57 PM
 */
public class AbsoluteDateVisitManager extends RelativeDateVisitManager
{
    public AbsoluteDateVisitManager(StudyImpl study)
    {
        super(study);
    }

    @Override
    public String getLabel()
    {
        return "[no-visit]"; // XXX: for checking all the places in the UI where the label shows up
    }

    @Override
    public String getPluralLabel()
    {
        return "[no-visits]"; // XXX: for checking all the places in the UI where the label shows up
    }

    @Override
    protected SQLFragment getVisitSummarySql(User user, CohortFilter cohortFilter, QCStateSet qcStates, String statsSql, String alias, boolean showAll)
    {
        TableInfo studyData = showAll ?
                StudySchema.getInstance().getTableInfoStudyData(getStudy(), user) :
                StudySchema.getInstance().getTableInfoStudyDataVisible(getStudy(), user);
        TableInfo participantTable = StudySchema.getInstance().getTableInfoParticipant();

        SQLFragment studyDataContainerFilter = new SQLFragment(alias + ".Container = ?", _study.getContainer());
        if (_study.isDataspaceStudy())
            studyDataContainerFilter = new DataspaceContainerFilter(user, _study).getSQLFragment(studyData.getSchema(), new SQLFragment(alias+".Container"),getStudy().getContainer());

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<RelativeDateVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());

        SQLFragment keyCols = new SQLFragment("DatasetId");

        if (cohortFilter == null)
        {
            sql.append("SELECT ").append(keyCols).append(", -1 as visitId").append(statsSql);
            sql.append("\nFROM ").append(studyData.getFromSQL(alias));
            sql.append("\nWHERE ");
            sql.append(studyDataContainerFilter);
            if (null != qcStates)
                sql.append(" AND ").append(qcStates.getStateInClause(DatasetTableImpl.QCSTATE_ID_COLNAME));
            sql.append("\nGROUP BY ").append(keyCols);
            sql.append("\nORDER BY 1, 2");
        }
        else
        {
            switch (cohortFilter.getType())
            {
                case PTID_CURRENT:
                case PTID_INITIAL:
                    sql.append("SELECT ").append(keyCols).append(", -1 as visitId").append(statsSql);
                    sql.append("\nFROM ").append(studyData.getFromSQL(alias))
                            .append("\nJOIN ").append(participantTable.getFromSQL("P")).append(" ON (").append(alias).append(".ParticipantId = P.ParticipantId AND ").append(alias).append(".Container = P.Container)\n");
                    sql.append("\nWHERE ")
                            .append(studyDataContainerFilter)
                            .append(" AND P.").append(cohortFilter.getType() == CohortFilter.Type.PTID_CURRENT ? "CurrentCohortId" : "InitialCohortId")
                            .append(" = ?\n").append(qcStates != null ? "AND " + qcStates.getStateInClause(DatasetTableImpl.QCSTATE_ID_COLNAME) + "\n" : "");
                    sql.add(cohortFilter.getCohortId());
                    sql.append("\nGROUP BY ").append(keyCols);
                    sql.append("\nORDER BY 1, 2");
                    break;
                //case DATA_COLLECTION:
                default:
                    throw new UnsupportedOperationException("Unsupported cohort filter for date-based study: " + cohortFilter.getType());
            }
        }

        sql.appendComment("</AbsoluteDateVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());
        return sql;
    }

    public VisitImpl findVisitBySequence(double seq)
    {
        return null;
    }

    public boolean isVisitOverlapping(VisitImpl visit)
    {
        throw new UnsupportedOperationException("Study has no timepoints");
    }

    @Override
    protected void updateParticipantVisitTable(@Nullable User user, @Nullable Logger logger)
    {
        // no-op
    }

    @Override
    protected void updateVisitTable(User user, @Nullable Logger logger)
    {
        // no-op
    }
}
