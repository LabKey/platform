/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
