/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

import org.labkey.api.security.permissions.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Specifies a set of allowed permissions for an action, for situations where ALL of the permissions are required.
 * It does not imply that the user needs to be logged in or otherwise authenticated.
 * Created by adam on 6/15/2015.
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface RequiresAllOf
{
    Class<? extends Permission>[] value();
}