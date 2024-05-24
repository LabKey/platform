package org.labkey.specimen.actions;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.specimen.SpecimenQuerySchema;
import org.labkey.specimen.query.SpecimenQueryView;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;

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
    private final Set<Pair<String, String>> _filteredPtidVisits;

    private Integer _selectedRequest;

    public SpecimenHeaderBean(ViewContext context, SpecimenQueryView view)
    {
        this(context, view, Collections.emptySet());
    }

    public SpecimenHeaderBean(ViewContext context, SpecimenQueryView view, Set<Pair<String, String>> filteredPtidVisits) throws RuntimeException
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
        _filteredPtidVisits = filteredPtidVisits;
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

    public Set<Pair<String, String>> getFilteredPtidVisits()
    {
        return _filteredPtidVisits;
    }

    public boolean isSingleVisitFilter()
    {
        if (getFilteredPtidVisits().isEmpty())
            return false;
        Iterator<Pair<String, String>> visitIt = getFilteredPtidVisits().iterator();
        String firstVisit = visitIt.next().getValue();
        while (visitIt.hasNext())
        {
            if (!Objects.equals(firstVisit, visitIt.next().getValue()))
                return false;
        }
        return true;
    }
}
