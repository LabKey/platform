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
package org.labkey.specimen.report;

import org.labkey.api.data.SQLFragment;

import java.util.Map;

public class SpecimenDetailQueryHelper
{
    private final SQLFragment _viewSql;
    private final String _typeGroupingColumns;
    private final Map<String, SpecimenTypeBeanProperty> _aliasToTypePropertyMap;

    public SpecimenDetailQueryHelper(SQLFragment viewSql, String typeGroupingColumns, Map<String, SpecimenTypeBeanProperty> aliasToTypePropertyMap)
    {
        _viewSql = viewSql;
        _typeGroupingColumns = typeGroupingColumns;
        _aliasToTypePropertyMap = aliasToTypePropertyMap;
    }

    public SQLFragment getViewSql()
    {
        return _viewSql;
    }

    public String getTypeGroupingColumns()
    {
        return _typeGroupingColumns;
    }

    public Map<String, SpecimenTypeBeanProperty> getAliasToTypePropertyMap()
    {
        return _aliasToTypePropertyMap;
    }
}
