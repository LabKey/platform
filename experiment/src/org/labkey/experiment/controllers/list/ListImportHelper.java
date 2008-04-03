package org.labkey.experiment.controllers.list;

import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.experiment.list.ListItemImpl;
import org.labkey.common.tools.TabLoader;

import java.util.Map;
import java.sql.SQLException;

public class ListImportHelper implements OntologyManager.ImportHelper
{
    User _user;
    ListDefinition _list;
    DomainProperty[] _properties;
    TabLoader.ColumnDescriptor _cdKey;
    public ListImportHelper(User user, ListDefinition list, DomainProperty[] properties, TabLoader.ColumnDescriptor cdKey)
    {
        _user = user;
        _list = list;
        _properties = properties;
        _cdKey = cdKey;
    }
    
    public String beforeImportObject(Map map) throws SQLException
    {
        try
        {
            Object key = (null == _cdKey ? null : map.get(_cdKey.name));  // Could be null in auto-increment case
            ListItem item = (null == key ? null : _list.getListItem(key));
            if (item == null)
            {
                item = _list.createListItem();
                item.setKey(key);
            }
            else
            {
                for (DomainProperty pd : _properties)
                {
                    item.setProperty(pd, null);
                }
            }

            String ret = ((ListItemImpl) item).ensureOntologyObject().getObjectURI();
            item.save(_user);
            return ret;
        }
        catch (SQLException sqlException)
        {
            throw sqlException;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public void afterImportObject(String lsid, ObjectProperty[] props) throws SQLException
    {
    }
}
