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

import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.StudyManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Assay progress report information that is sourced from a custom query
 */
public class QueryAssayProgressSource implements AssayProgressReport.AssayData
{
    private final AssayProgressReport.AssayExpectation _expectation;
    private final Study _study;
    private final List<String> _participants = new ArrayList<>();
    private final List<Pair<AssayProgressReport.ParticipantVisit, String>> _specimenStatus = new ArrayList<>();

    public QueryAssayProgressSource(AssayProgressReport.AssayExpectation expectation, Study study)
    {
        _expectation = expectation;
        _study = study;
    }

    @Override
    public List<Pair<AssayProgressReport.ParticipantVisit, String>> getSpecimenStatus(ViewContext context)
    {
        ensureResults(context);
        return _specimenStatus;
    }

    @Override
    public List<String> getParticipants(ViewContext context)
    {
        ensureResults(context);
        return _participants;
    }

    @Override
    public List<Visit> getVisits(ViewContext context)
    {
        return _expectation.getExpectedVisits();
    }

    private void ensureResults(ViewContext context)
    {
        if (_participants.isEmpty())
        {
            UserSchema schema = QueryService.get().getUserSchema(context.getUser(), _expectation.getQueryFolder() != null ? _expectation.getQueryFolder() : context.getContainer(),
                    _expectation.getSchemaName());
            if (schema != null)
            {
                Set<String> participants = new HashSet<>();

                TableInfo tableInfo = schema.getTable(_expectation.getQueryName());
                if (tableInfo != null)
                {
                    final String format = "%s is a required field from the source query: %s/%s";
                    if (tableInfo.getColumn(FieldKey.fromParts("ParticipantId")) == null)
                        throw new RuntimeException(String.format(format, "ParticipantId", _expectation.getSchemaName(), _expectation.getQueryName()));
                    if (tableInfo.getColumn(FieldKey.fromParts("SequenceNum")) == null)
                        throw new RuntimeException(String.format(format, "SequenceNum", _expectation.getSchemaName(), _expectation.getQueryName()));
                    if (tableInfo.getColumn(FieldKey.fromParts("Status")) == null)
                        throw new RuntimeException(String.format(format, "Status", _expectation.getSchemaName(), _expectation.getQueryName()));

                    Set<PtidSequenceNum> uniqueResults = new HashSet<>();
                    new TableSelector(tableInfo).forEach(rs ->
                    {
                        String ptid = rs.getString("ParticipantId");
                        BigDecimal sequenceNum = rs.getBigDecimal("SequenceNum");
                        String status = rs.getString("Status");

                        if (ptid != null)
                        {
                            participants.add(ptid);
                            if (sequenceNum != null && status != null)
                            {
                                // find the visit associated with the sequence number
                                Visit visit = StudyManager.getInstance().getVisitForSequence(_study, sequenceNum);
                                if (visit != null)
                                {
                                    PtidSequenceNum ptidSequenceNum = new PtidSequenceNum(ptid, sequenceNum);
                                    if (!uniqueResults.contains(ptidSequenceNum))
                                        uniqueResults.add(ptidSequenceNum);
                                    else
                                        throw new RuntimeException("Duplicate rows found for ParticipantID/SequenceNum: (" + ptid + ", " + sequenceNum + ")");

                                    _specimenStatus.add(new Pair<>(new AssayProgressReport.ParticipantVisit(ptid, visit.getId()), status));
                                }
                            }
                        }
                    });
                    _participants.addAll(participants);
                    _participants.sort(String::compareTo);
                }
                else
                {
                    throw new RuntimeException("Unable to access the configured query: " + _expectation.getQueryName());
                }
            }
            else
            {
                throw new RuntimeException("Unable to access the configured schema schema: " + _expectation.getSchemaName());
            }
        }
    }

    private static class PtidSequenceNum
    {
        private final String _ptid;
        private final BigDecimal _sequenceNum;

        public PtidSequenceNum(String ptid, BigDecimal sequenceNum)
        {
            _ptid = ptid;
            _sequenceNum = sequenceNum;
        }

        @Override
        public int hashCode()
        {
            return _ptid.hashCode() + (31 * _sequenceNum.hashCode());
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof PtidSequenceNum)
            {
                // Note: BigDecimal.compareTo() means this equals() check ignores scale... I THINK that's right
                return ((PtidSequenceNum)obj)._ptid.equals(_ptid) && ((PtidSequenceNum)obj)._sequenceNum.compareTo(_sequenceNum) == 0;
            }
            return false;
        }
    }
}
