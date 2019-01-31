package org.labkey.api.data;

import java.util.List;

/**
 * Utility class to serialize FK filters to JSON using Jackson
 */
public class ForeignKeyFilterGroup
{
    private ForeignKey.FilterOperation _operation;
    private List<ForeignKeyFilterGroup.Filter> _filters;

    public ForeignKeyFilterGroup(ForeignKey.FilterOperation operation, List<ForeignKeyFilterGroup.Filter> filters)
    {
        _operation = operation;
        _filters = filters;
    }

    public ForeignKey.FilterOperation getOperation()
    {
        return _operation;
    }

    public void setOperation(ForeignKey.FilterOperation operation)
    {
        _operation = operation;
    }

    public List<ForeignKeyFilterGroup.Filter> getFilters()
    {
        return _filters;
    }

    public void setFilters(List<ForeignKeyFilterGroup.Filter> filters)
    {
        _filters = filters;
    }

    public static class Filter
    {
        private String _column;
        private String _value;
        private String _operator;

        public Filter(String column, String value, String operator)
        {
            _column = column;
            _value = value;
            _operator = operator;
        }

        public String getColumn()
        {
            return _column;
        }

        public void setColumn(String column)
        {
            _column = column;
        }

        public String getValue()
        {
            return _value;
        }

        public void setValue(String value)
        {
            _value = value;
        }

        public String getOperator()
        {
            return _operator;
        }

        public void setOperator(String operator)
        {
            _operator = operator;
        }
    }
}
