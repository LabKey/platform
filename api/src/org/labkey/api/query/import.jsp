<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.query.AbstractQueryImportAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    ViewContext context = HttpView.currentContext();
    AbstractQueryImportAction.ImportViewBean bean = (AbstractQueryImportAction.ImportViewBean)HttpView.currentModel();
    String importDivId = "importDiv" + getRequestScopedUID();
    String tsvId = "tsv" + getRequestScopedUID();
    String errorDivId = "errorDiv" + getRequestScopedUID();
%>

<div id="<%=errorDivId%>" class="labkey-error">
<labkey:errors></labkey:errors>&nbsp;
</div>
<div id="<%=importDivId%>">
</div>
<script> (function(){
    var $json = Ext.util.JSON.encode;
    var $html = Ext.util.Format.htmlEncode;

    var importDiv = Ext.get(<%=q(importDivId)%>);
    var errorDiv = Ext.get(<%=q(errorDivId)%>);
    var tsvTextarea ;
    var endpoint = <%=q(bean.urlEndpoint)%>;
    var cancelUrl = <%=q(bean.urlCancel)%>;
    var returnUrl = <%=q(bean.urlReturn)%>;
    var importForm;

    function cancelForm()
    {
        window.location = cancelUrl;
    }

    function submitFormTsv()
    {
        if (!importForm)
            return;

        Ext.getBody().mask();
        errorDiv.update("&nbsp;");

        importForm.getForm().submit(
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
                            serverInvalidTsv(action.result);
                        else
                            Ext.Msg.alert('Failure', 'Ajax communication failed');
                        break;
                    case Ext.form.Action.SERVER_INVALID:
                        serverInvalidTsv(action.result);
                        break;
                    break;
                }
            }
        });
    }


    // extra processing for server errors
    function serverInvalidTsv(result)
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
        var errorHtml = errors.join("<br>");
        return errorHtml;
    }


    function _getGlobalErrors(collection, errors)
    {
        if (errors["exception"] || errors["msg"] || errors["_form"])
        {
            collection.push(errors["exception"] || errors["msg"] || errors["_form"]);
        }
        else if (Ext.isArray(errors))
        {
            for (var i=0 ; i<errors.length ; i++)
                _getGlobalErrors(collection, errors[i]);
        }
        else if (errors.errors)
        {
            _getGlobalErrors(collection, errors.errors);
        }
        else
        {
            collection.push($json(errors));
        }

        return collection;
    }


    function onReady()
    {
        importForm = new LABKEY.ext.FormPanel({
            fileUpload : true,
            errorEl : 'errorDiv',
            labelWidth: 75, // label settings here cascade unless overridden
            url:endpoint,
            title: 'Import text',
            bodyStyle:'padding:5px 5px 0',
            width: 600,
            defaultType: 'textfield',

            items: [
                {
                    xtype: 'textfield',
                    inputType: 'file',
                    name : 'file'
                },
                {
                    id: <%=q(tsvId)%>,
                    xtype: 'textarea',
                    fieldLabel: 'Text',
                    name: 'text',
                    width:500,
                    height:500
                }
            ],

            buttons: [{
                text: 'Submit', handler:submitFormTsv
            },{
                text: 'Cancel', handler:cancelForm
            }]
        });
        importForm.render(importDiv);
        tsvTextarea = Ext.get(<%=q(tsvId)%>);
        Ext.EventManager.on(tsvTextarea, 'keydown', handleTabsInTextArea);
    }

    Ext.onReady(onReady);

})(); </script>
