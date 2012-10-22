package org.labkey.api.writer;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
* Created by IntelliJ IDEA.
* User: klum
* Date: 10/21/12
*/
public class DefaultContainerUser implements ContainerUser
{
    private User _user;
    private Container _container;

    public DefaultContainerUser(Container container, User user)
    {
        _user = user;
        _container = container;
    }

    @Override
    public User getUser()
    {
        return _user;
    }

    @Override
    public Container getContainer()
    {
        return _container;
    }
}
