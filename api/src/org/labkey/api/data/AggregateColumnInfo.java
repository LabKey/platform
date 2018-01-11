/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Map;
import java.util.Set;

/**
 * Represents a ColumnInfo for an aggregate in a crosstab table info
 *
 * User: Dave
 * Date: Jan 29, 2008
 * Time: 4:39:51 PM
 */
public class AggregateColumnInfo extends ColumnInfo
{
    public static final String NAME_PREFIX = "CTAGG_";
    public static final String PIVOTED_NAME_PREFIX = "PCTAGG_";

    private @Nullable final CrosstabMember _member;
    private @NotNull final CrosstabMeasure _measure;

    public AggregateColumnInfo(TableInfo table, @Nullable CrosstabMember member, @NotNull CrosstabMeasure measure)
    {
        super(measure.getSourceColumn(), table);

        _member = member;
        _measure = measure;

        setName(getColumnName(_member, _measure));
        setLabel(_measure.getCaption());
        if (member != null)
        {
            setCrosstabColumnDimension(member.getDimensionFieldKey());
            setCrosstabColumnMember(member);
        }

        if (null != measure.getUrl() && null != member)
            setURL(StringExpressionFactory.createURL(measure.getUrl(member).getActionURL()));

        //if the agg function is something other than min or max, clear the FK
        if(!_measure.getAggregateFunction().retainsForeignKey())
        {
            setFk(null);
        }
        else
        {
            // Skip the prefixing if we can
            if (getFk() instanceof LookupForeignKey)
            {
                ((LookupForeignKey)getFk()).setPrefixColumnCaption(false);
            }
        }
    }

    @Override
    public ForeignKey getFk()
    {
        final ForeignKey fk = super.getFk();
        if (fk != null)
        {
            // Wrap the foreign key in a delegating class that propagates any CrosstabMember information
            return new ForeignKey()
            {
                @Override
                public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
                {
                    ColumnInfo result = fk.createLookupColumn(parent, displayField);
                    if (result != null)
                    {
                        result.setCrosstabColumnMember(getCrosstabColumnMember());
                    }
                    return result;
                }

                @Override
                public TableInfo getLookupTableInfo()
                {
                    return fk.getLookupTableInfo();
                }

                @Override
                public StringExpression getURL(ColumnInfo parent)
                {
                    return fk.getURL(parent);
                }

                @Override
                public NamedObjectList getSelectList(RenderContext ctx)
                {
                    return fk.getSelectList(ctx);
                }

                @Override
                public Container getLookupContainer()
                {
                    return fk.getLookupContainer();
                }

                @Override
                public String getLookupTableName()
                {
                    return fk.getLookupTableName();
                }

                @Override
                public String getLookupSchemaName()
                {
                    return fk.getLookupSchemaName();
                }

                @Override
                public String getLookupColumnName()
                {
                    return fk.getLookupColumnName();
                }

                @Override
                public String getLookupDisplayName()
                {
                    return fk.getLookupDisplayName();
                }

                @Override
                public ForeignKey remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> mapping)
                {
                    return fk.remapFieldKeys(parent, mapping);
                }

                @Override
                public Set<FieldKey> getSuggestedColumns()
                {
                    return fk.getSuggestedColumns();
                }
            };
        }
        return fk;
    }

    @NotNull
    @Override
    public JdbcType getJdbcType()
    {
        return _measure.getAggregateSqlType();
    }

    /**
     * Get the CrosstabMember for this AggregateColumnInfo.  May be null for customize view scenatio.
     * @return CrosstabMember or null for customize view scenario.
     */
    public @Nullable CrosstabMember getMember()
    {
        return _member;
    }

    public SQLFragment getValueSql(String tableAliasName)
    {
        return new SQLFragment(tableAliasName + "." + getName());
    }

    public static String getColumnName(@Nullable CrosstabMember member, CrosstabMeasure measure)
    {
        if(null == member)
            return NAME_PREFIX + measure.getAggregateFunction().name() + "_" + measure.getSourceColumn().getAlias();
        else
            return PIVOTED_NAME_PREFIX + member.getValueSQLAlias(measure.getSourceColumn().getSqlDialect()) + "_"
                    + measure.getAggregateFunction().name() + "_" + measure.getSourceColumn().getAlias();
    }
}
