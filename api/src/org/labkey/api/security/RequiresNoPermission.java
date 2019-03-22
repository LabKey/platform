/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Indicates that an action does not require any kind of authentication or permission to invoke. Use with extreme
 * caution. Typically, actions marked with this annotation will handle their own permission checks in their own code path.
 * User: adam
 * Date: Dec 22, 2009
*/
public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface RequiresNoPermission
{
}