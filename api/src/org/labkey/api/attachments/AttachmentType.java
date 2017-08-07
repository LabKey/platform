package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.SQLFragment;

public interface AttachmentType
{
    AttachmentType UNKNOWN = new AttachmentType()
    {
        @NotNull
        @Override
        public String getUniqueName()
        {
            return "UnknownAttachmentType";
        }

        @Override
        public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
        {
        }
    };

    @NotNull String getUniqueName();

    void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn);
}
