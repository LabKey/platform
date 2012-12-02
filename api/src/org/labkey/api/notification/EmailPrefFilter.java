/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.notification;

import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Apr 23, 2010
 * Time: 10:44:03 AM
 */
public abstract class EmailPrefFilter
{
    /**
     * The email pref setting to consider when filtering users.
     */
    public abstract EmailPref getEmailPref();

    /**
     * override to return a configurable default for this filter.
     */
    public EmailPref getDefaultEmailPref()
    {
        return null;
    }

    /**
     * Returns the list of users to consider in this filter.
     */
    public abstract User[] getUsers(Container c);

    /**
     * Returns true if the specified pref matches this filters criteria.
     */
    public abstract boolean accept(String pref);

    public User[] filterUsers(Container c)
    {
        List<User> users = new ArrayList<User>();
        EmailPref pref = getEmailPref();
        EmailPref defaultPref = getDefaultEmailPref();
        String defaultValue = pref.getDefaultValue();

        if (defaultPref != null)
        {
            Map<String, String> defaultProps = PropertyManager.getProperties(c, EmailService.EMAIL_PREF_CATEGORY);
            if (defaultProps.containsKey(defaultPref.getId()))
                defaultValue = defaultProps.get(defaultPref.getId());
            else
                defaultValue = defaultPref.getDefaultValue();
        }

        for (User user : getUsers(c))
        {
            Map<String, String> props = PropertyManager.getProperties(user, c, EmailService.EMAIL_PREF_CATEGORY);
            String value = defaultValue;
            
            if (props.containsKey(pref.getId()))
            {
                value = pref.getValue(props.get(pref.getId()), defaultValue);
            }
            if (accept(value))
                users.add(user);
        }
        return users.toArray(new User[users.size()]);
    }
}
