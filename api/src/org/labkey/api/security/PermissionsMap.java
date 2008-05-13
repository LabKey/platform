/*
 * Copyright (c) 2006-2007 LabKey Corporation
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

package org.labkey.api.security;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Apr 24, 2006
 * Time: 11:46:37 AM
 *
 * Compute a set of permissions given a User u and an Object o.  Often this will
 * be done by finding an ACL a and calling a.getPermssions(u)
 */
public interface PermissionsMap<T>
{
    int getPermissions(User u, T t);
}
