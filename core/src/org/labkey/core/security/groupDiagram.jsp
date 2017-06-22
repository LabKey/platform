<%
/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.security.SecurityController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Permissions");
    }
%>
<%
    ActionURL groupDiagramURL = urlFor(SecurityController.GroupDiagramAction.class);
%>
<div id="unconnected" style="padding:5px;"></div>
<div id="groupDiagram"></div>
<script type="text/javascript">
    Ext4.onReady(function() {
        if (Ext4.isIE && (Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8))
        {
            render("This feature is not supported on older versions of Internet Explorer. " +
                "Please upgrade to the latest version of Internet Explorer, or switch to using Firefox, Chrome, or Safari. " +
                "See <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=supportedBrowsers' target='browserVersions'>this page</a> for more information.");
        }
        else if (Ext4.isGecko2 || Ext4.isGecko3)
        {
            render("This feature is not supported on older versions of Firefox; please upgrade to the latest version of Firefox. " +
                "See <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=supportedBrowsers' target='browserVersions'>this page</a> for more information.");
        }
        else
        {
            var refreshTask = new Ext4.util.DelayedTask(refreshDiagram);
            var refresh  = function() {
                refreshTask.delay(250);
            };

            this.hideUnconnectedCheckbox = Ext4.create('Ext.form.field.Checkbox', {
                renderTo : 'unconnected',
                id       : 'hideUnconnectedCheckbox',
                style    : {display:'inline'},
                boxLabel : 'Hide unconnected nodes',
                listeners : {
                    change : function(){
                        refreshTask.delay(0);
                    },
                    scope : this
                }
            });

            var cache = Security.util.SecurityCache.getGlobalCache();
            if (cache)
            {
                cache.getPrincipalsStore().on({
                    add : {fn: refresh, scope: this},
                    remove : {fn: refresh, scope: this},
                    update : {fn: refresh, scope: this}
                });
            }
            else
            {
                console.warn('Group Diagram: Security cache not available.');
            }
            refresh();
        }
    });

    function refreshDiagram(s, record, type)
    {
        var urlString = <%=q(groupDiagramURL.toString())%>;
        if (this.hideUnconnectedCheckbox.getValue())
        {
            urlString = <%=q(groupDiagramURL.addParameter("hideUnconnected", true).toString())%>;
        }

        Ext4.Ajax.request({
            url: urlString,
            success: renderGroupDiagram,
            failure: onError
        });
    }

    function renderGroupDiagram(response)
    {
        var bean = Ext4.JSON.decode(response.responseText);
        render(bean.html);
    }

    function onError(response)
    {
        if (response.responseText)
        {
            var bean = Ext4.JSON.decode(response.responseText);

            if (bean.exception)
            {
                render("Error: " + bean.exception);
                return;
            }
        }

        render("Error generating or retrieving diagram");
    }

    function render(html)
    {
        Ext4.fly("groupDiagram").update(html);
    }

    function showPopupId(groupId)
    {
        showPopup(Security.util.SecurityCache.getGlobalCache().getPrincipal(groupId), null);
    }

    function showPopup(group, groupsList)
    {
        var canEdit = (!group.Container && LABKEY.Security.currentUser.isRootAdmin) || (group.Container && LABKEY.Security.currentUser.isAdmin);

        var w = Ext4.create('Security.window.UserInfoPopup', {
            userId : group.UserId,
            cache  : Security.util.SecurityCache.getGlobalCache(),
            policy : Security.panel.PolicyEditor.getGlobalPolicy(),
            modal  : true,
            canEdit: canEdit,
            listeners : {
                close : function() {
                    if (groupsList)
                        groupsList.onDataChanged();
                }
            }
        });
        w.show();
    }
</script>