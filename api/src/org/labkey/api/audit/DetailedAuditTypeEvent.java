/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.audit;

import org.labkey.api.data.Container;

public class DetailedAuditTypeEvent extends AuditTypeEvent
{
    private String _oldRecordMap;
    private String _newRecordMap;

    /** Important for reflection-based instantiation */
    public DetailedAuditTypeEvent() {}

    public DetailedAuditTypeEvent(String eventType, Container container, String comment)
    {
        super(eventType, container, comment);
    }

    public String getOldRecordMap()
    {
        return _oldRecordMap;
    }

    public void setOldRecordMap(String oldRecordMap, Container container)
    {
        setOldRecordMap(oldRecordMap);
    }

    public void setOldRecordMap(String oldRecordMap)
    {
        _oldRecordMap = oldRecordMap;
    }

    public String getNewRecordMap()
    {
        return _newRecordMap;
    }

    public void setNewRecordMap(String newRecordMap, Container container)
    {
        setNewRecordMap(newRecordMap);
    }

    public void setNewRecordMap(String newRecordMap)
    {
        _newRecordMap = newRecordMap;
    }
}
