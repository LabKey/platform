package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.Results;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

/*
 * This is an attempt at making a DataRegion that can be reused/reexecuted where ONLY the query parameters change
 */
public class ReexecutableDataregion extends DataRegion
{
    private final Map<String, Object> _parameters = new CaseInsensitiveHashMap<>();

    // close current result set and update query parameters
    // usually followed immediately by call to getResults()
    public void reset(ReexecutableRenderContext ctx, Map<String, Object> currentParameters)
    {
        ctx.setRow(Collections.emptyMap());
        Results results = ctx.getResults();
        if (null != results)
        {
            try {if (!results.isClosed()) results.close();}catch(SQLException x){/*pass*/}
            ctx.setResults(null);
        }
        _parameters.clear();
        _parameters.putAll(super.getQueryParameters());
        _parameters.putAll(currentParameters);
    }

    @Override
    public @NotNull Map<String, Object> getQueryParameters()
    {
        return _parameters;
    }
}
