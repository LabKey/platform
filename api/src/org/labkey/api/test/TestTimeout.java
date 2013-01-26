/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Timeout for server-side unit tests, in seconds
 * User: jeckels
 * Date: 1/14/13
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface TestTimeout
{
    /** One minute is our standard timeout */
    public static final int DEFAULT = 60;

    int value() default DEFAULT;
}