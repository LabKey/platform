package org.labkey.study.samples.report;

import org.labkey.study.model.Visit;
import org.labkey.study.model.StudyManager;
import org.labkey.study.SampleManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.*;
import org.labkey.api.security.User;

import java.util.*;

/**
 * Copyright (c) 2008-2009 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: Jan 14, 2008 10:14:50 AM
 */
public abstract class SpecimenVisitReport<CELLDATA extends SpecimenReportCellData>
{
    private Collection<Row> _rows;
    protected String _title;
    private Visit[] _visits;
    private Visit[] _nonEmptyVisits;
    protected Container _container;
    protected SimpleFilter _filter;
    private SpecimenVisitReportParameters _parameters;
    private boolean _viewVialCount = false;
    private boolean _viewParticipantCount = false;
    private boolean _viewVolume = false;
    private boolean _viewPtidList = false;
    private Map<Double, Double> _nonEmptyColumns = new HashMap<Double, Double>();

    public SpecimenVisitReport(String titlePrefix, Visit[] visits, SimpleFilter filter, SpecimenVisitReportParameters parameters)
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

    public Visit[] getVisits()
    {
        // ensure rows and non-empty columns have been generated
        getRows();
        if (!_parameters.isHideEmptyColumns())
            return _visits;
        else
        {
            if (_nonEmptyVisits == null)
            {
                List<Visit> visits = new ArrayList<Visit>();
                for (Visit visit : _visits)
                {
                    if (_nonEmptyColumns.containsKey(visit.getSequenceNumMin()))
                        visits.add(visit);
                }
                _nonEmptyVisits = visits.toArray(new Visit[0]);
            }
            return _nonEmptyVisits;
        }
    }

    protected void setVisitAsNonEmpty(Double sequenceNum)
    {
        if (!_nonEmptyColumns.containsKey(sequenceNum))
            _nonEmptyColumns.put(sequenceNum, sequenceNum);
    }

    protected abstract String getCellHtml(Visit visit, CELLDATA summary);

    protected abstract String[] getCellExcelText(Visit visit, CELLDATA summary);

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
            suffixBuilder.append("Participant Count");
        }
        if (isViewPtidList())
        {
            suffixBuilder.append(suffixBuilder.length() > 0 ? ", " : " (");
            suffixBuilder.append("Ptid List");
        }
        suffixBuilder.append(")");
        return suffixBuilder.toString();
    }

    protected SimpleFilter getViewFilter()
    {
        SimpleFilter fullFilter =  new SimpleFilter();
        fullFilter.addAllClauses(_filter);
        // When querying the study.SpecimenDetail view, we use an 'IN' clause for participant id.
        // We need to translate this to a query-based lookup for view filtering purposes:
        if (_parameters.getCohortId() != null)
            fullFilter.addCondition(FieldKey.fromParts("ParticipantId", "Cohort", "RowId").toString(), _parameters.getCohortId());

        // This is a terrible hack to deal with the fact that the some SpecimenDetail columns have been aliased
        // in the query view.  As a result, we need to use the view's column name for filtering
        // at the database layer, and then map this column name for use in a query view filter parameter:
        fullFilter = replaceFilterParameterName(fullFilter, "PrimaryTypeId", FieldKey.fromParts("PrimaryType", "RowId").toString());
        fullFilter = replaceFilterParameterName(fullFilter, "DerivativeTypeId", FieldKey.fromParts("DerivativeType", "RowId").toString());
        fullFilter = replaceFilterParameterName(fullFilter, "AdditiveTypeId", FieldKey.fromParts("AdditiveType", "RowId").toString());
        fullFilter = replaceFilterParameterName(fullFilter, "ptid", "ParticipantId");
        return fullFilter;
    }

    protected String getFilterQueryString(Visit visit, CELLDATA summary)
    {
        String ret = getViewFilter().toQueryString("SpecimenDetail");
        if (_parameters.getBaseCustomViewName() != null && _parameters.getBaseCustomViewName().length() > 0)
        {
            if (!SpecimenVisitReportParameters.DEFAULT_VIEW_ID.equals(_parameters.getBaseCustomViewName()))
                ret += "&SpecimenDetail.viewName=" + _parameters.getBaseCustomViewName();
        }
        else
            ret += "&SpecimenDetail.ignoreFilter=1";
        return ret;
    }

    public int getLabelDepth()
    {
        Collection<Row> rows = getRows();
        if (rows.isEmpty())
            return 0;
        return rows.iterator().next().getTitleHierarchy().length;
    }

    public class Row
    {
        private String[] _titleHierarchy;
        private Map<Double, CELLDATA> _visitData = new HashMap<Double, CELLDATA>();

        public Row(String[] titleHierarchy)
        {
            _titleHierarchy = titleHierarchy;
        }

        public String getCellHtml(Visit visit)
        {
            CELLDATA summary = _visitData.get(visit.getSequenceNumMin());
            return SpecimenVisitReport.this.getCellHtml(visit, summary);
        }

        public String[] getCellExcelText(Visit visit)
        {
            CELLDATA summary = _visitData.get(visit.getSequenceNumMin());
            return SpecimenVisitReport.this.getCellExcelText(visit, summary);
        }

        public int getMaxExcelRowHeight(Visit[] visits)
        {
            int max = 1;
            for (Visit visit : visits)
            {
                int currentHeight = getCellExcelText(visit).length;
                if (currentHeight > max)
                    max = currentHeight;
            }
            return max;
        }

        public String[] getTitleHierarchy()
        {
            return _titleHierarchy;
        }

        public void add(CELLDATA summary)
        {
            _visitData.put(summary.getSequenceNum(), summary);
        }
    }

    protected SimpleFilter replaceFilterParameterName(SimpleFilter filter, String oldKey, String newKey)
    {
        for (SimpleFilter.FilterClause clause : filter.getClauses())
        {
            if (clause.getColumnNames().size() == 1 && oldKey.equalsIgnoreCase(clause.getColumnNames().get(0)))
            {
                if (clause.getParamVals().length > 1)
                    throw new UnsupportedOperationException("Only single filters are supported on column " + newKey);
                filter.deleteConditions(oldKey);
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

    protected String buildCellHtml(Visit visit, CELLDATA summary, String linkHtml)
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

    protected SampleManager.SpecimenTypeLevel getTypeLevelEnum()
    {
        return _parameters.getTypeLevelEnum();
    }

    protected String getStatusFilterName()
    {
        return _parameters.getStatusFilterName();
    }

    protected CustomView getBaseCustomView()
    {
        if (_parameters.getBaseCustomViewName() == null)
            return null;
        StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(_container), _parameters.getUser(), true);
        QueryDefinition def = QueryService.get().createQueryDefForTable(schema, "SpecimenDetail");
        String customViewName = _parameters.getBaseCustomViewName();
        if (SpecimenVisitReportParameters.DEFAULT_VIEW_ID.equals(customViewName))
            customViewName = null;
        CustomView view = def.getCustomView(_parameters.getUser(), _parameters.getViewContext().getRequest(), customViewName);
        if (view == null)
            throw new IllegalStateException("Custom view " + _parameters.getBaseCustomViewName() + " was not found.  It may have been deleted by another user.");
        return view;
    }
}
