/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.core;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TempTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.mcp.ChatHistoryService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.mock.web.MockHttpSession;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ChatHistoryManager implements ChatHistoryService
{
    private static final Logger LOG = LogHelper.getLogger(ChatHistoryManager.class, "Assistant chat history");
    private static final int MAX_HISTORY_ROWS = 200;
    private static final Object TABLE_LOCK = new Object();

    // Held for the life of the server so TempTableTracker drops the table only at shutdown
    private static volatile @Nullable TempTableInfo _table = null;

    @Override
    public void addChatSnippet(HttpSession session, User user, Container c, String agentName, GUID conversationId, Role role, String content)
    {
        try
        {
            // Bound first, so a session that ends mid-turn can't leave an unbound row behind
            SessionHelper.getAttribute(session, getSessionKey(user.getUserId(), conversationId), () -> new SessionBinding(user.getUserId(), conversationId));
            TempTableInfo table = ensureTable();
            execute(table, new SQLFragment("INSERT INTO ").append(sqlName(table))
                    .append(" (Container, CreatedBy, Created, AgentName, ConversationId, Role, Content) VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)")
                    .addAll(c.getId(), user.getUserId(), agentName, conversationId.toString(), role.name(), content));
        }
        catch (RuntimeException x)
        {
            LOG.warn("Failed to record chat history", x);
        }
    }

    @Override
    public @NotNull List<ChatSnippet> getChatHistory(User user, GUID conversationId)
    {
        TempTableInfo table = _table;
        if (null == table)
            return List.of();

        SQLFragment sql = new SQLFragment("SELECT RowId, Container, AgentName, Role, Content, Created FROM ").append(sqlName(table))
                .append(" WHERE CreatedBy = ? AND ConversationId = ? ORDER BY RowId")
                .addAll(user.getUserId(), conversationId.toString());

        Map<String, Boolean> readable = new HashMap<>();
        List<ChatSnippet> snippets = new ArrayList<>();
        new SqlSelector(table.getSchema(), sql).forEach(rs -> {
            if (readable.computeIfAbsent(rs.getString("Container"), id -> canRead(user, id)))
                snippets.add(new ChatSnippet(rs.getInt("RowId"), rs.getString("AgentName"), Role.valueOf(rs.getString("Role")), rs.getString("Content"), rs.getTimestamp("Created")));
        });

        return snippets.size() > MAX_HISTORY_ROWS ? List.copyOf(snippets.subList(snippets.size() - MAX_HISTORY_ROWS, snippets.size())) : snippets;
    }

    @Override
    public @Nullable String getLastPromptContainerId(User user, GUID conversationId)
    {
        TempTableInfo table = _table;

        if (null == table)
            return null;

        SQLFragment sql = new SQLFragment("SELECT Container FROM ").append(sqlName(table))
                .append(" WHERE CreatedBy = ? AND ConversationId = ? AND Role = ? ORDER BY RowId DESC")
                .addAll(user.getUserId(), conversationId.toString(), Role.user.name());

        return new SqlSelector(table.getSchema(), table.getSqlDialect().limitRows(sql, 1)).getObject(String.class);
    }

    @Override
    public void deleteChatHistory(HttpSession session, User user, GUID conversationId)
    {
        session.removeAttribute(getSessionKey(user.getUserId(), conversationId));
        deleteRows(user.getUserId(), conversationId);
    }

    private static boolean canRead(User user, String containerId)
    {
        Container c = ContainerManager.getForId(containerId);
        return null != c && c.hasPermission(user, ReadPermission.class);
    }

    private static void deleteRows(int userId, GUID conversationId)
    {
        TempTableInfo table = _table;
        if (null == table)
            return;

        try
        {
            execute(table, new SQLFragment("DELETE FROM ").append(sqlName(table))
                    .append(" WHERE CreatedBy = ? AND ConversationId = ?").addAll(userId, conversationId.toString()));
        }
        catch (RuntimeException x)
        {
            // TempTableTracker drops the table at shutdown, before the remaining sessions expire
            if (ContextListener.isShuttingDown())
                LOG.debug("Failed to delete chat history during shutdown", x);
            else
                LOG.warn("Failed to delete chat history", x);
        }
    }

    // Own connection, so a caller's rollback can't undo the table or a write, and a failed write can't abort the caller's transaction
    private static void execute(TempTableInfo table, SQLFragment sql)
    {
        DbScope scope = table.getSchema().getScope();
        try (var _ = SpringActionController.ignoreSqlUpdates(); Connection conn = scope.getPooledConnection())
        {
            new SqlExecutor(scope, conn).execute(sql);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    private static SQLFragment sqlName(TempTableInfo table)
    {
        return Objects.requireNonNull(table.getSQLName());
    }

    private static String getSessionKey(int userId, GUID conversationId)
    {
        return ChatHistoryManager.class.getName() + "#" + userId + ":" + conversationId;
    }

    private static @NotNull TempTableInfo ensureTable()
    {
        TempTableInfo table = _table;
        if (null != table)
            return table;

        synchronized (TABLE_LOCK)
        {
            table = _table;
            if (null == table)
            {
                table = createTable();
                _table = table;
            }
            return table;
        }
    }

    private static TempTableInfo createTable()
    {
        // Columns are declared in the DDL below; the TableInfo supplies only the tracked name
        TempTableInfo table = new TempTableInfo("ChatHistory", List.of(), null);
        SqlDialect dialect = table.getSqlDialect();
        String guidType = dialect.getGuidType();

        // UNLOGGED keeps chat transcripts out of WAL, so they never reach standbys or point-in-time recovery; a reset only loses display history
        SQLFragment create = new SQLFragment("CREATE ").append(dialect.isPostgreSQL() ? "UNLOGGED " : "").append("TABLE ").append(sqlName(table))
                .append(" (RowId ").append(dialect.getUniqueIdentType()).append(" PRIMARY KEY")
                .append(", Container ").append(guidType).append(" NOT NULL")
                .append(", CreatedBy INT NOT NULL")
                .append(", Created ").append(dialect.getSqlTypeName(JdbcType.TIMESTAMP)).append(" NOT NULL")
                .append(", AgentName VARCHAR(200) NOT NULL")
                .append(", ConversationId ").append(guidType).append(" NOT NULL")
                .append(", Role VARCHAR(20) NOT NULL")
                .append(", Content ").append(dialect.getSqlTypeName(JdbcType.LONGVARCHAR)).append(" NOT NULL)");
        SQLFragment index = new SQLFragment("CREATE INDEX ").append("IX_ChatHistory_").append(GUID.makeHash()).append(" ON ").append(sqlName(table))
                .append(" (CreatedBy, ConversationId)");

        table.track();
        execute(table, create);
        execute(table, index);
        return table;
    }

    private record SessionBinding(int userId, GUID conversationId) implements HttpSessionBindingListener
    {
        // Unbound by deleteChatHistory(), logout, session expiry, and user impersonation
        @Override
        public void valueUnbound(HttpSessionBindingEvent event)
        {
            deleteRows(userId, conversationId);
        }
    }

    public static class TestCase extends Assert
    {
        private static final String AGENT = "ChatHistoryManager.TestCase";

        private final ChatHistoryManager _manager = new ChatHistoryManager();
        private final MockHttpSession _session = new MockHttpSession();
        private final User _user = TestContext.get().getUser();
        private final Container _container = JunitUtil.getTestContainer();
        private final GUID _conversationId = new GUID();

        @After
        public void cleanup()
        {
            if (!_session.isInvalid())
                _session.invalidate();
        }

        @Test
        public void testAddGetDelete()
        {
            _manager.addChatSnippet(_session, _user, _container, AGENT, _conversationId, Role.user, "How do I configure LDAP?");
            _manager.addChatSnippet(_session, _user, _container, AGENT, _conversationId, Role.error, "The chat model request failed.");
            _manager.addChatSnippet(_session, _user, _container, AGENT, _conversationId, Role.user, "How do I configure LDAP?");
            _manager.addChatSnippet(_session, _user, _container, AGENT, _conversationId, Role.assistant, "[{\"type\":\"html\",\"html\":\"<p>Like this</p>\"}]");

            List<ChatSnippet> snippets = _manager.getChatHistory(_user, _conversationId);
            assertEquals(List.of(Role.user, Role.error, Role.user, Role.assistant), snippets.stream().map(ChatSnippet::role).toList());
            assertEquals("The chat model request failed.", snippets.get(1).content());
            assertEquals(AGENT, snippets.getFirst().agentName());
            for (int i = 1; i < snippets.size(); i++)
                assertTrue("Ordered by RowId", snippets.get(i - 1).rowId() < snippets.get(i).rowId());

            assertEquals("Other conversations are separate", List.of(), _manager.getChatHistory(_user, new GUID()));

            _manager.deleteChatHistory(_session, _user, _conversationId);
            assertEquals(List.of(), _manager.getChatHistory(_user, _conversationId));
        }

        @Test
        public void testLastPromptContainerId()
        {
            Container shared = ContainerManager.getSharedContainer();
            assertNull(_manager.getLastPromptContainerId(_user, _conversationId));

            _manager.addChatSnippet(_session, _user, _container, AGENT, _conversationId, Role.user, "First");
            _manager.addChatSnippet(_session, _user, shared, AGENT, _conversationId, Role.assistant, "[]");
            _manager.addChatSnippet(_session, _user, shared, AGENT, _conversationId, Role.error, "Failed");
            assertEquals("Only prompts count", _container.getId(), _manager.getLastPromptContainerId(_user, _conversationId));

            _manager.addChatSnippet(_session, _user, shared, AGENT, _conversationId, Role.user, "Second");
            assertEquals(shared.getId(), _manager.getLastPromptContainerId(_user, _conversationId));

            User other = new User("chathistory-other@example.com", Integer.MAX_VALUE - 1);
            _manager.addChatSnippet(_session, other, _container, AGENT, _conversationId, Role.user, "Not yours");
            assertEquals("Other users' prompts don't count", shared.getId(), _manager.getLastPromptContainerId(_user, _conversationId));
            assertNull("Other conversations are separate", _manager.getLastPromptContainerId(_user, new GUID()));
        }

        @Test
        public void testOtherUsersRowsAreHidden()
        {
            User other = new User("chathistory-other@example.com", Integer.MAX_VALUE - 1);
            _manager.addChatSnippet(_session, other, _container, AGENT, _conversationId, Role.user, "Not yours");
            _manager.addChatSnippet(_session, _user, _container, AGENT, _conversationId, Role.user, "Yours");

            assertEquals(List.of("Yours"), _manager.getChatHistory(_user, _conversationId).stream().map(ChatSnippet::content).toList());
        }

        @Test
        public void testSessionEndDeletesRows()
        {
            _manager.addChatSnippet(_session, _user, _container, AGENT, _conversationId, Role.user, "Hello");
            assertEquals(1, _manager.getChatHistory(_user, _conversationId).size());

            _session.invalidate();
            assertEquals(List.of(), _manager.getChatHistory(_user, _conversationId));
        }

        @Test
        public void testTableIsUnlogged()
        {
            _manager.addChatSnippet(_session, _user, _container, AGENT, _conversationId, Role.user, "Hello");
            TempTableInfo table = _table;
            assertNotNull(table);
            Assume.assumeTrue("UNLOGGED is PostgreSQL only", table.getSqlDialect().isPostgreSQL());

            SQLFragment sql = new SQLFragment("SELECT relpersistence FROM pg_class WHERE oid = CAST(? AS regclass)").add(table.getSelectName());
            assertEquals("u", new SqlSelector(table.getSchema(), sql).getObject(String.class));
        }
    }
}
