<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%
    JspView me = (JspView) HttpView.currentView();
    Container container = me.getViewContext().getContainer();
%>

<style type="text/css">
    .wb-empty-description
    {
        font-style: italic;
    }
    .wb-name
    {
        font-size: 12pt;
        font-weight: bold;
    }
    .wb-title
    {
        font-size: 12pt;
        font-weight: bold;

    }
    .wb-name-title-container
    {
        padding-bottom: 5px;
    }
</style>

<div class="wb-name-title-container"><span id="wb=name" class="wb-name"><%=PageFlowUtil.filter(container.getName() + ":")%></span>
    <span id="wb-title" class="wb-title"><%=PageFlowUtil.filter(container.getTitle())%></span></div>

<div id="wbd-description" <%=null == container.getDescription() ? "class=\"wb-empty-description\"" : ""%>
><%=(null != container.getDescription() ? PageFlowUtil.filter(container.getDescription()) : "No description provided.")%></div>

<script type="text/javascript">
    var _editor, _field;
    Ext.onReady(function(){
        if (!LABKEY.Security.currentUser.canUpdate)
            return;

        _field = new Ext.form.TextField({
            selectOnFocus: true,
            cls: 'extContainer',
            grow: true
        });

        _editor = new Ext.Editor(_field, {
            alignment: 'lt-lt',
            autoSize: 'width',
            listeners: {
                complete: {
                    fn: onEditDescription
                }
            }
        });

        var descrElem = Ext.get("wbd-description");

        descrElem.on("click", function(){
            if (descrElem.hasClass("wb-empty-description"))
            {
                descrElem.update("");
                descrElem.removeClass("wb-empty-description");
            }
            _editor.startEdit(descrElem);
        });
    });

    function onEditDescription() {
        var descrElem = Ext.get("wbd-description");
        var oldDescr = descrElem.dom.innerHTML;
        descrElem.update("updating...");
        
        var params = {description: _field.getValue()};
        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("core", "updateDescription"),
            method: "POST",
            success: function() {
                Ext.get("wbd-description").update(_field.getValue());
            },
            failure: function() {
                Ext.Msg.alert("Error", "Error updating description!");
                descrElem.update(oldDescr);
            },
            jsonData: params,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

</script>