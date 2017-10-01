package org.labkey.api.compliance;

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
    };

    ColumnInfoFilter getColumnInfoFilter();
    ColumnInfoTransformer getColumnInfoTransformer();
}
