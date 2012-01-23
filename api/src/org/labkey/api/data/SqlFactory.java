package org.labkey.api.data;

/**
 * User: adam
 * Date: 1/22/12
 * Time: 2:33 PM
 */
public interface SqlFactory
{
    SQLFragment getSql();
    <K> K handleResultSet(BaseSelector.ResultSetHandler<K> handler);
}
