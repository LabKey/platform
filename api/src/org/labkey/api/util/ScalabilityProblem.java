/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

package org.labkey.api.util;

import java.lang.annotation.Retention;

/**
 * Used to mark any pattern, code, field, etc. that is inherently unscalable.  In particular, we're using this annotation
 * to tag techniques that won't work in multiple web server deployments (e.g., caching of database objects in static maps
 * instead of using a real cache).  We aren't planning to support multi-server deployments any time soon, but it doesn't
 * hurt to mark these issues as we notice them.
 * User: adam
 * Date: Mar 29, 2011
 */

public @Retention(java.lang.annotation.RetentionPolicy.SOURCE)
@interface ScalabilityProblem
{
}
