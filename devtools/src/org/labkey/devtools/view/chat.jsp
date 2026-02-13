<%@ page import="org.labkey.api.util.DOM" %>
<%@ page import="java.util.stream.Stream" %>
<%@ page import="static org.labkey.api.util.DOM.*" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<style type="text/css">

    DIV.chatItem {
      margin: 5px;
      padding: 5px;
      background-color: #4CAF50;
      border-radius: 15px;
      border : solid 1px darkgray;
      display: flex;
      align-items: center; */
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }

    DIV.userPrompt {
      margin-right: 20px;
      background-color : white;
    }

    DIV.genaiResponse {
      margin-left: 20px;
      background-color: lightgray;
    }

    .loading-spinner {
      margin: 8px 0;
    }

    .loading-spinner--hidden {
      display: none;
    }

</style>
<labkey:script>
function startChatting(chatEndpoint)
{
    var elPrompt = document.getElementById('chatPrompt');

    function scrollToBottom()
    {
        const div = document.getElementById("chatHistory");
        div.scrollTo({ top: div.scrollHeight, behavior: "smooth" });
    }

    function appendUserPrompt(text)
    {
        const chatItem = document.createElement('div');
        chatItem.className = 'chatItem userPrompt';
        chatItem.innerText = text;
        document.getElementById('chatHistory').appendChild(chatItem);
        scrollToBottom();
    }

    function appendTextResponse(text)
    {
        appendResponse({contentType:"text/plain", response:text});
    }

    function appendResponse(res)
    {
        const chatItem = document.createElement('div');
        chatItem.className = 'chatItem genaiResponse';

        if ("text/html" === res['contentType'])
            chatItem.innerHTML = res.response;
        else
            chatItem.innerText = res.response;

        document.getElementById('chatHistory').appendChild(chatItem);
        scrollToBottom();
    }

    function handleChatResponse(event)
    {
        const req = event.target;
        if (req.readyState === 4) {
            if (req.status >= 200 && req.status < 300)
            {
                appendResponse(JSON.parse(req.responseText));
            }
            else
            {
                appendTextResponse('Request failed: ' + req.status + ' ' + (req.statusText || ''));
            }

            const loadingSpinner = document.querySelector('.loading-spinner');
            loadingSpinner.classList.add('loading-spinner--hidden');
        }
    }

    function sendMessage(prompt)
    {
        var url = new URL(chatEndpoint);
        url.searchParams.set('prompt', prompt);
        var req = new XMLHttpRequest();
        req.open('GET', url.toString(), true);
        req.onreadystatechange = handleChatResponse;
        req.send();
        const loadingSpinner = document.querySelector('.loading-spinner');
        loadingSpinner.classList.remove('loading-spinner--hidden');
    }

    let firstChat = true;
    elPrompt.addEventListener('keydown', function (ev)
    {
        var isEnter = (ev.key === 'Enter') || (ev.keyCode === 13);
        if (ev.shiftKey && isEnter)
        {
            const prompt = elPrompt.value;
            appendUserPrompt(prompt);
            elPrompt.value = '';
            sendMessage(prompt);
            ev.preventDefault();
            ev.stopPropagation();
            return false;
        }
        return true;
    });

    sendMessage("Please introduce yourself");
}

LABKEY.Utils.onReady(function()
    {
        startChatting(new URL("./test-chatEndpoint.api", window.location.href));
    }
);
</labkey:script>

<div id="chatHistory"></div>

<div class="loading-spinner loading-spinner--hidden">
    <span aria-hidden="true" class="fa fa-spinner fa-pulse"></span>
</div>

<textarea id="chatPrompt" style="height:100px; width:100%;" placeholder="Shift-Enter to submit"></textarea>
