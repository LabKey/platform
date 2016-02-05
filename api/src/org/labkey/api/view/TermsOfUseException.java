/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.api.view;

/**
 * Thrown to indicate that while the user has permission to view this data, they have not yet accepted
 * the terms of use and need to be redirected to a page that will let them review and accept.
 * User: Mark Igra
 * Date: Jun 30, 2006
 */
@Deprecated // TODO: Delete this once we've tested WikiTermsOfUseProvider changes
public class TermsOfUseException extends UnauthorizedException
{
    public TermsOfUseException()
    {
        super("You must accept the terms of use before you can access this data");
    }
}
