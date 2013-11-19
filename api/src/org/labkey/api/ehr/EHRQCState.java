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
package org.labkey.api.ehr;

import org.json.JSONObject;
import org.labkey.api.data.Container;

/**
 * User: bimber
 * Date: 10/29/13
 * Time: 2:48 PM
 */
public interface EHRQCState
{
    public int getRowId();

    public String getLabel();

    public Container getContainer();

    public String getDescription();

    public Boolean isPublicData();

    public Boolean isDraftData();

    public Boolean isDeleted();

    public Boolean isRequest();

    public Boolean isAllowFutureDates();

    public JSONObject toJson();

}
