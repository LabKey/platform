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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDomainKind;
import org.labkey.study.model.QCStateSet;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.DatasetTableImpl;
import org.labkey.study.query.StudyQuerySchema;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Avoid bookkeeping on study-managed tables for continuous-style studies, like EHRs.
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
    @Nullable
    protected SQLFragment getVisitSummarySql(StudyQuerySchema sqs, CohortFilter cohortFilter, QCStateSet qcStates, Set<VisitStatistic> stats, boolean showAll)
    {
        String alias = "SD";
        FilteredTable studyData = sqs.getStudyDatasetsUnion(showAll);
        if (null == studyData)
            return null;
        ColumnInfo datasetId = studyData.getColumn("DatasetId");
        if (null == datasetId)
            datasetId = studyData.getColumn("Dataset");
        ColumnInfo container = studyData.getColumn("Container");
        ColumnInfo participant = studyData.getColumn("ParticipantId");
        if (null == participant)
            participant = studyData.getColumn(sqs.getSubjectColumnName());
        ColumnInfo sequencenum = studyData.getColumn("SequenceNum");
        assert null!=datasetId && null!=container && null!=participant && null!=sequencenum;

        String participantTableName = StudyService.get().getSubjectTableName(sqs.getContainer());
        TableInfo participantTable = sqs.getTable(participantTableName);

        SQLFragment sql = new SQLFragment();
        sql.appendComment("<RelativeDateVisitManager.getVisitSummarySql>", participantTable.getSqlDialect());

        SQLFragment keyCols = datasetId.getValueSql(alias);
        SQLFragment selectCols = new SQLFragment(keyCols).append(", -1 as VisitId");
        for (var stat : stats)
            selectCols.append(",").append(stat.getSql(participant.getValueSql(alias)));

        if (cohortFilter == null)
        {
            sql.append("SELECT ").append(selectCols);
            sql.append("\nFROM ").append(studyData.getFromSQL(alias));
            if (null != qcStates)
                sql.append("\nWHERE ").append(qcStates.getStateInClause(DatasetTableImpl.QCSTATE_ID_COLNAME));
            sql.append("\nGROUP BY ").append(keyCols);
            sql.append("\nORDER BY 1, 2");
        }
        else
        {
            switch (cohortFilter.getType())
            {
                case PTID_CURRENT:
                case PTID_INITIAL:
                    sql.append("SELECT ").append(selectCols);
                    sql.append("\nFROM ").append(studyData.getFromSQL(alias))
                            .append("\nJOIN ").append(participantTable.getFromSQL("P")).append(" ON (")
                            .append(participant.getValueSql(alias)).append(" = P.ParticipantId AND ")
                            .append(container.getValueSql(alias)).append(" = P.Container)\n");
                    sql.append("\nWHERE ")
                            .append(" P.").append(cohortFilter.getType() == CohortFilter.Type.PTID_CURRENT ? "CurrentCohortId" : "InitialCohortId")
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

    @Override
    public VisitImpl findVisitBySequence(BigDecimal seq)
    {
        return null;
    }

    @Override
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
    protected @NotNull ValidationException updateVisitTable(User user, @Nullable Logger logger, boolean failForUndefinedVisits)
    {
        // no-op
        return new ValidationException();
    }
}
