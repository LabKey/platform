<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.query.AbstractQueryImportAction" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    ViewContext context = HttpView.currentContext();
    AbstractQueryImportAction.ImportViewBean bean = (AbstractQueryImportAction.ImportViewBean)HttpView.currentModel();
    final String copyPasteDivId = "copypasteDiv" + getRequestScopedUID();
    final String uploadFileDivId = "uploadFileDiv" + getRequestScopedUID();
    String tsvId = "tsv" + getRequestScopedUID();
    String errorDivId = "errorDiv" + getRequestScopedUID();
%>

<div id="<%=errorDivId%>" class="labkey-error">
<labkey:errors></labkey:errors>&nbsp;
</div>
<% if (!StringUtils.isBlank(bean.urlExcelTemplate)) {
    %><%=generateButton("Download Template", bean.urlExcelTemplate)%><br>&nbsp;<br><%
}%>
<table class="labkey-wp" style="background-color:#ffffff;">
<tr class="labkey-wp-header" style="min-width:600px;">
    <th class="labkey-wp-title-left\" style="padding:5px;"><span class="labkey-header" style="font-weight:normal;">Copy/paste text (tab delimited)</span></th>
    <th class="labkey-wp-title-right"><%=PageFlowUtil.generateButton("-","#",null,"id='"+copyPasteDivId+"Expando'")%></th>
</tr>
<tr><td colspan=2 style="display:inline;"><div id="<%=copyPasteDivId%>"></div></td></tr>
</table>
<br>
<table class="labkey-bordered labkey-wp" style="background-color:#ffffff;">
<tr class="labkey-wp-header" style="min-width:600px;">
    <th class="labkey-wp-title-left\" style="padding:5px;"><span class="labkey-header" style="font-weight:normal;">Upload file (.xls, .csv, .txt)</span></th>
    <th class="labkey-wp-title-right"><%=PageFlowUtil.generateButton("+","#",null,"id='"+uploadFileDivId+"Expando'")%></tr>
<tr><td colspan=2 style="display:none;"><div id="<%=uploadFileDivId%>"></div></td></tr>
</table>
<script> (function(){
    var $json = Ext.util.JSON.encode;
    var $html = Ext.util.Format.htmlEncode;

    var importTsvDiv = Ext.get(<%=q(copyPasteDivId)%>);
    var uploadFileDiv = Ext.get(<%=q(uploadFileDivId)%>);
    var errorDiv = Ext.get(<%=q(errorDivId)%>);
    var tsvTextarea ;
    var endpoint = <%=q(bean.urlEndpoint)%>;
    var cancelUrl = <%=q(bean.urlCancel)%>;
    var returnUrl = <%=q(bean.urlReturn)%>;
    var importTsvForm;
    var uploadFileForm;

    // attach listeners to the buttons
    var importTsvExpando = Ext.get(<%=q(copyPasteDivId+"Expando")%>);
    var uploadTsvExpando = Ext.get(<%=q(uploadFileDivId+"Expando")%>);

    importTsvExpando.parent('tr').on('click',function(){toggleExpanded(importTsvExpando,importTsvDiv,uploadTsvExpando,uploadFileDiv);});
    uploadTsvExpando.parent('tr').on('click',function(){toggleExpanded(uploadTsvExpando,uploadFileDiv,importTsvExpando,importTsvDiv);});

    function toggleExpanded(toggleButton, toggleDiv, collapseButton, collapseDiv)
    {
        var collapsed = toggleButton.dom.innerText == "+";
        toggleButton.dom.innerText = collapsed ? "-" : "+";
        toggleDiv.parent().setStyle("display",collapsed?"inline":"none");

        collapseButton.dom.innerText = "+";
        collapseDiv.parent().setStyle("display","none");
    }


    function expandFileUpload()
    {
        var td = importTsvDiv.getParent();
        td.setStyle("display","none");
        td = uploadFileDiv.getParent();
        td.setStyle("display","inline");
    }


    function cancelForm()
    {
        window.location = cancelUrl;
    }

    function submitFormTsv()
    {
        submitForm(importTsvForm);
    }

    function submitFormUpload()
    {
        submitForm(uploadFileForm);
    }
    
    function submitForm(form)
    {
        if (!form)
            return;

        Ext.getBody().mask();
        errorDiv.update("&nbsp;");

        form.getForm().submit(
        {
            clientValidation : false,
            success: function(form, action)
            {
                Ext.getBody().unmask();
                var msg = null;
                if ("msg" in action.result)
                    msg = action.result.msg;
                else if ("rowCount" in action.result)
                {
                    var rowCount = action.result.rowCount;
                    msg = rowCount + " row" + (rowCount!=1?"s":"") + " inserted.";
                }
                if (msg)
                    Ext.Msg.alert("Success", msg, function(){window.location = returnUrl;});
                else
                    window.location = returnUrl;
            },
            failure: function(form, action)
            {
                Ext.getBody().unmask();
                switch (action.failureType)
                {
                    case Ext.form.Action.CLIENT_INVALID:
                        Ext.Msg.alert('Failure', 'Form fields may not be submitted with invalid values');
                        break;
                    case Ext.form.Action.CONNECT_FAILURE:
                        if (action.result && (action.result.errors || action.result.exception))
                            serverInvalid(action.result);
                        else
                            Ext.Msg.alert('Failure', 'Ajax communication failed');
                        break;
                    case Ext.form.Action.SERVER_INVALID:
                        serverInvalid(action.result);
                        break;
                    break;
                }
            }
        });
    }


    // extra processing for server errors
    function serverInvalid(result)
    {
        if (result.exception)
            console.log(result.exception);
        if (result.stackTrace)
            console.log(result.stackTrace);

        var formMsg = getGlobalErrorHtml(result);
        errorDiv.update(formMsg);
    }


    /* our error reporting is complicated for multi-row import,
     * try to consolidate into one useful display string
     */
    function getGlobalErrorHtml(result)
    {
        var errors = _getGlobalErrors([], result);
        for (var i=0 ; i<errors.length ; i++)
            errors[i] = $html(errors[i]);
        if (errors.length > 20)
        {
            var total = errors.length;
            errors = errors.slice(0,19);
            errors.push("... total of " + total + " errors");
        }

        var errorHtml = errors.join("<br>");
        return errorHtml;
    }


    function _getGlobalErrors(collection, errors)
    {
        var count = collection.length;

        if (errors["msg"] || errors["_form"])
        {
            collection.push(errors["exception"] || errors["msg"] || errors["_form"]);
        }
        
        if (Ext.isArray(errors))
        {
            for (var i=0 ; i<errors.length ; i++)
                _getGlobalErrors(collection, errors[i]);
        }
        else if (errors.errors)
        {
            _getGlobalErrors(collection, errors.errors);
        }

        if (collection.length == count)
        {
            // don't want to double up messages, so ignore errors.exception unless there are no other messages
            if (errors["exception"])
            {
                collection.push(errors.exception);
            }
            else
            {
                collection.push($json(errors));
            }
        }

        return collection;
    }


    function onReady()
    {
        importTsvForm = new Ext.form.FormPanel({
            fileUpload : false,
            labelWidth: 75, // label settings here cascade unless overridden
            url:endpoint,
            title: false, // 'Import text',
            border: false,
            bodyStyle:'padding:5px',
            width: 600,
            defaultType: 'textfield',

            items: [
                {
                    id: <%=q(tsvId)%>,
                    xtype: 'textarea',
                    hideLabel: true, //fieldLabel: 'Text',
                    name: 'text',
                    width:580,
                    height:500
                }
            ],
            buttons: [{
                text: 'Submit', handler:submitFormTsv
            },{
                text: 'Cancel', handler:cancelForm
            }]
        });
        importTsvForm.render(importTsvDiv);
        tsvTextarea = Ext.get(<%=q(tsvId)%>);
        Ext.EventManager.on(tsvTextarea, 'keydown', handleTabsInTextArea);



        uploadFileForm = new Ext.form.FormPanel({
            fileUpload : true,
            labelWidth: 75, // label settings here cascade unless overridden
            url:endpoint,
            title: false, // 'Import text',
            border: false,
            bodyStyle:'padding:5px',
            width: 600,
            defaultType: 'textfield',

            items: [
                {
                    xtype: 'textfield',
                    inputType: 'file',
                    name : 'file',
                    width:200
                }
            ],

            buttons: [{
                text: 'Submit', handler:submitFormUpload
            },{
                text: 'Cancel', handler:cancelForm
            }]
        });
        uploadFileForm.render(uploadFileDiv);
    }

    Ext.onReady(onReady);

})(); </script>
