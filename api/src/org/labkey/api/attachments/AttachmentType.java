package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;

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

        @NotNull
        @Override
        public String getSelectSqlForIds()
        {
            throw new IllegalStateException();
        }
    };

    static AttachmentType getUnknown()
    {
        return UNKNOWN;
    }

    @NotNull String getUniqueName();
    @NotNull String getSelectSqlForIds();
}
