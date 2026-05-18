package org.labkey.api.mcp;

public class PromptForm
{
    public String conversationId;
    public String prompt;

    public String getConversationId()
    {
        return conversationId;
    }

    public void setConversationId(String conversationId)
    {
        this.conversationId = conversationId;
    }

    public void setPrompt(String prompt)
    {
        this.prompt = prompt;
    }

    public String getPrompt()
    {
        return this.prompt;
    }
}
