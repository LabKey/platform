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
package org.labkey.api.specimen.importer;

import org.labkey.api.data.JdbcType;
import org.labkey.api.specimen.importer.Rollup;
import org.labkey.api.util.Pair;

public class RollupInstance<K extends Rollup> extends Pair<String, K>
{
    private final JdbcType _fromType;
    private final JdbcType _toType;

    public RollupInstance(String first, K second, JdbcType fromType, JdbcType toType)
    {
        super(first.toLowerCase(), second);
        _fromType = fromType;
        _toType = toType;
    }

    public JdbcType getFromType()
    {
        return _fromType;
    }

    public JdbcType getToType()
    {
        return _toType;
    }

    public boolean isTypeConstraintMet()
    {
        return second.isTypeConstraintMet(_fromType, _toType);
    }
}
