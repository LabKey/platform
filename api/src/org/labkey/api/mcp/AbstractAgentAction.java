package org.labkey.api.mcp;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.util.HtmlString;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.BindException;

import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * "agent" it is too strong a word, but if you want to create a tools specific chat endpoint then
 * start here.
 * First implement getServicePrompt() to tell your "agent its mission.  You can also listen in on the
 * conversation to help you user get the right results.
 */
public abstract class AbstractAgentAction<F extends PromptForm> extends ReadOnlyApiAction<F>
{
    protected abstract String getAgentName();

    protected abstract String getServicePrompt();

    protected boolean useVectorStore()
    {
        return true;
    }

    protected ChatClient getChat(boolean create)
    {
        HttpSession session = getViewContext().getRequest().getSession(true);
        ChatClient chatSession = McpService.get().getChat(session, getAgentName(), this::getServicePrompt, create, useVectorStore());
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
                 return "OK, let's start over.";
            }
        }
        return null;
    }

    @Override
    public Object execute(PromptForm form, BindException errors) throws Exception
    {
        try (var mcpPush = McpContext.withContext(getViewContext()))
        {
            String prompt = form.getPrompt();

            String escapeResponse = handleEscape(prompt);
            if (null != escapeResponse)
            {
                return new JSONObject(Map.of(
                        "contentType", "text/plain",
                        "response", escapeResponse,
                        "success", Boolean.TRUE));
            }

            // call getChat() after handleEscape()
            ChatClient chatSession = getChat(true);
            if (null == chatSession)
                return new JSONObject(Map.of(
                        "contentType", "text/plain",
                        "response", "Service is not ready yet",
                        "success", Boolean.FALSE));

            McpService.MessageResponse response = McpService.get().sendMessage(chatSession, prompt);
            var ret = new JSONObject(Map.of("success", Boolean.TRUE));
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
            var ret = new JSONObject(Map.of(
                    "text", ex.getMessage(),
                    "user", getViewContext().getUser().getName(),
                    "success", Boolean.FALSE));
            return ret;
        }
    }
}
