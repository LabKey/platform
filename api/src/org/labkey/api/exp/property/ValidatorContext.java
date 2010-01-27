package org.labkey.api.exp.property;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jan 26, 2010
 */
public class ValidatorContext
{
    private Map<Pair<Class<? extends ValidatorKind>, Object>, Object> _map = new HashMap<Pair<Class<? extends ValidatorKind>, Object>, Object>();

    private Container _container;
    private User _user;

    public ValidatorContext(Container container, User user)
    {
        _container = container;
        _user = user;
    }

    public Object get(Class<? extends ValidatorKind> validatorClass, Object key)
    {
        return _map.get(new Pair<Class<? extends ValidatorKind>, Object>(validatorClass, key));
    }

    public void put(Class<? extends ValidatorKind> validatorClass, Object key, Object value)
    {
        _map.put(new Pair<Class<? extends ValidatorKind>, Object>(validatorClass, key), value);
    }

    public Container getContainer()
    {
        return _container;
    }

    public User getUser()
    {
        return _user;
    }
}
