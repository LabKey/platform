package org.labkey.api.compliance;

import org.labkey.api.data.SQLFragment;

import java.util.function.UnaryOperator;

public interface TableRules
{
    TableRules NOOP_TABLE_RULES = new TableRules()
    {
        @Override
        public ColumnInfoFilter getColumnInfoFilter()
        {
            return columnInfo -> true;
        }

        @Override
        public ColumnInfoTransformer getColumnInfoTransformer()
        {
            return columnInfo -> columnInfo;
        }

        @Override
        public UnaryOperator<SQLFragment> getSqlTransformer()
        {
            return sqlFragment -> sqlFragment;
        }
    };

    ColumnInfoFilter getColumnInfoFilter();
    ColumnInfoTransformer getColumnInfoTransformer();
    UnaryOperator<SQLFragment> getSqlTransformer();
}
