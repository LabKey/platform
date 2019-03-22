/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;

/**
 * Specify comma-separated names for an action. Once this annotation is added to an action, it replaces the
 * standard naming, so you must include the default name if you want it to continue resolving.
 * For example, if you annotate GetDataAction and want http://server/controller/container/getData.view to resolve,
 * you must include "getData" in your value string.
 *
 * First name in the list is the "primary" name -- it's used when the class is passed to ActionURL. 
 *
 * User: adam
 * Date: Dec 20, 2007
 * Time: 1:13:03 PM
 */
public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface ActionNames
{
    String value();
}
