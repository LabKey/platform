package org.labkey.core.mpc;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.HttpView;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DatabaseChatMemoryRepository implements ChatMemoryRepository
{
    private static final Logger LOG = LogManager.getLogger(DatabaseChatMemoryRepository.class);

    @Override
    public @NotNull List<String> findConversationIds()
    {
        var table = CoreSchema.getInstance().getTableInfoChatConversation();
        var sql = new SQLFragment("SELECT DISTINCT cc.ConversationId FROM ").append(table, "cc");

        return new SqlSelector(table.getSchema(), sql).getArrayList(String.class);
    }

    @Override
    public @NotNull List<Message> findByConversationId(@NotNull String conversationId)
    {
        var table = CoreSchema.getInstance().getTableInfoChatMessages();
        var filter = new SimpleFilter(FieldKey.fromParts("ConversationId"), conversationId);
        var selector = new TableSelector(table, filter, new Sort("RowId"));
        var messages = new ArrayList<Message>();

        try (var result = selector.getResults())
        {
            while (result.next())
            {
                String messageType = result.getString("MessageType");
                String messageText = result.getString("MessageText");

                Map<String, Object> metadata = null;
                String messageMetadata = result.getString("MessageMetadata");
                if (!StringUtils.isEmpty(messageMetadata))
                    metadata = new JSONObject(messageMetadata).toMap();

                Message message = null;
                if (MessageType.SYSTEM.getValue().equals(messageType))
                {
                    var builder = new SystemMessage.Builder().text(messageText);

                    if (metadata != null)
                        builder.metadata(metadata);

                    message = builder.build();
                }
                else if (MessageType.USER.getValue().equals(messageType))
                {
                    var builder = new UserMessage.Builder().text(messageText);

                    if (metadata != null)
                        builder.metadata(metadata);

                    message = builder.build();
                }
                else if (MessageType.ASSISTANT.getValue().equals(messageType))
                {
                    var builder = AssistantMessage.builder().content(messageText);

                    if (metadata != null)
                        builder.properties(metadata);

                    message = builder.build();
                }
                else if (MessageType.TOOL.getValue().equals(messageType))
                {
                    LOG.info("Skipping tool message: \"{}\"", messageText);
                    // NYI
                }

                if (message != null)
                    messages.add(message);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }

        return Collections.unmodifiableList(messages);
    }

    /**
     * Saves messages for the given conversation ID, preserving existing messages that
     * already match by MessageText and MessageType (keeping their original timestamps).
     * Only deletes/inserts messages where the sequence diverges from what's already stored.
     */
    @Override
    public void saveAll(@NotNull String conversationId, @NotNull List<Message> messages)
    {
        var viewContext = HttpView.currentContext();
        if (viewContext == null)
            throw new IllegalStateException("Cannot save messages without a view context");

        var user = viewContext.getUser();
        if (user == null)
            throw new IllegalStateException("Cannot save messages without a user");

        var container = viewContext.getContainer();
        if (container == null)
            throw new IllegalStateException("Cannot save messages without a container");

        var conversationTable = CoreSchema.getInstance().getTableInfoChatConversation();
        try (var tx = conversationTable.getSchema().getScope().ensureTransaction())
        {
            var filter = new SimpleFilter(FieldKey.fromParts("ConversationId"), conversationId);
            if (!new TableSelector(conversationTable, Set.of("ConversationId"), filter, null).exists())
            {
                var row = new HashMap<String, Object>();
                row.put("ConversationId", conversationId);
                row.put("Container", container.getId());

                if (viewContext.getRequest() != null)
                {
                    var referer = viewContext.getRequest().getHeader("Referer");
                    if (!StringUtils.isEmpty(referer))
                        row.put("Referer", referer);
                }

                Table.insert(user, conversationTable, row);
            }

            var messageTable = CoreSchema.getInstance().getTableInfoChatMessages();

            // Fetch existing messages ordered by RowId
            var existingRows = new ArrayList<Map<String, Object>>();
            try (var result = new TableSelector(messageTable, filter, new Sort("RowId")).getResults())
            {
                while (result.next())
                {
                    var existing = new HashMap<String, Object>();
                    existing.put("RowId", result.getInt("RowId"));
                    existing.put("MessageText", result.getString("MessageText"));
                    existing.put("MessageType", result.getString("MessageType"));
                    existingRows.add(existing);
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }

            // Find the longest prefix of messages that already match
            int matchCount = 0;
            int limit = Math.min(existingRows.size(), messages.size());
            for (int i = 0; i < limit; i++)
            {
                var existing = existingRows.get(i);
                var newMsg = messages.get(i);
                if (newMsg.getText().equals(existing.get("MessageText"))
                    && newMsg.getMessageType().getValue().equals(existing.get("MessageType")))
                {
                    matchCount++;
                }
                else
                {
                    break;
                }
            }

            // Delete existing messages beyond the matching prefix
            if (matchCount < existingRows.size())
            {
                int firstRowIdToDelete = (int) existingRows.get(matchCount).get("RowId");
                var deleteFilter = new SimpleFilter(FieldKey.fromParts("ConversationId"), conversationId);
                deleteFilter.addCondition(FieldKey.fromParts("RowId"), firstRowIdToDelete, CompareType.GTE);
                Table.delete(messageTable, deleteFilter);
            }

            // Insert only new messages beyond the matching prefix
            for (int i = matchCount; i < messages.size(); i++)
            {
                var message = messages.get(i);
                var rowMap = new HashMap<String, Object>();
                rowMap.put("ConversationId", conversationId);
                rowMap.put("MessageText", message.getText());
                rowMap.put("MessageType", message.getMessageType().getValue());

                if (MessageType.ASSISTANT.equals(message.getMessageType()))
                {
                    String md = message.getText();
                    HtmlString html = HtmlString.unsafe(MarkdownService.get().toHtml(md));

                    rowMap.put("ContentType", "text/markdown");
                    rowMap.put("MessageTextFormatted", html);
                }
                else
                {
                    rowMap.put("ContentType", "text/plain");
                    rowMap.put("MessageTextFormatted", message.getText());
                }

                if (message.getMetadata() != null && !message.getMetadata().isEmpty())
                {
                    var json = new JSONObject(message.getMetadata());
                    rowMap.put("MessageMetadata", json.toString());
                }

                Table.insert(user, messageTable, rowMap);
            }

            tx.commit();
        }
    }

    @Override
    public void deleteByConversationId(@NotNull String conversationId)
    {
        // Messages are deleted on cascade
        Table.delete(CoreSchema.getInstance().getTableInfoChatConversation(), conversationId);
    }
}
