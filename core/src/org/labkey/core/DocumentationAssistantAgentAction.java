package org.labkey.core;

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
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

@RequiresPermission(ReadPermission.class)
@RequiresLogin
public class DocumentationAssistantAgentAction extends AbstractAgentAction<PromptForm>
{
    private static final Logger LOG = LogHelper.getLogger(DocumentationAssistantAgentAction.class, "Documentation Assistant interactions");

    @Override
    protected String getAgentName()
    {
        return DocumentationAssistantAgentAction.class.getName();
    }

    @Override
    protected String getServicePrompt()
    {
        return "Use the Server Documentation to answer the question.";
    }

    @Override
    public Object execute(PromptForm form, BindException errors) throws Exception
    {
        try (var _ = McpContext.withContext(getViewContext()))
        {
            boolean firstTurn = isBlank(form.getConversationId());
            String prompt = form.getPrompt();
            String composedPrompt = composePrompt(firstTurn, prompt);

            if (isBlank(composedPrompt))
            {
                return new JSONObject(Map.of(
                        "contentType", "text/plain",
                        "text", "🤷",
                        "success", Boolean.TRUE));
            }

            ChatClient chatSession = getChat(true);
            List<McpService.MessageResponse> responses;

            try
            {
                LOG.info("Documentation assistant prompt: {}", prompt);
                responses = McpService.get().sendMessageEx(chatSession, composedPrompt);
            }
            catch (ChatException x)
            {
                return new JSONObject(Map.of(
                        "error", x.getMessage(),
                        "text", "ERROR: " + x.getMessage(),
                        "success", Boolean.FALSE));
            }

            JSONArray segments = buildSegments(responses);
            return new JSONObject(Map.of(
                    "success", Boolean.TRUE,
                    "conversationId", getConversationId(),
                    "segments", segments));
        }
        catch (ChatException x)
        {
            return errorResponse(x);
        }
    }

    /**
     * Combines the user's prompt with any first-turn context supplied by the client. When {@code firstTurn} is false the context fields are ignored, and the
     * user's prompt is returned verbatim.
     */
    static String composePrompt(boolean firstTurn, String userPrompt)
    {
        if (!firstTurn)
            return StringUtils.defaultString(userPrompt);

        // TODO add more context here
        return StringUtils.defaultString(userPrompt);
    }

    /**
     * TODO update for this action. Will it have SQL blocks?
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

        LOG.debug("Expression assistant raw response:\n{}", text);

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

    // TODO move to a shared place
    private static void flushHtmlSegment(JSONArray segments, StringBuilder buf, MarkdownService md)
    {
        if (buf.isEmpty())
            return;

        String raw = buf.toString().strip();
        buf.setLength(0);
        if (raw.isEmpty())
            return;

        String html;
        try
        {
            html = md != null ? md.toHtml(raw) : raw;
        }
        catch (Exception x)
        {
            html = raw;
        }

        var validateErrors = new ArrayList<String>();
        PageFlowUtil.validateHtml(html, validateErrors, validateErrors);
        if (!validateErrors.isEmpty())
            throw new RuntimeValidationException("Invalid HTML markup. " + String.join("\n", validateErrors));

        segments.put(new JSONObject(Map.of("type", "html", "html", html)));
    }
}
