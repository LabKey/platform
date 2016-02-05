/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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
 * @deprecated Use SecurityPolicy
 */
public class ACL implements Cloneable
{
    public static final int PERM_NONE = 0x00000000;

    public static final int PERM_READ = 0x00000001;
    public static final int PERM_INSERT = 0x00000002;
    public static final int PERM_UPDATE = 0x00000004;
    public static final int PERM_DELETE = 0x00000008;

    public static final int PERM_READOWN = 0x00000010;
    public static final int PERM_UPDATEOWN = 0x00000040;
    public static final int PERM_DELETEOWN = 0x00000080;

    public static final int PERM_ADMIN = 0x00008000;
    public static final int PERM_ALLOWALL = 0x0000ffff;
}