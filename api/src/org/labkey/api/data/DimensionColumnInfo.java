/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpressionFactory;

/**
 * User: Dave
 * Date: Jan 29, 2008
 * Time: 5:35:17 PM
 */
public class DimensionColumnInfo extends ColumnInfo
{
    private CrosstabDimension _dimension = null;

    public DimensionColumnInfo(CrosstabTableInfo table, CrosstabDimension dimension)
    {
        super(dimension.getSourceColumn(), table);
        _dimension = dimension;
        setName(_dimension.getSourceColumn().getAlias());
        setLabel(_dimension.getSourceColumn().getLabel());
        setURL(StringExpressionFactory.createURL(dimension.getUrl()));
        setDimension(true);
        setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
    }

    @Override
    public CrosstabTableInfo getParentTable()
    {
        return (CrosstabTableInfo)super.getParentTable();
    }

    public FieldKey getSourceFieldKey()
    {
        return _dimension.getSourceFieldKey();
    }


    public SQLFragment getValueSql(String tableAliasName)
    {
        return new SQLFragment(tableAliasName + "." + _dimension.getSourceColumn().getAlias());
    }
}
