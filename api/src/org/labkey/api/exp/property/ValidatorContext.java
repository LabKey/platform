/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
    private Map<Pair<Class<? extends ValidatorKind>, Object>, Object> _map = new HashMap<>();

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
        _map.put(new Pair<>(validatorClass, key), value);
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
