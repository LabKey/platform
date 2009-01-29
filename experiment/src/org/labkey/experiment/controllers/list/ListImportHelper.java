/*
 * Copyright (c) 2007-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.experiment.controllers.list;

import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.experiment.list.ListItemImpl;

import java.sql.SQLException;
import java.util.Map;

public class ListImportHelper implements OntologyManager.ImportHelper
{
    User _user;
    ListDefinition _list;
    DomainProperty[] _properties;
    ColumnDescriptor _cdKey;
    public ListImportHelper(User user, ListDefinition list, DomainProperty[] properties, ColumnDescriptor cdKey)
    {
        _user = user;
        _list = list;
        _properties = properties;
        _cdKey = cdKey;
    }
    
    public String beforeImportObject(Map<String, Object> map) throws SQLException
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
