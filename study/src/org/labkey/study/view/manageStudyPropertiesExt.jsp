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
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    ViewContext context = getViewContext();
    boolean canEdit = context.getContainer().hasPermission(context.getUser(), AdminPermission.class);
    String cancelLink = context.getActionURL().getParameter("returnURL");
    if (cancelLink == null || cancelLink.length() == 0)
        cancelLink = new ActionURL(StudyController.ManageStudyAction.class, context.getContainer()).toString();
%>
<div class="extContainer" id="manageStudyPropertiesDiv"></div>
<script type="text/javascript">

var editable = false;   // render as details view initially
var studyPropertiesFormPanel = null;

function removeProtocolDocument(name, xid)
{
    if (Ext)
    {
        function remove()
        {
            var params =
            {
                name: name
            };

            Ext.Ajax.request(
            {
                url    : LABKEY.ActionURL.buildURL('study', 'removeProtocolDocument'),
                method : 'POST',
                success: function()
                {
                    var el = document.getElementById(xid);
                    if (el)
                    {
                        el.parentNode.removeChild(el);
                    }
                },
                failure: function()
                {
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


function showSuccessMessage(message, after)
{
    Ext.get("formError").update("");
    var el = Ext.get("formSuccess");
    el.update(message);
    el.pause(3).fadeOut({callback:function(){el.update("");}});
}


function onSaveSuccess()
{
    showSuccessMessage("Study properties saved successfully", null);
    editable = false;
    createPage();
}


function onSaveFailure(error)
{
    studyPropertiesFormPanel.el.unmask();
    Ext.get("formSuccess").update("");
    Ext.get("formError").udpate(error.exeption);
}


function submitButtonHandler()
{
    var form = studyPropertiesFormPanel.getForm();
    if (form.isValid())
    {
        var rows = studyPropertiesFormPanel.getFormValues();
        LABKEY.Query.updateRows(
        {
            schemaName:'study',
            queryName:'StudyProperties',
            rows:rows,
            success : onSaveSuccess,
            failure : onSaveFailure
        });
        studyPropertiesFormPanel.el.mask();
    }
    else
    {
        Ext.MessageBox.alert("Error Saving", "There are errors in the form.");
    }
}


function editButonHandler()
{
    editable = true;
    destroyFormPanel();
    createPage();
}


function cancelButtonHandler()
{
    window.location = <%=q(cancelLink)%>;
}


function doneButtonHandler()
{
    window.location = <%=q(cancelLink)%>;
}


function destroyFormPanel()
{
    if (studyPropertiesFormPanel)
    {
        studyPropertiesFormPanel.destroy();
        studyPropertiesFormPanel = null;
    }
}


function renderFormPanel(data, editable)
{
    destroyFormPanel();
    var buttons = [];

    if (editable)
    {
        buttons.push({text:"Submit", handler: submitButtonHandler});
        buttons.push({text: "Cancel", handler: cancelButtonHandler});
    }
    else if (<%=canEdit ? "true" : "false"%>)
    {
        buttons.push({text:"Edit", handler: editButonHandler});
        buttons.push({text:"Done", handler: doneButtonHandler});
    }

    var renderTypeCombo = new Ext.form.ComboBox(
    {
        mode:'local',
        store : new Ext.data.ArrayStore(
        {
            id : 0, fields:['renderType', 'displayText'],
            data : [
<%
                String comma = "";
                for (WikiRendererType type : getRendererTypes())
                {
                    String value = type.name();
                    String displayName = type.getDisplayName();
                    %><%=comma%>[<%=q(value)%>,<%=q(displayName)%>]<%
                    comma = ",";
                }
%>
            ]
        })
    });

    studyPropertiesFormPanel = new LABKEY.ext.FormPanel(
    {
        selectRowsResults:data,
        addAllFields:true,
        buttonAlign:'left',
        buttons: buttons,
        items:[renderTypeCombo]
    });
    studyPropertiesFormPanel.render('formDiv');
}


function onQuerySuccess(data) // e.g. callback from Query.selectRows
{
    renderFormPanel(data, editable);
}


function onQueryFailure(a)
{
    alert(a);
}


function createPage()
{
    LABKEY.Query.selectRows({
        schemaName: 'study',
        queryName: 'StudyProperties',
        columns: '*',
        success: onQuerySuccess,
        failure: onQueryFailure
    });
}

Ext.onReady(createPage);
</script>

<%=canEdit?"CAN EDIT":"READ-ONLY"%><br>
<span id=formSuccess class=labkey-message-strong></span><spaan id=formError class=labkey-error></spaan>&nbsp;</br>
<div id='formDiv'/>

</script>
