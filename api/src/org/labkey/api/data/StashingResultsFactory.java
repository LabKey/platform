package org.labkey.api.data;

import java.sql.SQLException;

public class StashingResultsFactory implements ResultsFactory, AutoCloseable
{
    private final ResultsFactory _factory;

    private Results _results = null;

    public StashingResultsFactory(ResultsFactory factory)
    {
        _factory = factory;
    }

    @Override
    public Results get() throws Exception
    {
        if (null == _results)
            _results = _factory.get();

        return _results;
    }

    @Override
    public void close() throws SQLException
    {
        if (_results != null && !_results.isClosed())
            _results.close();
    }
}
