package org.labkey.core;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.mcp.AbstractAgentAction;
import org.labkey.api.mcp.ChatException;
import org.labkey.api.mcp.ChatHistoryService;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.mcp.PromptForm;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.AbortedRequestException;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

@RequiresPermission(ReadPermission.class)
@RequiresLogin
public class LabKeyAssistantAgentAction extends AbstractAgentAction<PromptForm>
{
    private static final Logger LOG = LogHelper.getLogger(LabKeyAssistantAgentAction.class, "Labkey Assistant interactions");
    private static final String UNEXPECTED_ERROR_MESSAGE = "Request failed. Please try again.";
    private static final String PROMPT_RESOURCE = "org/labkey/core/prompts/LabKeyAssistant.md";

    @Override
    protected String getAgentName()
    {
        return LabKeyAssistantAgentAction.class.getName();
    }

    @Override
    protected String getServicePrompt()
    {
        try
        {
            return IOUtils.resourceToString(PROMPT_RESOURCE, null, LabKeyAssistantAgentAction.class.getClassLoader()) +
                    "\n## This server\n\nThis server is running LabKey version " + AppProps.getInstance().getReleaseVersion() + ".\n";
        }
        catch (IOException x)
        {
            throw new ConfigurationException("error loading resource", x);
        }
    }

    @Override
    protected Set<String> getToolNames()
    {
        // This is a list of most tools. We're skipping tools like executeSQL right now because it returns rows of data,
        // which could potentially return PHI, and we don't want PHI leaking into the ChatHistory, even if it's a temp
        // table.
        return Set.of(
                "whereAmIWhoAmITalkingTo",
                "getMyPermissions",
                "listModules",
                "listContainers",
                "siteSearch",
                "listSearchCategories",
                "searchDocumentation",
                "retrieveDocument",
                "listResources",
                "readResource",
                "listSchemas",
                "listTables",
                "listColumns",
                "getSourceForSavedQuery",
                "validateSQL"
        );
    }

    @Override
    protected boolean recordsHistory()
    {
        return true;
    }

    @Override
    public Object execute(PromptForm form, BindException errors) throws Exception
    {
        try (var _ = McpContext.withContext(getViewContext()))
        {
            McpContext.get().put(McpContext.AGENT_NAME_ATTR, getAgentName());

            String prompt = form.getPrompt();

            JSONObject escapeResponse = escapeResponse(prompt);
            if (null != escapeResponse)
                return escapeResponse;

            String composedPrompt = composePrompt(prompt, isBlank(form.getConversationId()));

            if (isBlank(composedPrompt))
            {
                return new JSONObject(Map.of(
                        "contentType", "text/plain",
                        "text", "🤷",
                        "conversationId", getConversationId(),
                        "success", Boolean.TRUE));
            }

            recordPrompt(prompt);

            try
            {
                return recordResponse(sendPrompt(composedPrompt));
            }
            catch (ChatException x)
            {
                return recordResponse(failureResponse(StringUtils.defaultIfBlank(x.getMessage(), UNEXPECTED_ERROR_MESSAGE)));
            }
            catch (AbortedRequestException x)
            {
                throw x;
            }
            catch (RuntimeException x)
            {
                // Answered rather than thrown so the client shows the same text that history replays
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), x);
                return recordResponse(failureResponse(UNEXPECTED_ERROR_MESSAGE));
            }
        }
    }

    private JSONObject sendPrompt(String prompt)
    {
        ChatClient chatSession = getChat(true);

        if (null == chatSession)
            return failureResponse("Service is not ready yet");

        List<McpService.MessageResponse> responses = McpService.get().sendMessageEx(chatSession, prompt);
        JSONArray segments = buildSegments(responses);

        return new JSONObject(Map.of(
                "success", Boolean.TRUE,
                "conversationId", getConversationId(),
                "segments", segments));
    }

    private JSONObject failureResponse(String message)
    {
        return new JSONObject(Map.of(
                "error", message,
                "text", "ERROR: " + message,
                "conversationId", getConversationId(),
                "success", Boolean.FALSE));
    }

    private String composePrompt(String prompt, boolean firstTurn)
    {
        String previousContainerId = firstTurn ? null : ChatHistoryService.get().getLastPromptContainerId(getUser(), getConversationId());
        return composePrompt(prompt, previousContainerId, getContainer(), getUser());
    }

    /** Prefixes the user's prompt with a note when they've moved to another container since their last prompt. */
    static String composePrompt(String userPrompt, @Nullable String previousContainerId, Container current, User user)
    {
        if (isBlank(userPrompt) || null == previousContainerId || previousContainerId.equals(current.getId()))
            return StringUtils.defaultString(userPrompt);

        // Name the old folder only if the user can still read it
        Container previous = ContainerManager.getForId(previousContainerId);
        String from = null != previous && previous.hasPermission(user, ReadPermission.class)
                ? "the folder \"" + previous.getPath() + "\""
                : "a folder they can no longer access";

        return "[The user has moved from " + from + " to the folder \"" + current.getPath() +
                "\" since their last message. Their questions are now about \"" + current.getPath() +
                "\". Call whereAmIWhoAmITalkingTo again if you need its details.]\n\n" + userPrompt;
    }

    /**
     * Walks the markdown of each MessageResponse and produces an ordered list of segments.
     * Each segment is either a rendered-HTML span or a fenced SQL block. SQL blocks fenced as
     * `expression` are tagged "expression" (the model's assertion that this SQL has been validated
     * and is safe to apply); blocks fenced as `sql` are tagged "sql" (illustrative / unvalidated).
     * For `expression` blocks the body is expected to be the JSON returned by
     * validateCalculatedColumnExpression — at minimum {@code {"expression": "..."}}, optionally
     * with {@code "jdbcType"}.
     */
    private static JSONArray buildSegments(List<McpService.MessageResponse> responses)
    {
        JSONArray segments = new JSONArray();
        MarkdownService md = MarkdownService.get();
        StringBuilder htmlBuf = new StringBuilder();

        // Scan the turns as one document: tool calling can split a single assistant turn so that a
        // fence opens in one MessageResponse and closes in the next.
        String text = responses.stream()
                .map(McpService.MessageResponse::text)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));

        LOG.debug("LabKey assistant raw response:\n{}", text);

        // TODO: update this to handle SQL blocks like we do in the ExpressionAssistant

        String[] lines = text.split("\n", -1);
        int i = 0;
        while (i < lines.length)
        {
            if (!htmlBuf.isEmpty()) htmlBuf.append("\n");
            htmlBuf.append(lines[i]);
            i++;
        }

        flushHtmlSegment(segments, htmlBuf, md);

        return segments;
    }

    public static class TestCase extends Assert
    {
        private static final String FOLDER_NAME = "LabKeyAssistantTestCase";

        private final User _user = TestContext.get().getUser();
        private final Container _junit = JunitUtil.getTestContainer();

        @Before
        @After
        public void cleanup()
        {
            Container folder = ContainerManager.getForPath(_junit.getPath() + "/" + FOLDER_NAME);
            if (null != folder)
                ContainerManager.deleteAll(folder, _user);
        }

        @Test
        public void testPromptUnchangedWithoutMove()
        {
            assertEquals("No earlier prompt", "Hi", composePrompt("Hi", null, _junit, _user));
            assertEquals("Same folder", "Hi", composePrompt("Hi", _junit.getId(), _junit, _user));
            assertEquals("Blank prompt", "", composePrompt(null, new GUID().toString(), _junit, _user));
        }

        @Test
        public void testMoveNamesReadableFolder()
        {
            Container folder = ContainerManager.createContainer(_junit, FOLDER_NAME, _user);
            String composed = composePrompt("Hi", _junit.getId(), folder, _user);
            assertTrue(composed, composed.contains("from the folder \"" + _junit.getPath() + "\" to the folder \"" + folder.getPath() + "\""));
            assertTrue(composed, composed.endsWith("\n\nHi"));
        }

        @Test
        public void testMoveHidesUnreadableFolder()
        {
            Container folder = ContainerManager.createContainer(_junit, FOLDER_NAME, _user);
            // A non-empty policy of its own, so the folder doesn't inherit the parent's
            MutableSecurityPolicy policy = new MutableSecurityPolicy(folder);
            policy.addRoleAssignment(_user, ReaderRole.class);
            SecurityPolicyManager.savePolicyForTests(policy, _user);
            User other = new User("docassistant-other@example.com", Integer.MAX_VALUE - 2);
            assertFalse(folder.hasPermission(other, ReadPermission.class));

            String composed = composePrompt("Hi", folder.getId(), _junit, other);
            assertTrue(composed, composed.contains("from a folder they can no longer access to the folder \"" + _junit.getPath() + "\""));
            assertFalse(composed, composed.contains(folder.getPath()));
        }

        @Test
        public void testMoveFromDeletedFolder()
        {
            String composed = composePrompt("Hi", new GUID().toString(), _junit, _user);
            assertTrue(composed, composed.contains("from a folder they can no longer access"));
        }
    }
}
