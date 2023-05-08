/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.bigiron.oracle;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator;
import org.labkey.api.data.dialect.LimitRowsSqlGenerator.LimitRowsCustomizer;

/**
 * Handles both 12cR1 and 12cR2 (we haven't seen any reason to distinguish between them)
 */
public class Oracle12cDialect extends Oracle11gR2Dialect
{
    // Oracle 12c introduced the OFFSET - FETCH clause, which allows for a more standard paging mechanism
    private static final LimitRowsCustomizer CUSTOMIZER = new LimitRowsCustomizer()
    {
        @Override
        public void appendLimit(SQLFragment frag, int limit)
        {
            frag.append("FETCH NEXT ").appendValue(limit).append(" ROWS ONLY");
        }

        @Override
        public void appendOffset(SQLFragment frag, long offset)
        {
            frag.append("OFFSET ").appendValue(offset).append(" ROWS");
        }

        @Override
        public boolean supportsOffsetWithoutLimit()
        {
            return true;
        }

        @Override
        public boolean requiresOffsetBeforeLimit()
        {
            return true;
        }
    };

    @Override
    public SQLFragment limitRows(SQLFragment frag, int maxRows)
    {
        return LimitRowsSqlGenerator.limitRows(frag, maxRows, 0, CUSTOMIZER);
    }

    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset)
    {
        return LimitRowsSqlGenerator.limitRows(select, from, filter, order, groupBy, maxRows, offset, CUSTOMIZER);
    }
}
