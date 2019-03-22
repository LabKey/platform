/*
 * Copyright (c) 2008 LabKey Corporation
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

/*
* User: Dave
* Date: Jun 12, 2008
* Time: 3:38:02 PM
*/

/**
 * Wrapper class for exceptions thrown from methods of the {@link QueryUpdateService} interface.
 * Implementation-specific exceptions thrown from implementations of QueryUpdateService
 * will be wrapped into an exception of this type to keep the interface methods
 * standardized across implementations.
 */
public class QueryUpdateServiceException extends Exception
{
    public QueryUpdateServiceException(String message)
    {
        super(message);
    }

    public QueryUpdateServiceException(String message, Throwable t)
    {
        super(message, t);
    }

    public QueryUpdateServiceException(Throwable t)
    {
        super(t);
    }
}