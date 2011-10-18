<%
/*
 * Copyright (c) 2006-2011 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    String cancelLink = getViewContext().getActionURL().getParameter("returnURL");
    if (cancelLink == null || cancelLink.length() == 0)
        cancelLink = "manageStudy.view";
%>
<script type="text/javascript">
    function removeProtocolDocument(name, xid)
    {
        if (Ext)
        {
            function remove()
            {
                var params = {
                    name: name
                };

                Ext.Ajax.request({
                    url    : LABKEY.ActionURL.buildURL('study', 'removeProtocolDocument'),
                    method : 'POST',
                    success: function() {
                        var el = document.getElementById(xid);
                        if (el) {
                            el.parentNode.removeChild(el);
                        }
                    },
                    failure: function() {
                        alert('Failed to remove study protocol document.');
                    },
                    params : params
                });
            }

            Ext.Msg.show({
                title : 'Remove Attachment',
                msg : 'Please confirm you would like to remove this study protocol document. This cannot be undone.',
                buttons: Ext.Msg.OKCANCEL,
                icon: Ext.MessageBox.QUESTION,
                fn  : function(b) {
                    if (b == 'ok') {
                        remove();
                    }
                }
            });
        }
    }
</script>
<form action="updateStudyProperties.post" method="POST" enctype="multipart/form-data">
    <input type="hidden" name="returnURL" value="<%= h(getViewContext().getActionURL().getParameter("returnURL")) %>">
    <table>
        <tr>
            <td class="labkey-form-label">Study Label</td>
            <td><input type="text" size="40" name="label" value="<%= h(getStudy().getLabel()) %>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Description</td>
            <td>
                <textarea name="description" rows="20" cols="80"><%= h(getStudy().getDescription()) %></textarea>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Render&nbsp;As</td>
            <td>
                <select name="descriptionRendererType">
                      <%
                          for (WikiRendererType type : getRendererTypes())
                          {
                              String value = type.name();
                              String displayName = type.getDisplayName();
                              String selected = type == currentRendererType() ? "selected " : "";
                          %>
                              <option <%=selected%> value="<%=h(value)%>"><%=h(displayName)%></option>
                          <%
                      }%>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Protocol Documents</td>
            <td>
                <table id="filePickerTable">
                    <tbody>
                        <%
                            int x = -1;
                            for (Attachment att : getStudy().getProtocolDocuments())
                        {
                            x++;
                            %><tr id="attach-<%=x%>">
                                <td><img src="<%=request.getContextPath() + att.getFileIcon()%>" alt="logo"/>&nbsp;<%= h(att.getName()) %></td>
                                <td><a onclick="removeProtocolDocument(<%=PageFlowUtil.jsString(att.getName())%>, 'attach-<%=x%>'); ">remove</a></td>
                            </tr><%
                        }
                        %>
                    </tbody>
                </table>
                <table>
                    <tbody>
                        <tr><td><a href="javascript:addFilePicker('filePickerTable','filePickerLink')" id="filePickerLink"><img src="<%=request.getContextPath()%>/_images/paperclip.gif">&nbsp;Attach a file</a></td></tr>
                    </tbody>
                </table>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td><%= generateSubmitButton("Update")%>&nbsp;<%= generateButton("Cancel", cancelLink)%></td>
        </tr>
    </table>
</form>