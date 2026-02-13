<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<style>
  .footer-content {
    display: none;
  }

  DIV.chatItem {
    margin: 5px;
    padding: 5px;
    background-color: #4CAF50;
    border-radius: 15px;
    border : solid 1px darkgray;
    position: relative;
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  }

  .chatTimestamp {
    position: absolute;
    top: 6px;
    right: 12px;
    font-size: 11px;
    opacity: 0.5;
  }

  DIV.userPrompt {
    margin: 5px;
    margin-right: 20px;
    background-color : white;
  }

  DIV.genaiResponse {
    margin: 5px;
    margin-left: 20px;
    background-color: lightgray;
  }

  DIV.sqlResponse {
    margin-left: 10px;
    background-color: pink;
  }
</style>
<div class="panel panel-default">
    <div class="panel-body" id="conversationMeta">
        <span class="text-muted">Loading...</span>
    </div>
</div>
<div class="panel panel-default">
    <div class="panel-heading">
        <h3 class="panel-title">Messages</h3>
    </div>
    <div class="panel-body" id="querySourceLayout">
        <div id="chatHistory"></div>
    </div>
</div>
<script type="application/javascript" nonce="<%=getScriptNonce()%>">
    LABKEY.Utils.onReady(() => {
        function addChatItem(item) {
            document.getElementById('chatHistory').appendChild(item);
        }

        function createTimestamp(created) {
            const span = document.createElement('span');
            span.className = 'chatTimestamp';
            span.textContent = created ? new Date(created).toLocaleString() : '';
            return span;
        }

        function appendHtmlResponse(html, created) {
            const chatItem = document.createElement('div');
            chatItem.className = 'chatItem genaiResponse';
            chatItem.appendChild(createTimestamp(created));
            chatItem.insertAdjacentHTML('beforeend', html);
            addChatItem(chatItem);
        }

        function appendTextResponse(text, created) {
            const chatItem = document.createElement('div');
            chatItem.className = 'chatItem genaiResponse';
            chatItem.appendChild(createTimestamp(created));
            chatItem.appendChild(document.createTextNode(text));
            addChatItem(chatItem);
        }

        function appendUserPrompt(text, created) {
            const chatItem = document.createElement('div');
            chatItem.className = 'chatItem userPrompt';
            chatItem.appendChild(createTimestamp(created));
            chatItem.appendChild(document.createTextNode(text));
            addChatItem(chatItem);
        }

        const conversationId = LABKEY.ActionURL.getParameter('id');

        // Fetch conversation metadata for the header
        LABKEY.Query.selectRows({
            schemaName: 'core',
            queryName: 'ChatConversation',
            columns: ['ConversationId', 'CreatedBy', 'CreatedBy/DisplayName', 'Created', 'Referer'],
            filterArray: [LABKEY.Filter.create('ConversationId', conversationId)],
            success: (data) => {
                const metaEl = document.getElementById('conversationMeta');
                if (data.rows.length > 0) {
                    const row = data.rows[0];
                    const displayName = row['CreatedBy/DisplayName'] || 'Unknown';
                    const created = row.Created ? new Date(row.Created).toLocaleString() : 'Unknown';
                    const referer = row.Referer || '';

                    let html = '<dl class="dl-horizontal" style="margin-bottom: 0;">'
                        + '<dt>User</dt><dd>' + LABKEY.Utils.encodeHtml(displayName) + '</dd>'
                        + '<dt>Date</dt><dd>' + LABKEY.Utils.encodeHtml(created) + '</dd>';

                    if (referer) {
                        html += '<dt>Source</dt><dd><a href="' + LABKEY.Utils.encodeHtml(referer) + '">'
                            + LABKEY.Utils.encodeHtml(referer) + '</a></dd>';
                    }

                    html += '</dl>';
                    metaEl.innerHTML = html;
                } else {
                    metaEl.innerHTML = '<span class="text-muted">Conversation not found</span>';
                }
            },
            failure: () => {
                document.getElementById('conversationMeta').innerHTML =
                    '<span class="text-danger">Failed to load conversation details</span>';
            },
        });

        // Fetch chat messages
        LABKEY.Query.selectRows({
            schemaName: 'core',
            queryName: 'ChatMessage',
            filterArray: [LABKEY.Filter.create('conversationId', conversationId)],
            success: (data) => {
                let firstUser = true;
                data.rows.forEach(row => {
                    if (row.MessageType === 'user') {
                        let text = row.MessageText;
                        if (firstUser) {
                            const idx = text.indexOf('\n\n');
                            if (idx !== -1) {
                                text = text.substring(idx + 2);
                            }
                            firstUser = false;
                        }
                        appendUserPrompt(text, row.Created);
                    } else if (row.MessageType === 'assistant') {
                        if (row.ContentType === 'text/markdown') {
                            appendHtmlResponse(row.MessageTextFormatted, row.Created);
                        } else {
                            appendHtmlResponse(row.MessageTextFormatted, row.Created);
                        }
                    } else {
                        appendTextResponse(row.MessageText, row.Created);
                    }
                });
            },
            failure: () => {},
        });
    });
</script>