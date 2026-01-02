package org.labkey.api.mcp;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.util.HtmlString;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.BindException;

import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public abstract class AbstractAgentAction<F extends PromptForm> extends ReadOnlyApiAction<F>
{
    protected abstract String getAgentName();

    protected abstract String getServicePrompt();

    protected ChatClient getChat()
    {
        HttpSession session = getViewContext().getRequest().getSession(true);
        ChatClient chatSession = McpService.get().getChat(session, getAgentName(), this::getServicePrompt);
        return chatSession;
    }

    @Override
    public Object execute(PromptForm form, BindException errors) throws Exception
    {
        try (var mcpPush = McpContext.withContext(getViewContext()))
        {
            ChatClient chatSession = getChat();
            String prompt = form.getPrompt();
            McpService.MessageResponse response = McpService.get().sendMessage(chatSession, prompt);
            var ret = new JSONObject(Map.of("success", Boolean.TRUE));
            if (!HtmlString.isBlank(response.html()))
            {
                ret.put("contentType", "text/html");
                ret.put("response", response.html());
            }
            else if (isNotBlank(response.markdown()))
            {
                ret.put("contentType", "text/markdown");
                ret.put("response", response.html());
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
