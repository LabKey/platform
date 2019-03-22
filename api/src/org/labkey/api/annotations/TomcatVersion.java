/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by adam on 7/25/2015.
 *
 * Used to mark code that is specific to a particular version of Apache Tomcat, for example, hacks that allow JSPs compiled
 * with one version of Tomcat to run in a different version. Re-evaluate these when new Tomcat versions are supported or
 * deprecated. Search for text "@TomcatVersion" to find all usages.
 */

@Retention(RetentionPolicy.SOURCE)
public @interface TomcatVersion
{
}
