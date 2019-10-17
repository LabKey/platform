package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

/*
 * This is an attemp at making a DataRegion that can be reused/reexecuted where ONLY the query parameters change
 */
public class ReexecutableDataregion extends DataRegion
{
    Map<String,Object> parameters = new CaseInsensitiveHashMap<>();

    // close current result set and update query parameters
    // usually followed immediately by call to getResultSet()
    void reset(ReexecutableRenderContext ctx, Map<String,Object> currentParameters)
    {
        ctx.setRow(Collections.emptyMap());
        ResultSet rs = ctx.getResults();
        if (null != rs)
        {
            try {if (!rs.isClosed()) rs.close();}catch(SQLException x){/*pass*/}
            ctx.setResults(null);
        }
        parameters.clear();
        parameters.putAll(super.getQueryParameters());
        parameters.putAll(currentParameters);
    }

    @Override
    public @NotNull Map<String, Object> getQueryParameters()
    {
        return parameters;
    }

    @Override
    protected Results getResultSet(RenderContext ctx, boolean async) throws SQLException, IOException
    {
        return super.getResultSet(ctx, async);
    }
}
