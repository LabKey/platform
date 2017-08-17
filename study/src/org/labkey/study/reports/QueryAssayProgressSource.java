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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assay progress report information that is sourced from a custom query
 */
public class QueryAssayProgressSource implements AssayProgressReport.AssayData
{
    private AssayProgressReport.AssayExpectation _expectation;
    private Study _study;
    private List<String> _participants = new ArrayList<>();
    private List<Visit> _visits = new ArrayList<>();
    private List<Pair<AssayProgressReport.ParticipantVisit, String>> _specimenStatus = new ArrayList<>();

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
                Set<Integer> visits = new HashSet<>();

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

                    new TableSelector(tableInfo).forEach(rs ->
                    {
                        String ptid = rs.getString("ParticipantId");
                        Double sequenceNum = rs.getDouble("SequenceNum");
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
                                    visits.add(visit.getId());
                                    _specimenStatus.add(new Pair<>(new AssayProgressReport.ParticipantVisit(ptid, visit.getId()), status));
                                }
                            }
                        }
                    });
                    _participants.addAll(participants);
                    _participants.sort(String::compareTo);

                    Map<Integer, Visit> visitMap = new HashMap<>();
                    for (Visit visit : _study.getVisits(Visit.Order.DISPLAY))
                    {
                        visitMap.put(visit.getId(), visit);
                    }

                    for (Integer visitId : visits)
                    {
                        if (visitMap.containsKey(visitId))
                            _visits.add(visitMap.get(visitId));
                    }
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
}
