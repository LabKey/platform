<%@ page import="org.labkey.wiki.model.WikiEditModel" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%
    JspView<WikiEditModel> me = (JspView<WikiEditModel>) HttpView.currentView();
    WikiEditModel model = me.getModelBean();
    final String ID_PREFIX = "wiki-input-";
%>

<script type="text/javascript">
    LABKEY.requiresScript('tiny_mce/tiny_mce.js');
    LABKEY.requiresScript('tiny_mce/init_tiny_mce.js');
</script>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>

<script type="text/javascript">

    //TODO: delay init of tinyMCE if rendererType is not HTML
    InitTinyMCE();

    //page-level variables
    var _idPrefix = <%=PageFlowUtil.jsString(ID_PREFIX)%>;
    var _wikiProps = {
        entityId: <%=model.getEntityId()%>,
        rowId: <%=model.getRowId()%>,
        name: <%=model.getName()%>,
        title: <%=model.getTitle()%>,
        body: <%=model.getBody()%>,
        parent: <%=model.getParent()%>,
        rendererType: <%=model.getRendererType()%>,
        isDirty: <%=null==model.getWiki() ? "true" : "false"%>
    };
    var _editableProps = ['name', 'title', 'body', 'parent']
    var _attachments = [
        <%
        if(model.hasAttachments())
        {
            String sep = "";
            for(Attachment att : model.getWiki().getAttachments())
            {
                %>
                    <%=sep%>{name: <%=PageFlowUtil.jsString(att.getName())%>, iconUrl: <%=PageFlowUtil.jsString(me.getViewContext().getContextPath() + att.getFileIcon())%>}
                <%
                sep = ",";
            }
        }
        %>
    ];

    var _redirUrl = <%=model.getRedir()%>;
    var _finished = false;
    var _newAttachmentIndex = 0;

    function onReady()
    {
        updateControls(_wikiProps);
        updateExistingAttachments(_attachments);
        addNewAttachmentInput();
    }

    Ext.onReady(onReady);

    function onSave()
    {
        if(!isDirty())
        {
            setStatus("Saved.");
            onAfterSave();
            return;
        }

        var wikiDataNew = gatherProps();
        
        setStatus("Saving...");
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("wiki", "saveWiki"),
            method : 'POST',
            success: onSuccess,
            failure: onError,
            jsonData : wikiDataNew,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function onFinish()
    {
        _finished = true;
        onSave();
    }

    function onCancel()
    {
        if(_wikiProps.isDirty || _attachments.isDirty)
            Ext.Msg.confirm("Confirm Cancel", "Are you sure you want to cancel all your changes?", onCancelConfirmed);
        else
            window.location.href = _redirUrl;
    }

    function onCancelConfirmed(btn)
    {
        if(btn == 'yes')
            window.location.href = _redirUrl;
    }

    function onChangeName()
    {
        //if this is an existing page, warn the user about changing the name
        if(_wikiProps.entityId)
        {
            Ext.Msg.show({
                title: "Warning",
                msg: "Changing the name of this page will break any links to this page embedded in other pages. Are you sure you want to change the name?",
                buttons: Ext.MessageBox.YESNO,
                icon: Ext.MessageBox.WARNING,
                fn: onChangeNameConfirmed
            });
        }
    }

    function onChangeNameConfirmed(buttonId)
    {
        if(buttonId == "no")
            updateControl("name", _wikiProps.name);
        else
            setWikiDirty();
    }


    function onSuccess(response, options)
    {
        //parse the response JSON
        var respJson = Ext.util.JSON.decode(response.responseText);
        if(respJson.success)
        {
            //update the current wiki props
            if(respJson.wikiProps)
            {
                _wikiProps = respJson.wikiProps;
                updateControls(_wikiProps);
            }

            if(_attachments.isDirty)
            {
                setStatus("Saving file attachments...");
                Ext.Ajax.request({
                    params: {entityId: _wikiProps.entityId},
                    url: LABKEY.ActionURL.buildURL("wiki", "attachFiles"),
                    method : 'POST',
                    form: 'form-files',
                    isUpload: true,
                    success: onAttachmentSuccess,
                    failure: onAttachmentFailure
                });
            }
            else
            {
                //no attachments to save
                setStatus("Saved");
                onAfterSave();
            }
        }
        else
        {
            //report validaton errors
            if(respJson.errors)
            {
                var msg = "Unable to save changes due to the following validation errors:<ul>";
                for(var err in respJson.errors)
                    msg += "<li>" + respJson.errors[err] + "</li>";
                msg += "</ul>";
                setError(msg);
            }
            else
                setError("Unable to save changes for an unknown reason.");
        }
    }

    function onAttachmentSuccess(response, options)
    {
        //parse the response JSON
        var respJson = Ext.util.JSON.decode(response.responseText);
        if(respJson.attachments)
        {
            _attachments = respJson.attachments;
            updateExistingAttachments(_attachments);
            clearNewAttachments();
        }
        
        var status = "Saved."
        if(respJson.warnings)
        {
            status = "Your changes were saved but the following warnings were returned:<ul>";
            for(var warning in respJson.warnings)
                status += "<li>" + respJson.warnings[warning] + "</li>";

            status += "</ul>";
        }
        setStatus(status);

        onAfterSave();
    }

    function onAttachmentFailure(response, options)
    {
        //parse the response JSON
        var respJson = Ext.util.JSON.decode(response.responseText);
        if(respJson.exception)
            setError("Unable to save attachments: " + respJson.exception);
        else
            setError("Unable to save attachments: " + response.statusText);
    }

    function onError(response, options)
    {
        //parse the response JSON
        var respJson = Ext.util.JSON.decode(response.responseText);
        if(respJson.exception)
            setError("There was a problem while saving: " + respJson.exception);
        else
            setError("There was a problem while saving: " + response.statusText);
    }

    function onAfterSave()
    {
        if(_finished)
            window.location.href = _redirUrl;
    }

    function gatherProps()
    {
        //init the return obj with the read-only values
        var ret = cloneObj(_wikiProps);

        updateSourceFromVisual();

        //get editable props from controls
        for(var prop in _editableProps)
        {
            var input = Ext.get(_idPrefix + _editableProps[prop]);
            if(input)
                ret[_editableProps[prop]] = input.getValue();
        }

        return ret;
    }

    function cloneObj(source)
    {
        var ret = {};
        for(var prop in source)
            ret[prop] = source[prop];
        return ret;
    }

    function updateSourceFromVisual()
    {
        tinyMCE.triggerSave();
    }

    function updateVisaulFromSource()
    {
        tinyMCE.updateContent();
    }

    function updateControls(wikiProps)
    {
        for(var prop in _editableProps)
            updateControl(_editableProps[prop], wikiProps[_editableProps[prop]]);
    }

    function updateControl(propName, propValue)
    {
        var elem = Ext.get(_idPrefix + propName)
        if(elem)
            elem.dom.value = propValue;
    }

    function setError(msg)
    {
        var elem = Ext.get("status");
        elem.update(msg);
        elem.dom.className = "status-error";
        elem.setVisible(true);
    }

    function setStatus(msg)
    {
        var elem = Ext.get("status");
        elem.update(msg);
        elem.dom.className = "status-info";
        elem.setVisible(true);
        setTimeout("clearStatus();", 5000);
    }

    function clearStatus()
    {
        var elem = Ext.get("status");
        elem.update("&nbsp;")
        elem.setVisible(false);
    }

    function switchToSource()
    {
        if(tinyMCE.getEditorId("body"))
        {
            tinyMCE.removeMCEControl(tinyMCE.getEditorId("body"));
            document.getElementById("wiki-tab-visual").className = "tab-inactive";
            document.getElementById("wiki-tab-source").className = "tab-active";
        }
    }

    function switchToVisual()
    {
        if(!tinyMCE.getEditorId("body"))
        {
            tinyMCE.addMCEControl(document.getElementById(_idPrefix + "body"), "body");
            document.getElementById("wiki-tab-visual").className = "tab-active";
            document.getElementById("wiki-tab-source").className = "tab-inactive";
        }
    }

    function updateExistingAttachments(attachments)
    {
        if(!attachments)
            return;

        var table = document.getElementById("wiki-existing-attachments");
        if(!table)
            return;

        //clear the table
        table.innerHTML = "";

        if(null == attachments || attachments.length == 0)
        {
            var row = table.insertRow(0);
            var cell = row.insertCell(0);
            cell.innerHTML = "[none]";
        }
        else
        {
            //add a row for each attachment
            for(var idx = 0; idx < attachments.length; ++idx)
            {
                var newRow = table.insertRow(idx);
                newRow.id = "wiki-ea-" + idx;
                var cell = newRow.insertCell(0);
                cell.id = "wiki-ea-icon-" + idx;
                cell.innerHTML = "<img src='" + attachments[idx].iconUrl + "' id='wiki-ea-icon-img-" + idx + "'/>";

                cell = newRow.insertCell(1);
                cell.id = "wiki-ea-name-" + idx;
                //append name as a text node so that it gets HTML encoded
                cell.appendChild(document.createTextNode(attachments[idx].name));

                cell = newRow.insertCell(2);
                cell.id = "wiki-ea-del-" + idx;
                cell.innerHTML = "[<a href='javascript:{}' onclick='onDeleteAttachment(" + idx + ")'>delete</a>]";
            }
        }
    }

    function onDeleteAttachment(index)
    {
        var row = getExistingAttachmentRow(index);

        getExistingAttachmentIconImg(index).src = "<%=me.getViewContext().getContextPath()%>/_icons/_deleted.gif";
        row.cells[1].style.textDecoration = "line-through";
        row.cells[2].innerHTML = "[<a href='javascript:{}' onclick='onUndeleteAttachment(" + index + ")'>undelete</a>]"
                + "<input type='hidden' name='toDelete' value='" + _attachments[index].name + "'/>";

        //add a prop so we know we need to save the attachments
        _attachments.isDirty = true;
    }

    function onUndeleteAttachment(index)
    {
        var row = getExistingAttachmentRow(index);

        getExistingAttachmentIconImg(index).src = _attachments[index].iconUrl;
        row.cells[1].style.textDecoration = "";
        row.cells[2].innerHTML = "[<a href='javascript:{}' onclick='onDeleteAttachment(" + index + ")'>delete</a>]";
    }

    function getFilesForm()
    {
        var form = document.getElementById("form-files");
        if(!form)
            window.alert("Could not access the files form!");
        return form;
    }

    function getExistingAttachmentRow(index)
    {
        var row = document.getElementById("wiki-ea-" + index);
        if(!row)
            window.alert("Could not access the existing attachment table row!");
        return row;
    }

    function getExistingAttachmentIconImg(index)
    {
        var img = document.getElementById("wiki-ea-icon-img-" + index);
        if(!img)
            window.alert("Could not get img element for existing attachment!");
        return img;
    }

    function clearNewAttachments()
    {
        var table = getNewAttachmentsTable();
        table.innerHTML = "";
        addNewAttachmentInput();
    }

    function addNewAttachmentInput()
    {
        var table = getNewAttachmentsTable();
        var row = table.insertRow(-1);
        row.id = "wiki-na-" + _newAttachmentIndex;

        var cell = row.insertCell(0);
        cell.innerHTML = "<input type='file' name='formFiles[" + _newAttachmentIndex + "]' size='60' onChange='onAddAttachment(this," + _newAttachmentIndex + ")'>";

        cell = row.insertCell(1);
        cell.id = "wiki-na-name-" + _newAttachmentIndex;
        cell.innerHTML = "&nbsp;";

        ++_newAttachmentIndex;
    }

    function onAddAttachment(fileInput, index)
    {
        //update the name column
        var cell = document.getElementById("wiki-na-name-" + index);
        if(cell)
        {
            cell.setAttribute("nobreak", "1");
            cell.innerHTML = "[<a href='javascript:{}' onclick='onRemoveNewAttachment(" + index + ")'>remove</a>]&nbsp;"
                    + getFileName(fileInput.value);
        }

        //mark the attachments as dirty
        _attachments.isDirty = true;

        //add another new attachment input
        addNewAttachmentInput();
    }

    function getFileName(pathname)
    {
        if (pathname.indexOf('/') > -1)
             return pathname.substring(pathname.lastIndexOf('/')+1,pathname.length);
        else
             return pathname.substring(pathname.lastIndexOf('\\')+1,pathname.length);
    }

    function onRemoveNewAttachment(index)
    {
        //delete the entire table row
        var row = document.getElementById("wiki-na-" + index);
        if(row)
            getNewAttachmentsTable().deleteRow(row.rowIndex);
    }

    function getNewAttachmentsTable()
    {
        var table = document.getElementById("wiki-new-attachments");
        if(!table)
            window.alert("Could not get the new attachments table!");
        return table;
    }

    function setWikiDirty()
    {
        _wikiProps.isDirty = true;
    }

    function isDirty()
    {
        return _wikiProps.isDirty || _attachments.isDirty ||
        (tinyMCE.getEditorId("body") && tinyMCE.getInstanceById(tinyMCE.getEditorId("body")).isDirty());
    }

</script>

<style type="text/css">
    .status-info
    {
        width: 100%;
        text-align: center;
        background-color: #FFDF8C;
        border: 1px solid #FFAD6A;
        padding: 2px;
        font-weight: bold;
    }
    .status-error
    {
        width: 100%;
        text-align: center;
        background-color: #E42217;
        border: 1px solid #C11B17;
        color: #FFFFFF;
        font-weight: bold;
    }
    table.form-layout
    {
        width: 100%;
    }
    .stretch-input
    {
        width: 100%;
    }
    .button-bar
    {
        padding: 4px;
        vertical-align: middle;
    }
    .tab-container
    {
        width: 100%;
        border-spacing: 0;
    }
    .tab-active
    {
        border-left: 1px solid #89A1B4;
        border-right: 1px solid #89A1B4;
        border-top: 1px solid #89A1B4;
        font-weight: bold;
        padding: 4px 8px 4px 8px;
        border-bottom: none;
        background-color: #E1ECFC;
    }
    .tab-inactive
    {
        border: 1px solid #89A1B4;
        font-weight: normal;
        background-color: #D1DCEC;
        padding: 4px 8px 4px 8px;
    }
    .tab-blank
    {
        border-bottom: 1px solid #89A1B4;
        padding: 4px 8px 4px 8px;
    }
    .tab-content
    {
        border-left: 1px solid #89A1B4;
        border-right: 1px solid #89A1B4;
        border-bottom: 1px solid #89A1B4;
    }
    .ms-searchform
    {
        width: 1%;
    }
    .field-content
    {
        width: 99%;
    }
</style>

<div id="status" class="status-info" style="visibility: hidden;">(status)</div>

<div class="button-bar">
    <a href="javascript:{}" onclick="onSave()"><%=PageFlowUtil.buttonImg("Save")%></a>
    <a href="javascript:{}" onclick="onFinish()"><%=PageFlowUtil.buttonImg("Save and Finish")%></a>
    <a href="javascript:{}" onclick="onCancel()"><%=PageFlowUtil.buttonImg("Cancel")%></a>
</div>

<table class="form-layout">
    <tr>
        <td class="ms-searchform">Name</td>
        <td class="field-content">
            <input type="text" name="name" id="<%=ID_PREFIX%>name" class="stretch-input" onchange="onChangeName()"/>
        </td>
    </tr>
    <tr>
        <td class="ms-searchform">Title</td>
        <td class="field-content">
            <input type="text" name="name" id="<%=ID_PREFIX%>title" class="stretch-input" onchange="setWikiDirty()"/>
        </td>
    </tr>
    <tr>
        <td class="ms-searchform">Parent</td>
        <td class="field-content">
            <select name="parent" id="<%=ID_PREFIX%>parent" class="stretch-input" onchange="setWikiDirty()">
                <option <%= model.getParent() == -1 ? "selected='1'" : "" %> value="-1">[none]</option>
                <%
                    for (Wiki possibleParent : model.getPossibleParents())
                        {
                        String indent = "";
                        int depth = possibleParent.getDepth();
                        String parentTitle = possibleParent.latestVersion().getTitle();
                        while (depth-- > 0)
                          indent = indent + "&nbsp;&nbsp;";
                        %><option <%= possibleParent.getRowId() == model.getParent() ? "selected" : "" %> value="<%= possibleParent.getRowId() %>"><%= indent %><%= parentTitle %> (<%= possibleParent.getName() %>)</option><%
                        }
                %>
            </select>
        </td>
    </tr>
    <tr>
        <td class="ms-searchform">Body</td>
        <td class="field-content">
            <table class="tab-container" cellspacing="0">
                <tr>
                    <td class="tab-blank">&nbsp;</td>
                    <td id="wiki-tab-visual" class="tab-active"><a href="javascript:{}" onclick="switchToVisual()">Visual</a></td>
                    <td id="wiki-tab-source" class="tab-inactive"><a href="javascript:{}" onclick="switchToSource()">Source</a></td>
                    <td class="tab-blank" style="width:100%">&nbsp;</td>
                </tr>
                <tr>
                    <td colspan="4" class="tab-content">
                        <textarea rows="30" cols="80" class="stretch-input" id="<%=ID_PREFIX%>body"
                                  name="body" onchange="setWikiDirty()"></textarea>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="ms-searchform">Files</td>
        <td class="field-content">
            <form action="attachFiles.post" method="POST" enctype="multipart/form-data" id="form-files">
                <table width="100%">
                    <tr>
                        <td class="tab-blank"><img src="<%=me.getViewContext().getContextPath()%>/_images/paperclip.gif" alt="Clip"/>Existing Attachments</td>
                    </tr>
                </table>
                <table id="wiki-existing-attachments">
                </table>
                <table width="100%">
                    <tr>
                        <td class="tab-blank"><img src="<%=me.getViewContext().getContextPath()%>/_images/paperclip.gif" alt="Clip"/>Add Attachments</td>
                    </tr>
                </table>
                <table id="wiki-new-attachments">
                </table>
            </form>
        </td>
    </tr>
</table>

<div class="button-bar">
    <a href="javascript:{}" onclick="onSave()"><%=PageFlowUtil.buttonImg("Save")%></a>
    <a href="javascript:{}" onclick="onFinish()"><%=PageFlowUtil.buttonImg("Save and Finish")%></a>
    <a href="javascript:{}" onclick="onCancel()"><%=PageFlowUtil.buttonImg("Cancel")%></a>
</div>

