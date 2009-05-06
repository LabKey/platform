/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.apache.commons.beanutils.Converter;
import org.apache.log4j.Logger;

/*
* User: Dave
* Date: Apr 23, 2009
* Time: 4:28:20 PM
*/
public class RoleConverter implements Converter
{
    private static final Logger _log = Logger.getLogger(RoleConverter.class);

    public Object convert(Class type, Object value)
    {
        if(null == value || !type.equals(Role.class) || !(value instanceof String))
            return null;
        else
        {
            Role role = RoleManager.getRole((String) value);
            if(null == role)
                _log.warn("Unable to resolve role name '" + value + "'! This role may no longer exist, or has not been registered with RoleManger.register().");
            
            return role;
        }
    }
}