package org.labkey.query.reports.getdata;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.UnexpectedException;

/**
 * Renders a QueryView as JSON-ified HTML and context metadata (including JavaScript and CSS dependencies).
 *
 * User: jeckels
 * Date: 5/29/13
 */
@JsonTypeName("grid")
public class QueryWebPartDataRenderer extends AbstractQueryViewReportDataRenderer
{
    @Override
    protected ApiResponse createApiResponse(QueryView view)
    {
        try
        {
            return view.renderToApiResponse();
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new UnexpectedException(e);
        }
    }
}
