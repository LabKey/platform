package org.labkey.api.iterator;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: Aug 20, 2010
 * Time: 11:55:59 AM
 */
public class IteratorUtil
{
    // Returns a list of T records, one for each non-header row in the input.  Static convenience method intended
    // for use with BeanIterators.
    //
    // Caution: Using this instead of iterating directly has lead to many scalability problems in the past.
    public static <T> List<T> toList(CloseableIterator<T> it) throws IOException
    {
        List<T> list = new LinkedList<T>();

        try
        {
            while (it.hasNext())
                list.add(it.next());
        }
        finally
        {
            it.close();
        }

        return list;
    }
}
