package org.labkey.core.query;

import org.labkey.api.data.CoreSchema;
import org.labkey.api.query.SimpleUserSchema;

public class ChatMessageTable extends SimpleUserSchema.SimpleTable<CoreQuerySchema>
{
    public ChatMessageTable(CoreQuerySchema schema)
    {
        super(schema, CoreSchema.getInstance().getTableInfoChatMessages(), null);
    }

    public ChatMessageTable init()
    {
        return (ChatMessageTable) super.init();
    }
}
