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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that the user must be authenticated as a site administrator (not just have admin permission in the current
 * container) to invoke the action.
 * User: matthewb
 * Date: Jan 11, 2007
 * Time: 11:35:28 AM
 */
public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface RequiresSiteAdmin
{
}
