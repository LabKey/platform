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
