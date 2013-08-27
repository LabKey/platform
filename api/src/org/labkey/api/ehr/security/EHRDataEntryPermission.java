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
package org.labkey.api.ehr.security;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * User: bimber
 * Date: 1/17/13
 * Time: 7:49 PM
 */
public class EHRDataEntryPermission extends AbstractEHRPermission
{
    public EHRDataEntryPermission()
    {
        super("EHRDataEntryPermission", "This is the base permission required in order to submit data to any colony records table");
    }
}
