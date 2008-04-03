package org.labkey.query.sql;

import antlr.RecognitionException;

import java.io.StringReader;
import java.util.List;
import java.util.ArrayList;

import org.labkey.query.sql.antlr.SqlBaseLexer;
import org.labkey.query.sql.antlr.SqlBaseParser;

public class SqlParser extends SqlBaseParser
{
    List<RecognitionException> _errors;
    public SqlParser(String str)
    {
        super(new SqlBaseLexer(new StringReader(str)));
        _errors = new ArrayList();
    }

    public void reportError(RecognitionException ex)
    {
        _errors.add(ex);
    }

    public List<RecognitionException> getErrors()
    {
        return _errors;
    }
}
