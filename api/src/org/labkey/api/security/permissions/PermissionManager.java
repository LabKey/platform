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
package org.labkey.api.security.permissions;

import org.apache.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
* User: Dave
* Date: Apr 23, 2009
* Time: 1:54:09 PM
*/

public class PermissionManager
{
    private static Logger _log = Logger.getLogger(PermissionManager.class);
    private static final Map<Class<? extends Permission>, Permission> _instances =
            new ConcurrentHashMap<>();

    /**
     * Returns a singleton instance of a given permission given its class
     * @param cls The permission class
     * @return A permission instance
     */
    public static Permission getPermission(Class<? extends Permission> cls)
    {
        Permission ret = _instances.get(cls);
        if(null == ret)
        {
            try
            {
                ret = cls.newInstance();
                _instances.put(cls, ret);
            }
            catch(InstantiationException e)
            {
                _log.error("Unable to instantiate the permission class " + cls.getName());
                throw new RuntimeException(e);
            }
            catch(IllegalAccessException e)
            {
                _log.error("Unable to instantiate the permission class " + cls.getName());
                throw new RuntimeException(e);
            }
        }

        return ret;
    }
}