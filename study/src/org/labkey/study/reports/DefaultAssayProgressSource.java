/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.study.reports;

import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 7/25/2017.
 */
public class DefaultAssayProgressSource implements AssayProgressReport.AssayData
{
    private AssayProgressReport.AssayExpectation _expectation;
    private Study _study;
    private List<Specimen> _specimen = new ArrayList<>();

    public DefaultAssayProgressSource(AssayProgressReport.AssayExpectation expectation, Study study)
    {
        _expectation = expectation;
        _study = study;
    }

    @Override
    public List<String> getParticipants(ViewContext context)
    {
        SQLFragment sql = new SQLFragment();

        sql.append("SELECT DISTINCT(ParticipantId) FROM study.").append(StudyService.get().getSubjectTableName(context.getContainer()));
        sql.append(" WHERE ParticipantId IS NOT NULL AND Container = ?");
        sql.add(context.getContainer());

        return new SqlSelector(StudySchema.getInstance().getSchema(), sql).getArrayList(String.class);
    }

    @Override
    public List<Visit> getVisits(ViewContext context)
    {
        return _expectation.getExpectedVisits();
    }

    @Override
    public List<Pair<AssayProgressReport.ParticipantVisit, String>> getSpecimenStatus(ViewContext context)
    {
        List<Pair<AssayProgressReport.ParticipantVisit, String>> status = new ArrayList<>();

        for (Visit visit : getVisits(context))
        {
            for (String participant : getParticipants(context))
            {
                status.add(new Pair<>(new AssayProgressReport.ParticipantVisit(participant, visit.getId()), AssayProgressReport.SPECIMEN_EXPECTED));
            }
        }
        return status;
    }

    private void getSpecimenData(Study study, ViewContext context) throws Exception
    {
        if (study != null)
        {
            SQLFragment sql = new SQLFragment();

            StudySchema.getInstance().getTableInfoSpecimenDetail(context.getContainer());

            sql.append("SELECT ParticipantId, Visit.SequenceNumMin, SequenceNum FROM ");
            sql.append(StudySchema.getInstance().getTableInfoSpecimenDetail(context.getContainer()), "");
            sql.append(" WHERE ParticipantId IS NOT NULL AND Visit IS NOT NULL");
            //sql.add(context.getContainer());

            BindException errors = new NullSafeBindException(new Object(), "fake");
            try (Results result = executeSql(sql, StudySchema.getInstance().getSchemaName(), context, errors))
            {
                if (result != null)
                {
                    while (result.next())
                    {
                        String ptid = result.getString("ParticipantId");
                        double sequenceNumMin = result.getDouble("SequenceNumMin");
                        double sequenceNum = result.getDouble("SequenceNum");

                        _specimen.add(new Specimen(ptid, sequenceNumMin, sequenceNum));
                    }
                }
            }
        }
    }

    /**
     * Helper to execute arbitrary LabKey SQL
     */
    private Results executeSql(SQLFragment sql, String schemaName, ViewContext context, BindException errors) throws Exception
    {
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), schemaName);
        QueryDefinition def = QueryService.get().saveSessionQuery(context, context.getContainer(), schemaName, sql.getSQL());
        QuerySettings settings = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT, def.getName());

        QueryView view = schema.createView(context, settings, errors);

        if (view != null)
            return view.getResults();

        return null;
    }

    private static class Specimen
    {
        private String _ptid;
        private double _sequenceNumMin;
        private double _sequenceNum;

        public Specimen(String ptid, double sequenceNumMin, double sequenceNum)
        {
            _ptid = ptid;
            _sequenceNumMin = sequenceNumMin;
            _sequenceNum = sequenceNum;
        }
    }
}
