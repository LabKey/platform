package org.labkey.api.reader;

import java.io.IOException;
import java.util.List;

/**
 * User: adam
 * Date: Aug 8, 2010
 * Time: 9:55:29 AM
 */

// A loader of columnar data.   
public interface Loader<T>
{
    //public CloseableIterator<Map<String, Object>> mapIterator();
    public ColumnDescriptor[] getColumns() throws IOException;
    public List<T> load() throws IOException;
}
