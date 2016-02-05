/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.query;

import org.apache.log4j.Logger;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.SchemaTreeWalker;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Pair;
import org.labkey.query.persist.QueryManager;

import java.util.ArrayList;
import java.util.List;

/**
 * User: kevink
 * Date: 10/9/12
 *
 * Walks the entire schema tree and validates queries.
 * The return value is true if all queries are valid.
 */
public class ValidateQueriesVisitor extends SchemaTreeWalker<Boolean, Logger>
{
    private List<Pair<String, ? extends Throwable>> _warnings = new ArrayList<>();
    private int _totalCount = 0;
    private int _validCount = 0;
    private QueryManager _mgr;

    public ValidateQueriesVisitor()
    {
        super(true);
        _mgr = QueryManager.get();
    }

    public List<Pair<String, ? extends Throwable>> getWarnings()
    {
        return _warnings;
    }

    public int getTotalCount()
    {
        return _totalCount;
    }

    public int getValidCount()
    {
        return _validCount;
    }

    public int getInvalidCount()
    {
        return _totalCount - _validCount;
    }

    @Override
    public Boolean reduce(Boolean r1, Boolean r2)
    {
        return (r1 != null ? r1 : true) && (r2 != null ? r2 : true);
    }

    @Override
    public Boolean visitTable(TableInfo table, Path path, Logger logger)
    {
        _totalCount++;
        List<QueryParseException> errors = new ArrayList<>();
        List<QueryParseException> warnings = new ArrayList<>();
        if (!_mgr.validateQuery(table, true, errors, warnings))
        {
            for (QueryParseException error : errors)
            {
                String msg = String.format("Query %s.%s failed validation!", table.getPublicSchemaName(), table.getName());
                if (logger != null)
                    logger.warn("VALIDATION ERROR: " + msg, error.getCause());
                _warnings.add(Pair.of(msg, error.getCause()));
            }
            return false;
        }
        else
        {
            _validCount++;
            return true;
        }
    }

    @Override
    public Boolean visitTableError(UserSchema schema, String name, Exception e, Path path, Logger logger)
    {
        _totalCount++;
        String msg = String.format("Query %s.%s failed validation!", schema.getName(), name);
        if (logger != null)
            logger.warn("VALIDATION ERROR: " + msg, e);
        _warnings.add(Pair.of(msg, e));
        return false;
    }
}
