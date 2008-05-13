/*
 * Copyright (c) 2008 LabKey Corporation
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

/**
 * Represents a ColumnInfo for an aggregate in a crosstab table info
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Jan 29, 2008
 * Time: 4:39:51 PM
 */
public class AggregateColumnInfo extends ColumnInfo
{
    public static final String NAME_PREFIX = "CTAGG_";
    public static final String PIVOTED_NAME_PREFIX = "PCTAGG_";

    private TableInfo _table = null;
    private CrosstabMember _member = null;
    private CrosstabMeasure _measure = null;

    public AggregateColumnInfo(TableInfo table, CrosstabMember member, CrosstabMeasure measure)
    {
        super(measure.getSourceColumn(), table);

        _table = table;
        _member = member;
        _measure = measure;

        setName(getColumnName(_member, _measure));
        setAlias(getName());
        setCaption(_measure.getCaption());

        if(null != measure.getUrl() && null != member)
            setURL(measure.getUrl(member));

        //if the agg function is something other than min or max, clear the FK
        if(_measure.getAggregateFunction() != CrosstabMeasure.AggregateFunction.MAX &&
                _measure.getAggregateFunction() != CrosstabMeasure.AggregateFunction.MIN)
        {
            setFk(null);
        }
    }

    public int getSqlTypeInt()
    {
        return _measure.getAggregateSqlType();
    }

    public CrosstabMember getMember()
    {
        return _member;
    }

    public CrosstabMeasure getMeasure()
    {
        return _measure;
    }

    public SQLFragment getValueSql(String tableAliasName)
    {
        return new SQLFragment(tableAliasName + "." + getName());
    }

    public static String getColumnName(CrosstabMember member, CrosstabMeasure measure)
    {
        if(null == member)
            return NAME_PREFIX + measure.getAggregateFunction().name() + "_" + measure.getSourceColumn().getAlias();
        else
            return PIVOTED_NAME_PREFIX + member.getValue().toString() + "_"
                    + measure.getAggregateFunction().name() + "_" + measure.getSourceColumn().getAlias();
    }
}
