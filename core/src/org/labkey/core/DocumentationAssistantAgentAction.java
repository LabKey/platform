package org.labkey.core;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.mcp.AbstractAgentAction;
import org.labkey.api.mcp.ChatException;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.mcp.PromptForm;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.AbortedRequestException;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

@RequiresPermission(ReadPermission.class)
@RequiresLogin
public class DocumentationAssistantAgentAction extends AbstractAgentAction<PromptForm>
{
    private static final Logger LOG = LogHelper.getLogger(DocumentationAssistantAgentAction.class, "Documentation Assistant interactions");
    private static final String UNEXPECTED_ERROR_MESSAGE = "Request failed. Please try again.";
    private static final String PROMPT_RESOURCE = "org/labkey/core/prompts/DocumentationAssistant.md";

    @Override
    protected String getAgentName()
    {
        return DocumentationAssistantAgentAction.class.getName();
    }

    @Override
    protected String getServicePrompt()
    {
        try
        {
            return IOUtils.resourceToString(PROMPT_RESOURCE, null, DocumentationAssistantAgentAction.class.getClassLoader());
        }
        catch (IOException x)
        {
            throw new ConfigurationException("error loading resource", x);
        }
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

            boolean firstTurn = isBlank(form.getConversationId());
            String composedPrompt = composePrompt(firstTurn, prompt);

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

    /**
     * Combines the user's prompt with any first-turn context supplied by the client. When {@code firstTurn} is false
     * the context fields are ignored, and the user's prompt is returned verbatim.
     */
    static String composePrompt(boolean firstTurn, String userPrompt)
    {
        if (!firstTurn)
            return StringUtils.defaultString(userPrompt);

        // TODO add more context here
        return StringUtils.defaultString(userPrompt);
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

        LOG.debug("Documentation assistant raw response:\n{}", text);

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
}
