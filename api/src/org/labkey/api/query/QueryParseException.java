package org.labkey.api.query;

public class QueryParseException extends QueryException
{
    int _line;
    int _column;

    public QueryParseException(String message, Throwable cause, int line, int column)
    {
        super(message, cause);
        _line = line;
        _column = column;
    }

    public QueryParseException(String queryName, QueryParseException other)
    {
        super(queryName + ":" + other.getMessage(), other.getCause());
        _line = other._line;
        _column = other._column;
    }

    public String getMessage()
    {
        String ret = super.getMessage();
        if (_line != 0)
        {
            ret = "Error on line " + _line + ":" + ret;
        }
        return ret;
    }

    public int getLine()
    {
        return _line;
    }

    public int getColumn()
    {
        return _column;
    }
}
