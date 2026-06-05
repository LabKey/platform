package org.labkey.api.mcp;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.SessionHelper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.labkey.api.action.SpringActionController.ERROR_GENERIC;

/**
 * "Agent" it is too strong a word, but if you want to create a tools-specific chat endpoint, then
 * start here.
 * First implement getServicePrompt() to tell your "agent its mission.  You can also listen in on the
 * conversation to help your user get the right results.
 */
public abstract class AbstractAgentAction<F extends PromptForm> extends ReadOnlyApiAction<F>
{
    private static final int MAX_ISSUED_CONVERSATION_IDS = 16;
    private static final int MAX_PROMPT_CHAR_LENGTH = 4_000;

    protected GUID conversationId;

    protected abstract String getAgentName();

    protected abstract String getServicePrompt();

    protected ChatClient getChat(boolean create)
    {
        String conversationName = getAgentName() + ":" + getConversationId();

        HttpSession session = getViewContext().getSession();
        ChatClient chatSession = McpService.get().getChat(session, conversationName, this::getServicePrompt, create);

        return chatSession;
    }

    protected String handleEscape(String prompt)
    {
        prompt = StringUtils.trimToEmpty(prompt);
        switch (prompt)
        {
            case "/clear" ->
            {
                ChatClient chatSession = getChat(false); // CONSIDER: getChat(boolean ifStarted)
                if (null != chatSession)
                    McpService.get().close(getViewContext().getSession(), chatSession);
                getIssuedConversationIds(getViewContext().getSession()).remove(conversationId);
                return "OK, let's start over.";
            }
        }
        return null;
    }

    @Override
    public void validateForm(F form, Errors errors)
    {
        String prompt = form.getPrompt();
        if (prompt != null && prompt.length() > MAX_PROMPT_CHAR_LENGTH)
            errors.rejectValue("prompt", ERROR_GENERIC, "Prompt cannot exceed " + MAX_PROMPT_CHAR_LENGTH + " characters.");

        // Only honor a client-supplied conversationId if this agent previously issued this session that
        // id. Otherwise, generate a fresh one. This prevents a same-session caller from splicing into
        // another conversation by guessing or replaying a GUID.
        Set<GUID> issued = getIssuedConversationIds(getViewContext().getSession());
        String supplied = form.getConversationId();
        if (supplied != null)
        {
            GUID candidate;

            try
            {
                candidate = new GUID(supplied);
            }
            catch (IllegalArgumentException e)
            {
                errors.rejectValue("conversationId", ERROR_GENERIC, "Invalid conversationId");
                return;
            }

            if (issued.contains(candidate))
            {
                conversationId = candidate;
                return;
            }
        }
        conversationId = new GUID();
        issued.add(conversationId);
    }

    @Override
    public Object execute(PromptForm form, BindException errors) throws Exception
    {
        try (var _ = McpContext.withContext(getViewContext()))
        {
            String prompt = form.getPrompt();

            JSONObject escapeResponse = escapeResponse(prompt);
            if (null != escapeResponse)
                return escapeResponse;

            // call getChat() after handleEscape()
            ChatClient chatSession = getChat(true);
            if (null == chatSession)
            {
                return new JSONObject(Map.of(
                        "contentType", "text/plain",
                        "response", "Service is not ready yet",
                        "success", Boolean.FALSE));
            }

            McpService.MessageResponse response = McpService.get().sendMessage(chatSession, prompt);
            var ret = new JSONObject(Map.of("success", Boolean.TRUE, "conversationId", conversationId));
            if (!HtmlString.isBlank(response.html()))
            {
                ret.put("contentType", "text/html");
                ret.put("response", response.html());
            }
            else if (isNotBlank(response.text()))
            {
                ret.put("contentType", response.contentType());
                ret.put("response", response.text());
            }
            else
            {
                ret.put("contentType", "text/plain");
                ret.put("response", "I got nothing");
            }
            return ret;
        }
        catch (ServerException x)
        {
            return new JSONObject(Map.of(
                    "error", x.getMessage(),
                    "text", "ERROR: " + x.getMessage(),
                    "success", Boolean.FALSE));
        }
        catch (ClientException ex)
        {
            return errorResponse(ex);
        }
    }

    protected @NotNull JSONObject errorResponse(Exception ex)
    {
        return new JSONObject(Map.of(
                "text", ex.getMessage(),
                "user", getViewContext().getUser().getName(),
                "success", Boolean.FALSE));
    }

    protected @Nullable JSONObject escapeResponse(String prompt)
    {
        String escapeResponse = handleEscape(prompt);
        if (null == escapeResponse)
            return null;

        return new JSONObject(Map.of(
                "contentType", "text/plain",
                "text", escapeResponse,
                "conversationId", conversationId,
                "success", Boolean.TRUE));
    }

    protected GUID getConversationId()
    {
        return conversationId;
    }

    private String getIssuedConversationIdsKey()
    {
        return getAgentName() + "#issuedConversationIds";
    }

    private Set<GUID> getIssuedConversationIds(HttpSession session)
    {
        return SessionHelper.getAttribute(session, getIssuedConversationIdsKey(), AbstractAgentAction::newIssuedConversationIdSet);
    }

    private static Set<GUID> newIssuedConversationIdSet()
    {
        return Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<>(16, 0.75f, false)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<GUID, Boolean> eldest)
                {
                    return size() > MAX_ISSUED_CONVERSATION_IDS;
                }
            }
        ));
    }
}
