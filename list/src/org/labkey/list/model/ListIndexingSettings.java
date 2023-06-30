package org.labkey.list.model;

public interface ListIndexingSettings
{
    boolean isEntireListIndex();
    String getEntireListTitleTemplate();
    int getEntireListIndexSetting();
    int getEntireListBodySetting();
    String getEntireListBodyTemplate();

    boolean isEachItemIndex();
    String getEachItemTitleTemplate();
    int getEachItemBodySetting();
    String getEachItemBodyTemplate();

    boolean isFileAttachmentIndex();
}
