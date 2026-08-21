/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen;

public enum RequestEventType
{
    REQUEST_CREATED("Request Created"),
    REQUEST_STATUS_CHANGED("Request Status Changed"),
    REQUIREMENT_ADDED("Requirement Created"),
    REQUIREMENT_REMOVED("Requirement Removed"),
    REQUIREMENT_UPDATED("Requirement Updated"),
    REQUEST_UPDATED("Request Updated"),
    SPECIMEN_ADDED("Specimen Added"),
    SPECIMEN_REMOVED("Specimen Removed"),
    SPECIMEN_LIST_GENERATED("Specimen List Generated"),
    COMMENT_ADDED("Comment/Attachment(s) Added"),
    NOTIFICATION_SENT("Notification Sent");

    private final String _displayText;

    RequestEventType(String displayText)
    {
        _displayText = displayText;
    }

    public String getDisplayText()
    {
        return _displayText;
    }
}
