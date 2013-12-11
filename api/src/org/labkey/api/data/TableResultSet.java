package org.labkey.api.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;

/**
* User: adam
* Date: 12/7/13
* Time: 7:52 PM
*/
public interface TableResultSet extends ResultSet, Iterable<Map<String, Object>>
{
    public boolean isComplete();

    public Map<String, Object> getRowMap() throws SQLException;

    public Iterator<Map<String, Object>> iterator();

    String getTruncationMessage(int maxRows);

    /** @return the number of rows in the result set. -1 if unknown */
    int getSize();
}
