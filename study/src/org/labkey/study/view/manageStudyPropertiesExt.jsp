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

var canEdit = <%=canEdit?"true":"false"%>;
var editableFormPanel = canEdit;
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



var maskEl = null;

function mask()
{
    maskEl = Ext.getBody();
    maskEl.mask();
}

function unmask()
{
    if (maskEl)
        maskEl.unmask();
    maskEl = null;
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
    // if you want to stay on page, you need to refresh anyway to udpate attachments
    var msgbox = Ext.Msg.show({
       title:'Status',
       msg: '<span class="labkey-message">Changes saved</span>',
       buttons: false
    });
    var el = msgbox.getDialog().el;
    el.pause(1).fadeOut({callback:cancelButtonHandler});
}


function onSaveFailure(error)
{
    unmask();
    Ext.get("formSuccess").update("");
    Ext.get("formError").update(error.exception);
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
        mask();
    }
    else
    {
        Ext.MessageBox.alert("Error Saving", "There are errors in the form.");
    }
}


function editButtonHandler()
{
    editableFormPanel = true;
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


var renderTypes = {<%
String comma = "";
for (WikiRendererType type : getRendererTypes())
{
    %><%=comma%><%=q(type.name())%>:<%=q(type.getDisplayName())%><%
    comma = ",";
}%>};

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
        buttons.push({text:"Edit", handler: editButtonHandler});
        buttons.push({text:"Done", handler: doneButtonHandler});
    }

    var renderTypeCombo = new Ext.form.ComboBox(
    {
        hiddenName : 'DescriptionRendererType',
        name : 'RendererTypeDisplayName',
        mode: 'local',
        triggerAction: 'all',
        valueField: 'renderType',
        displayField: 'displayText',
        store : new Ext.data.ArrayStore(
        {
            id : 0, fields:['renderType', 'displayText'],
            data : [
<%
                comma = "";
                for (WikiRendererType type : getRendererTypes())
                {
                    %><%=comma%>[<%=q(type.name())%>,<%=q(type.getDisplayName())%>]<%
                    comma = ",";
                }
%>
            ]
        })
    });

    // fields we've handled (whether or now we're showing them)
    var handledFields = {};
    var items = [];

    items.push({name:'Label', width:400});
    handledFields['Label'] = true;
    items.push({name:'Investigator'});
    handledFields['Investigator'] = true;
    items.push({name:'studyGrant'});
    handledFields['studyGrant'] = true;
    items.push({name:'Description', width:400});
    handledFields['Description'] = true;
    if (editableFormPanel)
        items.push(renderTypeCombo);
    handledFields[renderTypeCombo.hiddenName] = true;
    // the original form didn't include these, but we can decide later
    handledFields['StartDate'] = true;
    handledFields['Container'] = true;
    handledFields['TimepointType'] = true;

    // Now let's add all the other fields
    var cm = data.columnModel;
    var col, i;
    for (i=0 ; i<cm.length ; i++)
    {
        col = cm[i];
        console.log(col.dataIndex + " hidden=" + col.hidden);
        col = cm[i];
        if (col.hidden) continue;
        if (handledFields[col.dataIndex]) continue;
        items.push({name:col.dataIndex});
    }
    for (i=0 ; i<items.length ; i++)
    {
        items[i].disabled = !editableFormPanel;
        items[i].disabledClass = 'noop';    // TODO the defaultClass makes everything unreadable
    }

    studyPropertiesFormPanel = new LABKEY.ext.FormPanel(
    {
        selectRowsResults:data,
        padding : 10,
        defaults : { width:200, disabled : !editableFormPanel, disabledClass:'noop' },
        labelWidth:150,   <%-- don't wrap even on Large font theme --%>
        buttonAlign:'left',
        buttons: buttons,
        items:items
    });
    studyPropertiesFormPanel.render('formDiv');
    var renderType = data.rows[0][renderTypeCombo.hiddenName];
    if (renderTypes[renderType])
        renderTypeCombo.setValue(renderTypes[renderType]);
}


function onQuerySuccess(data) // e.g. callback from Query.selectRows
{
    renderFormPanel(data, editableFormPanel);
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

<span id=formSuccess class=labkey-message-strong></span><span id=formError class=labkey-error></span>&nbsp;</br>
<div id='formDiv'/>

</script>
