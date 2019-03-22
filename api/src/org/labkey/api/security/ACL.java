/*
 * Copyright (c) 2003-2017 Fred Hutchinson Cancer Research Center
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
 * Use {@link SecurityPolicy} instead. These bitmask values represent the most basic types of permissions.
 */
@Deprecated
public class ACL implements Cloneable
{
    public static final int PERM_NONE = 0x00000000;

    /** {@link org.labkey.api.security.permissions.ReadPermission} */
    public static final int PERM_READ = 0x00000001;
    /** {@link org.labkey.api.security.permissions.InsertPermission} */
    public static final int PERM_INSERT = 0x00000002;
    /** {@link org.labkey.api.security.permissions.UpdatePermission} */
    public static final int PERM_UPDATE = 0x00000004;
    /** {@link org.labkey.api.security.permissions.DeletePermission} */
    public static final int PERM_DELETE = 0x00000008;

    /** {@link org.labkey.api.security.permissions.ReadSomePermission} */
    public static final int PERM_READOWN = 0x00000010;

    public static final int PERM_UPDATEOWN = 0x00000040;
    public static final int PERM_DELETEOWN = 0x00000080;

    /** {@link org.labkey.api.security.permissions.AdminPermission} */
    /** Equivalent to site admin access */
    public static final int PERM_ADMIN = 0x00008000;
    public static final int PERM_ALLOWALL = 0x0000ffff;
}