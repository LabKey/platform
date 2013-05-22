package org.labkey.query.reports.getdata;

import org.labkey.api.action.ApiResponse;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.Collections;
import java.util.List;

/**
 * JSON deserialization target for the top-level request sent to the GetData API.
 *
 * User: jeckels
 * Date: 5/15/13
 */
public class DataRequest
{
    private AbstractReportDataSourceBuilder _source;
    private List<AggregateQueryDataTransformerBuilder> _transforms = Collections.emptyList();
    private ReportDataRenderer _renderer = new JSONReportDataRenderer();

    public void setSource(AbstractReportDataSourceBuilder source)
    {
        _source = source;
    }

    public void setTransforms(List<AggregateQueryDataTransformerBuilder> transforms)
    {
        _transforms = transforms;
    }

    public void setRenderer(ReportDataRenderer renderer)
    {
        _renderer = renderer;
    }

    public ApiResponse render(ViewContext context, BindException errors)
    {
        QueryReportDataSource source = _source.create(context.getUser(), context.getContainer());
        for (AggregateQueryDataTransformerBuilder transform : _transforms)
        {
            source = transform.create(source);
        }
        return _renderer.render(source, context, errors);
    }
}
