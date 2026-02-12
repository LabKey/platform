package org.labkey.core.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.util.StringExpression;

import java.util.Set;

public class ChatConversationTable extends SimpleUserSchema.SimpleTable<CoreQuerySchema>
{
    public ChatConversationTable(CoreQuerySchema schema, ContainerFilter cf)
    {
        super(schema, CoreSchema.getInstance().getTableInfoChatConversation(), cf);
        setTitleColumn("ConversationId");
    }

    public ChatConversationTable init()
    {
        return (ChatConversationTable) super.init();
    }

    @Override
    public StringExpression getDetailsURL(@Nullable Set<FieldKey> columns, Container container)
    {
        return DetailsURL.fromString("test-conversationDetail.view?id=${ConversationId}");
    }
}
