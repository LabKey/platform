package org.labkey.api.exp.list;

import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;

public interface ListItem
{
    public Object getKey();
    public void setKey(Object key);

    public String getEntityId();
    public void setEntityId(String entityId);

    public Object getProperty(DomainProperty property);
    public void setProperty(DomainProperty property, Object value);

    public void save(User user) throws Exception;
    public void delete(User user, Container c) throws Exception;
}
