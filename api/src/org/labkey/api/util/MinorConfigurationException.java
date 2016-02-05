/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

/**
 * Use to report minor configuration problems to users/admins (but NOT potential code issues, since the exceptions are
 * not reported to mothership). For example, file system permissions problem, missing directory, etc.
 * User: adam
 * Date: 2/28/12
 */
public class MinorConfigurationException extends RuntimeException implements SkipMothershipLogging
{
    public MinorConfigurationException(String message)
    {
        super(message);
    }


    public MinorConfigurationException(String message, Throwable t)
    {
        super(message, t);
    }
}
