package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CoreSchema;

public class LookAndFeelResourceType implements AttachmentType
{
    private static final LookAndFeelResourceType INSTANCE = new LookAndFeelResourceType();

    public static LookAndFeelResourceType get()
    {
        return INSTANCE;
    }

    private LookAndFeelResourceType()
    {
    }

    @Override
    public @NotNull String getUniqueName()
    {
        return getClass().getName();
    }

    @Override
    public @Nullable String getSelectSqlForIds()
    {
        return "SELECT EntityId AS ID FROM " + CoreSchema.getInstance().getTableInfoContainers();  // TODO: need to filter based on document names!
    }
}
