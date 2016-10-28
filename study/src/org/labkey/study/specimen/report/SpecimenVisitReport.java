/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.study.specimen.report;

import org.labkey.api.study.Study;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.SpecimenManager;
import org.labkey.study.CohortFilter;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;

import java.util.*;

/**
 * User: brittp
 * Created: Jan 14, 2008 10:14:50 AM
 */
public abstract class SpecimenVisitReport<CELLDATA extends SpecimenReportCellData>
{
    private Collection<Row> _rows;
    protected String _title;
    private List<VisitImpl> _visits;
    private List<VisitImpl> _nonEmptyVisits;
    protected Container _container;
    protected SimpleFilter _filter;
    private SpecimenVisitReportParameters _parameters;
    private boolean _viewVialCount = false;
    private boolean _viewParticipantCount = false;
    private boolean _viewVolume = false;
    private boolean _viewPtidList = false;
    private Map<Integer, Integer> _nonEmptyColumns = new HashMap<>();

    public SpecimenVisitReport(String titlePrefix, List<VisitImpl> visits, SimpleFilter filter, SpecimenVisitReportParameters parameters)
    {
        _visits = visits;
        _filter = filter;
        _parameters = parameters;
        _container = parameters.getContainer();
        _viewParticipantCount = parameters.isViewParticipantCount();
        _viewVolume = parameters.isViewVolume();
        _viewPtidList = parameters.isViewPtidList();
        // We'll show the vial count if it's explicitly requested or if nothing else has been selected:
        _viewVialCount = parameters.isViewVialCount() || (!_viewParticipantCount && !_viewVolume && !_viewPtidList);
        _title = titlePrefix + getTitleSuffix();
    }

    public Collection<Row> getRows()
    {
        if (_rows == null)
            _rows = createRows();
        return _rows;
    }

    protected abstract Collection<Row> createRows();

    public String getTitle()
    {
        return _title;
    }

    protected ActionURL updateURLFilterParameter(ActionURL url, String columnBase, Object value)
    {
        url.deleteParameter(columnBase + "~eq");
        url.deleteParameter(columnBase + "~isblank");
        if (value == null)
            url.addParameter(columnBase + "~isblank", null);
        else
            url.addParameter(columnBase + "~eq", value.toString());
        return url;
    }

    public List<VisitImpl> getVisits()
    {
        // ensure rows and non-empty columns have been generated
        getRows();
        if (!_parameters.isHideEmptyColumns())
            return _visits;
        else
        {
            if (_nonEmptyVisits == null)
            {
                List<VisitImpl> visits = new ArrayList<>();
                for (VisitImpl visit : _visits)
                {
                    if (_nonEmptyColumns.containsKey(visit.getRowId()))
                        visits.add(visit);
                }
                _nonEmptyVisits = new ArrayList<>(visits);
            }
            return _nonEmptyVisits;
        }
    }

    protected void setVisitAsNonEmpty(Integer visit)
    {
        if (!_nonEmptyColumns.containsKey(visit))
            _nonEmptyColumns.put(visit, visit);
    }

    protected abstract String getCellHtml(VisitImpl visit, CELLDATA summary);

    protected abstract String[] getCellExcelText(VisitImpl visit, CELLDATA summary);

    public boolean isNumericData()
    {
        int valuesDisplayed =(_parameters.isViewVialCount() ? 1 : 0) +
                (_parameters.isViewParticipantCount() ? 1 : 0) +
                (_parameters.isViewVolume() ? 1 : 0);
        return (valuesDisplayed == 1 && !_parameters.isViewPtidList());
    }

    private String getTitleSuffix()
    {
        StringBuilder suffixBuilder = new StringBuilder();
        if (isViewVialCount())
            suffixBuilder.append(" (Vial Count");
        if (isViewVolume())
        {
            suffixBuilder.append(suffixBuilder.length() > 0 ? "/" : " (");
            suffixBuilder.append("Total Volume");
        }
        if (isViewParticipantCount())
        {
            suffixBuilder.append(suffixBuilder.length() > 0 ? "/" : " (");
            suffixBuilder.append(PageFlowUtil.filter(StudyService.get().getSubjectNounSingular(getContainer()))).append(" Count");
        }
        if (isViewPtidList())
        {
            suffixBuilder.append(suffixBuilder.length() > 0 ? ", " : " (");
            suffixBuilder.append(PageFlowUtil.filter(StudyService.get().getSubjectColumnName(getContainer()))).append(" Group");
        }
        suffixBuilder.append(")");
        return suffixBuilder.toString();
    }

    private SimpleFilter geBaseViewFilter()
    {
        SimpleFilter fullFilter =  new SimpleFilter();
        fullFilter.addAllClauses(_filter);
        // This is a terrible hack to deal with the fact that the some SpecimenDetail columns have been aliased
        // in the query view.  As a result, we need to use the view's column name for filtering
        // at the database layer, and then map this column name for use in a query view filter parameter:
        fullFilter = replaceFilterParameterName(fullFilter, "PrimaryTypeId", FieldKey.fromParts("PrimaryType", "RowId").toString());
        fullFilter = replaceFilterParameterName(fullFilter, "DerivativeTypeId", FieldKey.fromParts("DerivativeType", "RowId").toString());
        fullFilter = replaceFilterParameterName(fullFilter, "AdditiveTypeId", FieldKey.fromParts("AdditiveType", "RowId").toString());
        fullFilter = replaceFilterParameterName(fullFilter, "ptid", StudyService.get().getSubjectColumnName(getContainer()));
        return fullFilter;
    }

    protected void addCohortURLFilter(Study study, ActionURL url)
    {
        if (_parameters.getCohortFilter() != null)
            _parameters.getCohortFilter().addURLParameters(study, url, null);
    }

    protected String getFilterQueryString(VisitImpl visit, CELLDATA summary)
    {
        ActionURL url = new ActionURL();
        if (_parameters.getBaseCustomViewName() != null && _parameters.getBaseCustomViewName().length() > 0)
        {
            if (!SpecimenVisitReportParameters.DEFAULT_VIEW_ID.equals(_parameters.getBaseCustomViewName()))
                url.addParameter("SpecimenDetail.viewName",  _parameters.getBaseCustomViewName());
        }
        else
            url.addParameter("SpecimenDetail.ignoreFilter", "1");


        Study study = StudyManager.getInstance().getStudy(visit.getContainer());
        if (null != study)
            addCohortURLFilter(study, url);

        if (_parameters.getParticipantGroupFilter() >= 0)
        {
            ParticipantGroup filterGroup = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), _parameters.getParticipantGroupFilter());
            if (filterGroup != null)
                filterGroup.addURLFilter(url, getContainer(), "SpecimenDetail");
        }

        switch (_parameters.getStatusFilter())
        {
            case ALL:
            case AVAILABLE:
            case UNAVAILABLE:
            case REQUESTED_INPROCESS:
            case NOT_REQUESTED_INPROCESS:
                // We don't need to add any special URL parameter here: these filters are just based on columns
                // in SpecimenDetail, so the query string should be populated correctly due to  geBaseViewFilter().toQueryString().
                break;
            case NOT_REQUESTED_COMPLETE:
                url.addParameter(SpecimenQueryView.PARAMS.showNotCompleteRequestedOnly, "true");
                break;
            case REQUESTED_COMPLETE:
                url.addParameter(SpecimenQueryView.PARAMS.showCompleteRequestedOnly, "true");
                break;
        }

        String baseParams = geBaseViewFilter().toQueryString("SpecimenDetail");
        if (baseParams != null && baseParams.length() > 0)
            return baseParams + "&" + url.getQueryString();
        else
            return url.getQueryString();
    }

    public int getLabelDepth()
    {
        Collection<Row> rows = getRows();
        if (rows.isEmpty())
            return 0;
        return rows.iterator().next().getTitleHierarchy().length;
    }

    private static SpecimenReportTitle[] getSpecimenReportTitle(String[] stringHierarchy)
    {
        SpecimenReportTitle[] titleHierarchy = new SpecimenReportTitle[stringHierarchy.length];

        for (int i = 0; i < titleHierarchy.length; i++)
            titleHierarchy[i] = new SpecimenReportTitle(stringHierarchy[i]);

        return titleHierarchy;
    }

    public class Row
    {
        private final SpecimenReportTitle[] _titleHierarchy;
        private Map<Integer, CELLDATA> _visitData = new HashMap<>();

        public Row(String[] stringHierarchy)
        {
            this(getSpecimenReportTitle(stringHierarchy));
        }

        public Row(SpecimenReportTitle[] titleHierarchy)
        {
            _titleHierarchy = titleHierarchy;
        }

        public String getCellHtml(VisitImpl visit)
        {
            CELLDATA summary = _visitData.get(visit.getRowId());
            return SpecimenVisitReport.this.getCellHtml(visit, summary);
        }

        public String[] getCellExcelText(VisitImpl visit)
        {
            CELLDATA summary = _visitData.get(visit.getRowId());
            return SpecimenVisitReport.this.getCellExcelText(visit, summary);
        }

        public int getMaxExcelRowHeight(List<VisitImpl> visits)
        {
            int max = 1;
            for (VisitImpl visit : visits)
            {
                int currentHeight = getCellExcelText(visit).length;
                if (currentHeight > max)
                    max = currentHeight;
            }
            return max;
        }

        public SpecimenReportTitle[] getTitleHierarchy()
        {
            return _titleHierarchy;
        }

        public void add(CELLDATA summary)
        {
            _visitData.put(summary.getVisit(), summary);
        }
    }

    protected SimpleFilter replaceFilterParameterName(SimpleFilter filter, String oldKey, String newKey)
    {
        FieldKey oldFieldKey = FieldKey.fromString(oldKey);
        for (SimpleFilter.FilterClause clause : filter.getClauses())
        {
            if (clause.getFieldKeys().size() == 1 && oldFieldKey.equals(clause.getFieldKeys().get(0)))
            {
                if (clause.getParamVals().length > 1)
                    throw new UnsupportedOperationException("Only single filters are supported on column " + newKey);
                filter.deleteConditions(oldFieldKey);
                if (clause.getParamVals().length != 1)
                    throw new UnsupportedOperationException("Reports that provide custom SQL filters must override getFilterQueryString.");
                filter.addCondition(newKey, clause.getParamVals()[0]);
                return filter;
            }
        }
        return filter;
    }

    protected boolean hasContent(String[] strArray)
    {
        if (strArray == null || strArray.length == 0)
            return false;
        for (String str : strArray)
        {
            if (str != null && str.length() > 0)
                return true;
        }
        return false;
    }

    protected String buildCellHtml(VisitImpl visit, CELLDATA summary, String linkHtml)
    {
        String[] summaryString = getCellExcelText(visit, summary);
        StringBuilder cellHtml = new StringBuilder();
        if (hasContent(summaryString))
        {
            if (linkHtml != null)
                cellHtml.append("<a href=\"").append(linkHtml).append("\">");
            boolean first = true;
            for (String str : summaryString)
            {
                if (str != null && str.length() > 0)
                {
                    if (!first)
                        cellHtml.append("<br>");
                    first = false;
                    cellHtml.append(str);
                }
            }
            if (linkHtml != null)
                cellHtml.append("</a>");
        }
        return cellHtml.toString();
    }

    protected Container getContainer()
    {
        return _parameters.getContainer();
    }

    protected User getUser()
    {
        return _parameters.getUser();
    }

    protected boolean isViewVialCount()
    {
        return _viewVialCount;
    }

    protected boolean isViewParticipantCount()
    {
        return _viewParticipantCount;
    }

    protected boolean isViewVolume()
    {
        return _viewVolume;
    }

    protected boolean isViewPtidList()
    {
        return _viewPtidList;
    }

    protected String getBaseCustomViewName()
    {
        return _parameters.getBaseCustomViewName();
    }

    protected SpecimenManager.SpecimenTypeLevel getTypeLevelEnum()
    {
        return _parameters.getTypeLevelEnum();
    }

    protected String getStatusFilterName()
    {
        return _parameters.getStatusFilterName();
    }

    protected CohortFilter getCohortFilter()
    {
        if (StudyManager.getInstance().showCohorts(_container, getUser()))
            return _parameters.getCohortFilter();
        return null;
    }

    protected CustomView getBaseCustomView()
    {
        if (_parameters.getBaseCustomViewName() == null)
            return null;
        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(_container), _parameters.getUser(), true);
        QueryDefinition def = QueryService.get().createQueryDefForTable(schema, "SpecimenDetail");
        String customViewName = _parameters.getBaseCustomViewName();
        if (SpecimenVisitReportParameters.DEFAULT_VIEW_ID.equals(customViewName))
            customViewName = null;
        CustomView view = def.getCustomView(_parameters.getUser(), _parameters.getViewContext().getRequest(), customViewName);
        if (view == null)
            throw new NotFoundException("Custom view " + _parameters.getBaseCustomViewName() + " was not found.  It may have been deleted by another user.");

        return view;
    }
}
