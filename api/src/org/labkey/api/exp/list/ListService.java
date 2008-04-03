package org.labkey.api.exp.list;

import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.view.ActionURL;

import java.util.Map;
import java.sql.SQLException;

public class ListService
{
    static private Interface instance;

    static public Interface get()
    {
        return instance;
    }

    static public void setInstance(Interface i)
    {
        instance = i;
    }
    public interface Interface
    {
        Map<String, ListDefinition> getLists(Container container);
        boolean hasLists(Container container);
        ListDefinition createList(Container container, String name);
        ListDefinition getList(int id);
        ListDefinition getList(Domain domain);
        ActionURL getManageListsURL(Container container);

        public void beginTransaction() throws SQLException;
        public void commitTransaction() throws SQLException;
        public void rollbackTransaction();
        public boolean isTransactionActive();
    }
}
