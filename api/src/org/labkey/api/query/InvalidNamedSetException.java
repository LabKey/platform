/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.util.ExpectedException;
import org.labkey.api.view.NotFoundException;

/**
 * Thrown if a requested named member set isn't found in the cache in QueryService. It either never existed, or has expired
 * from the cache. As the cache miss due to expiration is considered normal operation, mmplements ExpectedException
 * so we don't log to the console in API calls.
 * User: tgaluhn
 * Date: 7/15/2014
 */
public class InvalidNamedSetException extends NotFoundException implements ExpectedException
{
    public InvalidNamedSetException(String string)
    {
        super(string);
    }
}
