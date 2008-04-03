package org.labkey.api.ms2;

import org.apache.log4j.Logger;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.query.FieldKey;

/**
 * User: jeckels
 * Date: Jan 9, 2007
 */
public class MS2Service
{
    private static Service _serviceImpl = null;

    public interface Service
    {
        public String getRunsTableName();

        SearchClient createSearchClient(String server, String url, Logger instanceLogger, String userAccount, String userPassword);

        TableInfo createPeptidesTableInfo(User user, Container container);
        TableInfo createPeptidesTableInfo(User user, Container container, boolean includeFeatureFk, 
                                          boolean restrictContainer, SimpleFilter filter, Iterable<FieldKey> defaultColumns);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }
}
