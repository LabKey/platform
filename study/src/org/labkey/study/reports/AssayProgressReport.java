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

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.AssaySpecimenConfigImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by klum on 7/13/2017.
 */
public class AssayProgressReport extends AbstractReport
{
    public static final String REPORT_LABEL = "Assay Progress Report";

    public static final String TYPE = "ReportService.AssayProgressReport";
    public static final String PARTICIPANTS = "participants";
    public static final String VISITS = "visits";
    public static final String VISITS_LABELS = "visitLabels";
    public static final String HEAT_MAP = "heatMap";
    public static final String LEGEND = "legend";
    public static final String NAME = "name";

    public static final String SPECIMEN_EXPECTED = "expected";
    public static final String SPECIMEN_COLLECTED = "collected";
    public static final String SPECIMEN_NOT_COLLECTED = "not-collected";
    public static final String SPECIMEN_NOT_RECEIVED = "not-received";
    public static final String SPECIMEN_AVAILABLE = "available";
    public static final String SPECIMEN_NOT_AVAILABLE = "not-available";
    public static final String SPECIMEN_UNUSABLE = "unusable";
    public static final String SPECIMEN_RESULTS_UNEXPECTED = "unexpected";

    private SetValuedMap<String, ParticipantVisit> _copiedToStudyData = new HashSetValuedHashMap<>();

    public enum SpecimenStatus
    {
        EXPECTED(SPECIMEN_EXPECTED, "Expected", "Expected", "fa fa-circle-o"),
        COLLECTED(SPECIMEN_COLLECTED, "Collected", "Collected", "fa fa-flask"),
        NOT_COLLECTED(SPECIMEN_NOT_COLLECTED, "Not collected", "Specimen not collected", "fa fa-ban"),
        NOT_RECIEVED(SPECIMEN_NOT_RECEIVED, "Not received", "Specimen collected but not received", "fa fa-exclamation"),
        AVAILABLE(SPECIMEN_AVAILABLE, "Results available", "Results available", "fa fa-check-circle"),
        INVALID(SPECIMEN_NOT_AVAILABLE, "Results unavailable", "Collected and received but no data", "fa fa-warning"),
        UNUSABLE(SPECIMEN_UNUSABLE, "Unusable", "Unusable", "fa fa-trash-o"),
        UNEXPECTED(SPECIMEN_RESULTS_UNEXPECTED, "Unexpected results", "Needs QC Check", "fa fa-flag");

        private String _name;
        private String _lable;
        private String _iconClass;
        private String _description;

        SpecimenStatus(String name, String label, String description, String iconClass)
        {
            _name = name;
            _lable = label;
            _description = description;
            _iconClass = iconClass;
        }

        public String getName()
        {
            return _name;
        }

        public String getLabel()
        {
            return _lable;
        }

        public String getIconClass()
        {
            return _iconClass;
        }

        public String getDescription()
        {
            return _description;
        }

        @Nullable
        public static SpecimenStatus getForName(String name)
        {
            for (SpecimenStatus status : SpecimenStatus.values())
            {
                if (status.getName().equals(name))
                    return status;
            }
            return null;
        }

        public static List<Map<String, String>> serialize()
        {
            List<Map<String, String>> data = new ArrayList<>();

            for (SpecimenStatus status : SpecimenStatus.values())
            {
                data.add(PageFlowUtil.map("name", status.getName(),
                        "label", status.getLabel(),
                        "description", status.getDescription(),
                        "icon-class", status.getIconClass()));
            }
            return data;
        }
    }

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        return REPORT_LABEL;
    }

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        BindException errors = new NullSafeBindException(this, "form");
        return new JspView<>("/org/labkey/study/view/renderAssayProgressReport.jsp", new AssayReportBean(getReportId(), getAssayReportData(context, errors)), errors);
    }

    @NotNull
    public Map<Integer, Map<String, Object>> getAssayReportData(ViewContext context, BindException errors)
    {
        Study study = StudyService.get().getStudy(context.getContainer());

        Map<Integer, Map<String, Object>> assayData = new LinkedHashMap<>();

        _copiedToStudyData.clear();

        // assay expectations from the assay schedule
        List<AssayExpectation> assayExpectations = new TableSelector(StudySchema.getInstance().getTableInfoAssaySpecimen(), TableSelector.ALL_COLUMNS, SimpleFilter.createContainerFilter(context.getContainer()), new Sort(FieldKey.fromParts("AssayName"))).getArrayList(AssayExpectation.class);

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

        // get the saved assay configs, includes the query to source the specimen status information
        String jsonData = getDescriptor().getProperty(ReportDescriptor.Prop.json);
        Map<Integer, JSONObject> assayConfigMap = new HashMap<>();
        if (jsonData != null)
        {
            JSONArray assays = new JSONArray(jsonData);
            if (assays != null)
            {
                for (JSONObject assay : assays.toJSONObjectArray())
                {
                    String rowId = assay.getString("RowId");
                    String assayName = assay.getString("AssayName");
                    String schemaName = assay.getString("schemaName");
                    String queryName = assay.getString("queryName");

                    if (rowId != null && assayName != null && schemaName != null && queryName != null)
                        assayConfigMap.put(NumberUtils.toInt(rowId), assay);
                }
            }
        }

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

            if (assayConfigMap.containsKey(assay.getRowId()))
            {
                JSONObject assayConfig = assayConfigMap.get(assay.getRowId());

                String folder = assayConfig.getString("folderId");
                if (folder != null)
                {
                    Container container = ContainerManager.getForId(folder);
                    if (container != null)
                        assay.setQueryFolder(container);
                }
                assay.setSchemaName(assayConfig.getString("schemaName"));
                assay.setQueryName(assayConfig.getString("queryName"));
            }
        }

        try
        {
            for (AssayExpectation assay : assayExpectations)
            {
                AssayData assaySource = createAssayDataSource(study, assay);
                Map<String, Object> data = new HashMap<>();

                getDatasetData(assay, study, context);

                List<Integer> visits = assaySource.getVisits(context).stream()
                        .map(Visit::getId)
                        .collect(Collectors.toList());
                List<String> visitLabels = assaySource.getVisits(context).stream()
                        .map(Visit::getDisplayString)
                        .collect(Collectors.toList());

                data.put(NAME, assay.getAssayName());
                data.put(VISITS, visits);
                data.put(VISITS_LABELS, visitLabels);
                data.put(PARTICIPANTS, assaySource.getParticipants(context));

                // create the heat map data
                data.put(HEAT_MAP, createHeatMapData(context, assay, assaySource));

                assayData.put(assay.getRowId(), data);
            }
        }
        catch (Exception e)
        {
            errors.addError(new LabKeyError(e));
        }

        return assayData;
    }

    AssayData createAssayDataSource(Study study, AssayExpectation expectation)
    {
        if (expectation.getSchemaName() != null && expectation.getQueryName() != null)
            return new QueryAssayProgressSource(expectation, study);
        else
            return new DefaultAssayProgressSource(expectation, study);
    }

    Map<String, Map<String, String>> createHeatMapData(ViewContext context, AssayExpectation assay, AssayData assayData)
    {
        Map<String, Map<String, String>> heatmap = new HashMap<>();

        for (Pair<ParticipantVisit, String> status : assayData.getSpecimenStatus(context))
        {
            SpecimenStatus specimenStatus = SpecimenStatus.getForName(status.getValue());
            if (specimenStatus != null)
            {
                heatmap.put(status.getKey().getKey(), PageFlowUtil.map("iconcls", specimenStatus.getIconClass(),
                        "tooltip", specimenStatus.getDescription(),
                        "status", specimenStatus.getName()));
            }
            else
                throw new IllegalStateException("Specimen status : " + status.getValue() + " is not a valid status code");
        }

        // results available (copied to study)
        SpecimenStatus available = SpecimenStatus.getForName(SPECIMEN_AVAILABLE);
        for (ParticipantVisit visit : _copiedToStudyData.get(assay.getAssayName()))
        {
            heatmap.put(visit.getKey(), PageFlowUtil.map("iconcls", available.getIconClass(),
                    "tooltip", available.getDescription(),
                    "status", available.getName()));
        }
        return heatmap;
    }

    private void getDatasetData(AssayExpectation assay, Study study, ViewContext context)
    {
        if (assay.getDataset() != null)
        {
            Dataset dataset = study.getDataset(assay.getDataset());
            if (dataset != null)
            {
                UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), StudySchema.getInstance().getSchemaName());
                if (schema == null)
                    throw new NotFoundException("Schema not found");

                TableInfo tableInfo = schema.getTable(dataset.getName());
                if (tableInfo != null)
                {
                    new TableSelector(tableInfo, PageFlowUtil.set("ParticipantId", "SpecimenId", "VisitRowId")).forEachResults(rs -> {

                        String ptid = rs.getString(FieldKey.fromParts("participantId"));
                        //String specimenId = rs.getString(FieldKey.fromParts("specimenId"));
                        Integer visitId = rs.getInt(FieldKey.fromParts("visitRowId"));

                        if (ptid != null && visitId != 0)
                        {
                            Visit visit = StudyManager.getInstance().getVisitForRowId(study, visitId);
                            if (visit != null)
                            {
                                _copiedToStudyData.put(assay.getAssayName(), new ParticipantVisit(ptid, visitId));
                            }
                        }
                    });
                }
            }
        }
    }

    @Nullable
    @Override
    public ActionURL getEditReportURL(ViewContext context)
    {
        ActionURL url = new ActionURL(ReportsController.AssayProgressReportAction.class, context.getContainer());
        url.addParameter("reportId", getReportId().toString());

        return url;
    }

    public static class AssayReportBean
    {
        private ReportIdentifier _id;
        private Map<Integer, Map<String, Object>> _assayData;

        public AssayReportBean(ReportIdentifier id, Map<Integer, Map<String, Object>> assayData)
        {
            _id = id;
            _assayData = assayData;
        }

        public ReportIdentifier getId()
        {
            return _id;
        }

        public Map<Integer, Map<String, Object>> getAssayData()
        {
            return _assayData;
        }
    }

    public static class AssayExpectation extends AssaySpecimenConfigImpl
    {
        private List<Visit> _expectedVisits;
        private Container _queryFolder;
        private String _schemaName;
        private String _queryName;

        public List<Visit> getExpectedVisits()
        {
            return _expectedVisits;
        }

        public void setExpectedVisits(List<Visit> expectedVisits)
        {
            _expectedVisits = expectedVisits;
        }

        public Container getQueryFolder()
        {
            return _queryFolder;
        }

        public void setQueryFolder(Container queryFolder)
        {
            _queryFolder = queryFolder;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }
    }

    /**
     * Represents the information about an assay that would be required to generate the assay
     * specific progress report.
     */
    public interface AssayData
    {
        List<String> getParticipants(ViewContext context);
        List<Visit> getVisits(ViewContext context);
        List<Pair<ParticipantVisit, String>> getSpecimenStatus(ViewContext context);
    }

    public static class ParticipantVisit
    {
        private String _ptid;
        private int _visitId;

        public ParticipantVisit(String ptid, int visitId)
        {
            _ptid = ptid;
            _visitId = visitId;
        }

        public String getKey()
        {
            return getKey(_ptid, _visitId);
        }

        public static String getKey(String ptid, int visitId)
        {
            return ptid + "|" + visitId;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ParticipantVisit that = (ParticipantVisit) o;

            if (_visitId != that._visitId) return false;
            return _ptid != null ? _ptid.equals(that._ptid) : that._ptid == null;
        }

        @Override
        public int hashCode()
        {
            int result = _ptid != null ? _ptid.hashCode() : 0;
            result = 31 * result + _visitId;
            return result;
        }
    }
}
