package org.labkey.api.query;

public class QueryParseExceptionUnresolvedField extends QueryParseException
{
    final FieldKey fieldKey;

    public QueryParseExceptionUnresolvedField(FieldKey fk, Throwable cause, int line, int column)
    {
        super("Unknown field [" + fk.toDisplayString() + "]", cause, line, column);
        this.fieldKey = fk;
    }

    public FieldKey getFieldKey()
    {
        return fieldKey;
    }
}
