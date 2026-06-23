/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen.actions;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.specimen.SpecimenQuerySchema;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.actions.SpecimenController.PtidVisit;
import org.labkey.specimen.query.SpecimenQueryView;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SpecimenHeaderBean
{
    private final ActionURL _otherViewURL;
    private final ViewContext _viewContext;
    private final boolean _showingVials;
    private final Set<PtidVisit> _ptidVisits;

    private Integer _selectedRequest;

    public SpecimenHeaderBean(ViewContext context, SpecimenQueryView view)
    {
        this(context, view, Collections.emptySet());
    }

    public SpecimenHeaderBean(ViewContext context, SpecimenQueryView view, Set<PtidVisit> ptidVisits) throws RuntimeException
    {
        Map<String, String[]> params = context.getRequest().getParameterMap();

        String currentTable = view.isShowingVials() ? "SpecimenDetail" : "SpecimenSummary";
        String otherTable = view.isShowingVials() ? "SpecimenSummary" : "SpecimenDetail";
        ActionURL otherView = context.cloneActionURL();
        otherView.deleteParameters();

        Study study = StudyService.get().getStudy(context.getContainer());
        if (null == study)
            throw new NotFoundException("No study exists in this folder.");
        SpecimenQuerySchema schema = SpecimenQuerySchema.get(study, context.getUser());

        TableInfo otherTableInfo = schema.getTable(otherTable);

        for (Map.Entry<String, String[]> param : params.entrySet())
        {
            int dotIndex = param.getKey().indexOf('.');

            if (dotIndex >= 0)
            {
                String table = param.getKey().substring(0, dotIndex);
                String columnClause = param.getKey().substring(dotIndex + 1);
                String[] columnClauseParts = columnClause.split("~");
                String column = columnClauseParts[0];

                if (table.equals(currentTable))
                {
                    // use the query service to check to see if the current filter column is present
                    // in the other view. If so, we'll add a filter parameter with the same value on the
                    // other view. Otherwise, we'll keep the parameter, but we won't map it to the other view:
                    boolean translatable = column.equals("sort");

                    if (!translatable)
                    {
                        Map<FieldKey, ColumnInfo> presentCols = QueryService.get().getColumns(otherTableInfo,
                                Collections.singleton(FieldKey.fromString(column)));
                        translatable = !presentCols.isEmpty();
                    }

                    if (translatable)
                    {
                        String key = otherTable + "." + columnClause;
                        otherView.addParameter(key, param.getValue()[0]);
                        continue;
                    }
                }

                if (table.equals(currentTable) || table.equals(otherTable))
                    otherView.addParameter(param.getKey(), param.getValue()[0]);
            }
        }

        otherView.replaceParameter("showVials", Boolean.toString(!view.isShowingVials()));
        if (null != params.get(SpecimenQueryView.PARAMS.excludeRequestedBySite.name()))
            otherView.replaceParameter(SpecimenQueryView.PARAMS.excludeRequestedBySite.name(),
                params.get(SpecimenQueryView.PARAMS.excludeRequestedBySite.name())[0]);
        _otherViewURL = otherView;
        _viewContext = context;
        _showingVials = view.isShowingVials();
        _ptidVisits = ptidVisits;
    }

    public Integer getSelectedRequest()
    {
        return _selectedRequest;
    }

    public void setSelectedRequest(Integer selectedRequest)
    {
        _selectedRequest = selectedRequest;
    }

    public ActionURL getOtherViewURL()
    {
        return _otherViewURL;
    }

    public ViewContext getViewContext()
    {
        return _viewContext;
    }

    public boolean isShowingVials()
    {
        return _showingVials;
    }

    public Set<PtidVisit> getPtidVisits()
    {
        return _ptidVisits;
    }

    public boolean isSingleVisitFilter()
    {
        if (getPtidVisits().isEmpty())
            return false;
        Iterator<PtidVisit> ptidVisit = getPtidVisits().iterator();
        String firstVisit = ptidVisit.next().visit();
        while (ptidVisit.hasNext())
        {
            if (!Objects.equals(firstVisit, ptidVisit.next().visit()))
                return false;
        }
        return true;
    }
}
