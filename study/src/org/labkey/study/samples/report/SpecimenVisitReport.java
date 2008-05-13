package org.labkey.study.samples.report;

import org.labkey.study.model.Visit;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

/**
 * Copyright (c) 2008 LabKey Corporation
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
    protected Container _container;
    protected SimpleFilter _filter;
    protected SpecimenVisitReportParameters _parameters;

    public SpecimenVisitReport(String title, Visit[] visits, SimpleFilter filter, SpecimenVisitReportParameters parameters)
    {
        _title = title;
        _visits = visits;
        _filter = filter;
        _parameters = parameters;
        _container = parameters.getContainer();
    }

    public Collection<String> getStrings()
    {
        return null;
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
        return _visits;
    }

   protected abstract String getCellHtml(Visit visit, CELLDATA summary);

    protected static String getTitleSuffix(SpecimenVisitReportParameters parameters)
    {
        StringBuilder suffixBuilder = new StringBuilder();
        if (parameters.isViewVialCount())
            suffixBuilder.append(" (Vial Count");
        if (parameters.isViewParticipantCount())
        {
            suffixBuilder.append(suffixBuilder.length() > 0 ? "/" : " (");
            suffixBuilder.append("Participant Count");
        }
        if (parameters.isViewVolume())
        {
            suffixBuilder.append(suffixBuilder.length() > 0 ? "/" : " (");
            suffixBuilder.append("Total Volume");
        }
        if (parameters.isViewPtidList())
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
        // tin the query view.  As a result, we need to use the view's column name for filtering
        // at the database layer, and then map this column name for use in a query view filter parameter:
        fullFilter = replaceFilterParameterName(fullFilter, "PrimaryTypeId", FieldKey.fromParts("PrimaryType", "ScharpId").toString());
        fullFilter = replaceFilterParameterName(fullFilter, "DerivativeTypeId", FieldKey.fromParts("DerivativeType", "ScharpId").toString());
        fullFilter = replaceFilterParameterName(fullFilter, "AdditiveTypeId", FieldKey.fromParts("AdditiveType", "ScharpId").toString());
        fullFilter = replaceFilterParameterName(fullFilter, "ptid", "ParticipantId");
        return fullFilter;
    }

    protected String getFilterQueryString(Visit visit, CELLDATA summary)
    {
        return getViewFilter().toQueryString("SpecimenDetail");
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
}
