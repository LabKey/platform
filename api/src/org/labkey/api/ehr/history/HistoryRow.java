/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.ehr.history;

import org.json.JSONObject;

/**
 * User: bimber
 * Date: 3/3/13
 * Time: 9:00 PM
 */
public interface HistoryRow
{
    public String getSubjectId();

    public JSONObject toJSON();

    public void setShowTime(Boolean showTime);

    public String getSortDateString();
}
