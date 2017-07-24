package org.labkey.study.reports;

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.model.AssaySpecimenConfigImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Created by klum on 7/13/2017.
 */
public class AssayProgressReport extends AbstractReport
{
    public static final String TYPE = "ReportService.AssayProgressReport";
    private static final String PARTICIPANTS = "participants";
    private static final String VISITS = "visits";

    private Map<String, Map<String, Object>> _assayData = new HashMap<>();

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        return "Assay Progress Report";
    }

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        getAssayData(context);

        JspView<Map<String, Map<String, Object>>> view = new JspView<>("/org/labkey/study/view/assayProgressReport.jsp", _assayData);

        return view;
    }

    private void getAssayData(ViewContext context)
    {
        Study study = StudyService.get().getStudy(context.getContainer());

        // assay expectations from the assay schedule
        List<AssayExpectation> assayExpectations = new TableSelector(StudySchema.getInstance().getTableInfoAssaySpecimen(), TableSelector.ALL_COLUMNS, SimpleFilter.createContainerFilter(context.getContainer()), null).getCollection(AssayExpectation.class)
                .stream()
                .collect(Collectors.toList());

        // get all current visits in the study
        Map<Integer, Visit> visitMap = study.getVisits(Visit.Order.CHRONOLOGICAL)
                .stream()
                .collect(Collectors.toMap(Visit::getId, e -> e));

        ListValuedMap<Integer, Integer> scheduledVisits = new ArrayListValuedHashMap<>();
        new TableSelector(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(), TableSelector.ALL_COLUMNS, SimpleFilter.createContainerFilter(context.getContainer()), null)
                .forEach(rs ->
        {
            Integer specimenId = rs.getInt("AssaySpecimenId");
            Integer visitId = rs.getInt("VisitId");

            scheduledVisits.put(specimenId, visitId);
        });

        for (AssayExpectation assay : assayExpectations)
        {
            List<Visit> assayVisits = new ArrayList<>();
            for (Integer visitId : scheduledVisits.get(assay.getRowId()))
            {
                if (visitMap.containsKey(visitId))
                    assayVisits.add(visitMap.get(visitId));
            }

            assayVisits.sort((o1, o2) -> (int) (o1.getSequenceNumMin() - o2.getSequenceNumMin()));
            assay.setExpectedVisits(assayVisits);
        }

        for (AssayExpectation assay : assayExpectations)
        {
            getStandardAssayData(assay, context);

            //getCustomAssayData(assay, context);
        }
    }

    private void getStandardAssayData(AssayExpectation assayExpectation, ViewContext context)
    {
        Study study = StudyService.get().getStudy(context.getContainer());

        // get participants
        SQLFragment sql = new SQLFragment();

        sql.append("SELECT DISTINCT(ParticipantId) FROM study.").append(StudyService.get().getSubjectTableName(context.getContainer()));
        sql.append(" WHERE ParticipantId IS NOT NULL AND Container = ?");
        sql.add(context.getContainer());

        List<String>  participants = new SqlSelector(StudySchema.getInstance().getSchema(), sql).getArrayList(String.class);

        Map<String, Object> data = new HashMap<>();
        List<String> visits = assayExpectation.getExpectedVisits().stream()
                .map(Visit::getLabel)
                .collect(Collectors.toList());

        data.put(VISITS, visits);
        data.put(PARTICIPANTS, participants);

        _assayData.put(assayExpectation.getAssayName(), data);
    }

    private void getCustomAssayData(AssayExpectation assay, ViewContext context)
    {
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "Lists");
        if (schema == null)
            throw new NotFoundException("Schema not found");

        TableInfo tableInfo = schema.getTable("progressReport");
        if (tableInfo == null)
            throw new NotFoundException("Assay progress report status not found for " + assay.getAssayName());

        Map<String, Object> data = new HashMap<>();
        Set<String> visits = new TreeSet<>();
        Set<String> participants = new TreeSet<>();

        data.put(VISITS, visits);
        data.put(PARTICIPANTS, participants);

        _assayData.put(assay.getAssayName(), data);
        new TableSelector(tableInfo).forEach(rs ->
        {
            String visit = rs.getString("Visit");
            String participant = rs.getString("ParticipantID");
            String status = rs.getString("Status");

            if (visit != null && participant != null && status != null)
            {
                visits.add(visit);
                participants.add(participant);
            }
        });
    }

    public static class AssayExpectation extends AssaySpecimenConfigImpl
    {
        private List<Visit> _expectedVisits;

        public List<Visit> getExpectedVisits()
        {
            return _expectedVisits;
        }

        public void setExpectedVisits(List<Visit> expectedVisits)
        {
            _expectedVisits = expectedVisits;
        }
    }
}
