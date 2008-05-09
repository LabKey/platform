<%@ page import="org.labkey.wiki.model.WikiEditModel" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%
    JspView<WikiEditModel> me = (JspView<WikiEditModel>) HttpView.currentView();
    WikiEditModel model = me.getModelBean();
    final String ID_PREFIX = "wiki-input-";
    String sep;
%>

<script type="text/javascript">
    LABKEY.requiresScript('tiny_mce/tiny_mce.js');
</script>
<script type="text/javascript">
    LABKEY.requiresClientAPI();
</script>

<script type="text/javascript">
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
        pageId: <%=model.getPageId()%>,
        index: <%=model.getIndex()%>,
        isDirty: <%=null==model.getWiki() ? "true" : "false"%>
    };
    var _editableProps = ['name', 'title', 'body', 'parent']
    var _attachments = [
        <%
        if(model.hasAttachments())
        {
            sep = "";
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
    var _formats = {
        <%
            sep = "";
            for(WikiRendererType format : WikiRendererType.values())
            {
        %>
            <%=sep%>
            <%=format.name()%>: <%=PageFlowUtil.jsString(format.getDisplayName())%>
        <%
                sep = ",";
            }
        %>
    };
    var _redirUrl = <%=model.getRedir()%>;
    var _finished = false;
    var _newAttachmentIndex = 0;
    var _doingSave = false;
    var _editor = "source";
    var _tocTree;

    //you must init the tinyMCE before the page finishes loading
    //if you don't, you'll get a blank page an an error
    //seems to be a limitation of the tinyMCE.
    tinyMCE.init({
        mode : "none",
        theme : "advanced",
        entity_encoding : "named",
        entities : "160,nbsp,60,lt,62,gt,38,amp",
        relative_urls : "true",
        document_base_url : "",
        plugins : "table,advhr,advlink,searchreplace,contextmenu,fullscreen,nonbreaking,cleanup",
        theme_advanced_buttons1_add : "fontselect,fontsizeselect",
        theme_advanced_buttons2_add : "separator,forecolor,backcolor",
        theme_advanced_buttons2_add_before: "cut,copy,paste,separator,search,replace,separator",
        theme_advanced_buttons3_add_before : "tablecontrols,separator",
        theme_advanced_buttons3_add : "advhr,nonbreaking,separator,fullscreen",
        theme_advanced_disable : "image,code,hr,removeformat,visualaid",
        theme_advanced_layout_manager: "SimpleLayout",
        width: "100%",
        nonbreaking_force_tab : true,
        fullscreen_new_window : false,
        fullscreen_settings : {
        theme_advanced_path_location : "top"},
        theme_advanced_toolbar_location : "top",
        theme_advanced_toolbar_align : "left",
        theme_advanced_path_location : "bottom",
        apply_source_formatting : true,
        extended_valid_elements : "a[name|href|target|title|onclick],img[class|src|border=0|alt|title|hspace|vspace|width|height|align|onmouseover|onmouseout|name],hr[class|width|size|noshade],font[face|size|color|style],span[class|align|style]",
        external_link_list_url : "example_data/example_link_list.js",
        external_image_list_url : "example_data/example_image_list.js",
        theme_advanced_statusbar_location: "bottom",
        fix_list_elements : true
        });

    //the onReady function will execute after all elements
    //have been loaded and parsed into the DOM
    Ext.onReady(function(){
        updateControls(_wikiProps);
        updateExistingAttachments(_attachments);
        addNewAttachmentInput();
        enableDeleteButton(null != _wikiProps.entityId);

        //if the format is HTML
        //switch to visual or source if there are problemmatic elements
        if(_wikiProps.rendererType == "HTML")
        {
            if(textContainsNonVisualElements(_wikiProps.body))
            {
                switchToSource();
                setStatus("Switching to source editing mode because your page has elements that are not supported by the visual editor.", true);
            }
            else if(<%=model.useVisualEditor()%>)
                switchToVisual();
            else
                switchToSource();
        }

        showEditingHelp(_wikiProps.rendererType);
        loadToc();
    });

    function loadToc()
    {
        //kick off a request to get the wiki toc
        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL('wiki', 'getWikiToc'),
            success: onTocSuccess,
            failure: onTocError
        });
    }

    function onTocSuccess(response)
    {
        var json = Ext.util.JSON.decode(response.responseText);
        if(json.pages)
            createTocTree(json.pages);
    }

    function onTocError(response)
    {
        var json = Ext.util.JSON.decode(response.responseText);
        if(!json.exception)
            json.exception = response.statusText;
        
        setError()("Unable to load the wiki table of contents for this folder: " + json.exception);
    }

    function createTocTree(pages)
    {
        var root = new Ext.tree.TreeNode({
            expanded: true,
            text: <%=PageFlowUtil.jsString(me.getViewContext().getContainer().getName())%>,
            id: 'root'
        });

        if(!_tocTree)
        {
            loadChildren(root, pages);

            _tocTree = new Ext.tree.TreePanel({
                renderTo: 'wiki-toc-tree',
                width: 300,
                autoScroll: true,
                root: root
            });

            _tocTree.render();
        }
        else
        {
            clearTocBranch(_tocTree.root);
            loadChildren(_tocTree.root, pages);
            _tocTree.root.expand(false, false);
        }
    }

    function clearTocBranch(node)
    {
        while(node.firstChild)
            node.removeChild(node.firstChild);
    }

    function loadChildren(node, pages)
    {
        for(var idx in pages)
        {
            if(typeof(pages[idx]) != "object")
                continue;

            var page = pages[idx];
            var childNode = new Ext.tree.TreeNode({
                id: page.name,
                text: page.title + " (" + page.name + ")",
                leaf: (null == page.children),
                singleClickExpand: true,
                icon: LABKEY.contextPath + "/_images/page.png"
            });

            if(page.children)
                loadChildren(childNode, page.children);

            node.appendChild(childNode);
        }
    }

    function onSave()
    {
        if(_doingSave)
            return;

        _doingSave = true;
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

    function onDeletePage()
    {
        window.location.href = LABKEY.ActionURL.buildURL("wiki", "delete") + "?name=" + _wikiProps.name + "&rowId=" + _wikiProps.rowId;
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


    function onSuccess(response)
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
                onSaveComplete();
            }
        }
        else
        {
            _doingSave = false;
            _finished = false;
            //report validaton errors
            if(respJson.errors)
            {
                var msg = "Unable to save changes due to the following validation errors:<span style='text-align:left'><ul>";
                for(var err in respJson.errors)
                    msg += "<li>" + respJson.errors[err] + "</li>";
                msg += "</ul></span>";
                setError(msg);
            }
            else
            {
                setError("Unable to save changes for an unknown reason.");
            }
        }
    }

    function onAttachmentSuccess(response)
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
            status = "Your changes were saved but the following warnings were returned:<span style='text-align:left'><ul>";
            for(var warning in respJson.warnings)
                status += "<li>" + respJson.warnings[warning] + "</li>";

            status += "</ul></span>";
        }
        onSaveComplete(status);
    }

    function onAttachmentFailure(response)
    {
        _doingSave = false;
        _finished = false;
        //parse the response JSON
        var respJson = Ext.util.JSON.decode(response.responseText);
        if(respJson.exception)
            setError("Unable to save attachments: " + respJson.exception);
        else
            setError("Unable to save attachments: " + response.statusText);
    }

    function onError(response)
    {
        _doingSave = false;
        //parse the response JSON
        var respJson = Ext.util.JSON.decode(response.responseText);
        if(respJson.exception)
            setError("There was a problem while saving: " + respJson.exception);
        else
            setError("There was a problem while saving: " + response.statusText);
    }

    function onSaveComplete(statusMessage)
    {
        setClean();
        _doingSave = false;
        if(!statusMessage)
            statusMessage = "Saved.";

        setStatus(statusMessage, true);
        enableDeleteButton(true);

        if(_finished)
        {
            if(!_redirUrl || _redirUrl.length == 0)
                _redirUrl = LABKEY.ActionURL.buildURL("wiki", "page") + "?name=" + _wikiProps.name;

            window.location.href = _redirUrl;
        }
        else
            loadToc();
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

    function setStatus(msg, autoClear)
    {
        var elem = Ext.get("status");
        elem.update(msg);
        elem.dom.className = "status-info";
        elem.setVisible(true);
        if(autoClear)
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
        setTabStripVisible(true);
        document.getElementById("wiki-tab-visual").className = "tab-inactive";
        document.getElementById("wiki-tab-source").className = "tab-active";
        if(tinyMCE.getEditorId("body"))
            tinyMCE.removeMCEControl(tinyMCE.getEditorId("body"));
        _editor = "source";
        showEditingHelp(_wikiProps.rendererType);
        saveEditorPreference(_editor);
    }

    function switchToVisual(confirmOverride)
    {
        //check for elements that get mangled by the visual editor
        if(!confirmOverride && textContainsNonVisualElements(Ext.get("<%=ID_PREFIX%>body").getValue()))
        {
            Ext.Msg.show({
                title: "Warning",
                msg: "Your page contains elements that are not supported by the visual editor and will thus be removed. Are you sure you want to switch to the visual editor?",
                buttons: Ext.Msg.YESNO,
                animEl: "wiki-tab-visual",
                icon: Ext.Msg.QUESTION,
                fn: function(btn){
                    if(btn=="yes")
                        switchToVisual(true);
                }
            });
        }
        else
        {
            setTabStripVisible(true);
            document.getElementById("wiki-tab-visual").className = "tab-active";
            document.getElementById("wiki-tab-source").className = "tab-inactive";
            if(!tinyMCE.getEditorId("body"))
                tinyMCE.addMCEControl(document.getElementById(_idPrefix + "body"), "body");
            _editor = "visual";
            showEditingHelp(_wikiProps.rendererType);
            saveEditorPreference(_editor);
        }
    }

    function saveEditorPreference(editor)
    {
        var params = {useVisual: (editor == "visual")};
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("wiki", "setEditorPreference"),
            method : 'POST',
            success: onSaveEditorPrefSuccess,
            failure: onSaveEditorPrefError,
            jsonData : params,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function onSaveEditorPrefSuccess(response)
    {
        //for now, do nothing
    }

    function onSaveEditorPrefError(response)
    {
        //parse the response JSON and display the error
        var respJson = Ext.util.JSON.decode(response.responseText);
        if(respJson.exception)
            setError("There was a problem while saving your editor preference: " + respJson.exception);
        else
            setError("There was a problem while saving your editor preference: " + response.statusText);
    }

    function textContainsNonVisualElements(content)
    {
        var bodyText = new String(content);
        bodyText.toLowerCase();

        //look for pre, form, and script tags
        return null != bodyText.match(/<pre[\s>]/) ||
                null != bodyText.match(/<script[\s>]/) ||
                null != bodyText.match(/<form[\s>]/)

    }

    function setTabStripVisible(isVisible)
    {
        Ext.get("wiki-tab-strip").setDisplayed(isVisible);
        if(isVisible)
            Ext.get("wiki-tab-content").addClass("tab-content");
        else
            Ext.get("wiki-tab-content").removeClass("tab-content");
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

        var row;
        var cell;
        if(null == attachments || attachments.length == 0)
        {
            row = table.insertRow(0);
            cell = row.insertCell(0);
            cell.innerHTML = "[none]";
        }
        else
        {
            //add a row for each attachment
            for(var idx = 0; idx < attachments.length; ++idx)
            {
                row = table.insertRow(idx);
                row.id = "wiki-ea-" + idx;
                cell = row.insertCell(0);
                cell.id = "wiki-ea-icon-" + idx;
                cell.innerHTML = "<img src='" + attachments[idx].iconUrl + "' id='wiki-ea-icon-img-" + idx + "'/>";

                cell = row.insertCell(1);
                cell.id = "wiki-ea-name-" + idx;
                //append name as a text node so that it gets HTML encoded
                cell.appendChild(document.createTextNode(attachments[idx].name));

                cell = row.insertCell(2);
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

    function setClean()
    {
        _wikiProps.isDirty = false;
        _attachments.isDirty = false;

        //hack: tinyMCE doesn't have a proper API for resetting the dirty flag
        //but I found this at
        //http://www.bram.us/2007/06/15/my-tinymce-ajax-implementation-autosave-plugin-vs-isdirty-aka-fixing-the-tweak/
        var inst = tinyMCE.getInstanceById(tinyMCE.getEditorId("body"));
        if(inst)
        {
            inst.startContent = tinyMCE.trim(inst.getBody().innerHTML);
            inst.isNotDirty = true;
        }
    }

    function isDirty()
    {
        return _wikiProps.isDirty || _attachments.isDirty ||
        (tinyMCE.getEditorId("body") && tinyMCE.getInstanceById(tinyMCE.getEditorId("body")).isDirty());
    }

    var _convertWin;
    function showConvertWindow()
    {
        //initialize the from and possible to formats
        Ext.get("<%=ID_PREFIX%>window-change-format-from").update(_formats[_wikiProps.rendererType]);
        var toSelect = Ext.get("<%=ID_PREFIX%>window-change-format-to").dom;
        toSelect.innerHTML = "";
        for(var fmt in _formats)
        {
            if(fmt != _wikiProps.rendererType)
            {
                var opt = document.createElement("option");
                opt.value = fmt;
                opt.text = _formats[fmt];
                toSelect.add(opt, null);
            }
        }

        if(!_convertWin)
        {
            _convertWin = new Ext.Window({
                animateTarget: "<%=ID_PREFIX%>button-change-format",
                contentEl: "<%=ID_PREFIX%>window-change-format",
                title: "Change Format",
                width: 400,
                autoHeight: true,
                modal: true,
                resizable: false,
                closeAction: 'hide'
            });
        }
        _convertWin.show("<%=ID_PREFIX%>button-change-format");
    }

    function convertFormat()
    {
        var newType = Ext.get("<%=ID_PREFIX%>window-change-format-to").getValue();
        var transData = {name: _wikiProps.name, fromFormat: _wikiProps.rendererType, toFormat: newType};

        updateSourceFromVisual();
        transData.body = Ext.get("<%=ID_PREFIX%>body").getValue();

        setStatus("Converting Format...");
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("wiki", "transformWiki"),
            method : 'POST',
            success: onConvertSuccess,
            failure: onConvertError,
            jsonData : transData,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function onConvertSuccess(response)
    {
        var respJson = Ext.util.JSON.decode(response.responseText);

        _wikiProps.rendererType = respJson.toFormat;

        //if the new type is not html, switch to source and hide the tab strip
        if(respJson.toFormat != "HTML")
        {
            switchToSource();
            setTabStripVisible(false);
            updateControl("body", respJson.body);
        }
        else if(respJson.toFormat == "HTML")
        {
            updateControl("body", respJson.body);
            //if the new type is HTML, switch to visual
            switchToVisual();
        }

        setWikiDirty();

        //hide the convert window
        _convertWin.hide();

        setStatus("Converted. Click Save to save the converted content, or Cancel to abandon all your changes and exit.", true);
        showEditingHelp(_wikiProps.rendererType);
    }

    function onConvertError(response)
    {
        var respJson = Ext.util.JSON.decode(response.responseText);
        if(respJson.exception)
            setError("Unable to convert your page to the new format for the following reason:<br/>" + respJson.exception);
        else
            setError("Unable to convert your page to the new format: " + response.statusText);
    }

    function cancelConvertFormat()
    {
        if(_convertWin)
            _convertWin.hide();
    }

    function enableDeleteButton(enable)
    {
        var src = enable ? <%=PageFlowUtil.jsString(PageFlowUtil.buttonSrc("Delete Page"))%> : <%=PageFlowUtil.jsString(PageFlowUtil.buttonSrc("Delete Page", "disabled"))%>;
        var elem = document.getElementById(_idPrefix+"button-delete");
        if(elem)
            elem.src = src;
    }

    function showEditingHelp(format)
    {
        //hide all
        for(var fmt in _formats)
        {
            setEditingHelpDisplayed("wiki-help-" + fmt, false);
            setEditingHelpDisplayed("wiki-help-" + fmt + "-visual", false);
            setEditingHelpDisplayed("wiki-help-" + fmt + "-source", false);
        }

        //show the proper one
        setEditingHelpDisplayed("wiki-help-" + format + "-" + _editor, true);
    }

    function setEditingHelpDisplayed(id, isDisplayed)
    {
        var div = Ext.get(id);
        if(div)
            div.setDisplayed(isDisplayed);
    }

    function showHideToc()
    {
        var elem = Ext.get("wiki-toc-tree");
        if(elem)
            elem.setDisplayed(!elem.isDisplayed());
    }

    window.onbeforeunload = function(){
        if(isDirty())
            return "You have made changes that are not yet saved. Leaving this page now will abandon those changes.";
    }

</script>

<style type="text/css">
    .status-info
    {
        width: 99%;
        text-align: center;
        background-color: #FFDF8C;
        border: 1px solid #FFAD6A;
        padding: 2px;
        font-weight: bold;
    }
    .status-error
    {
        width: 99%;
        text-align: center;
        background-color: #FF5A7A;
        border: 1px solid #C11B17;
        color: #FFFFFF;
        font-weight: bold;
    }
    table.form-layout
    {
        width: 99%;
    }
    .stretch-input
    {
        width: 99%;
    }
    .button-bar
    {
        padding-top: 4px;
        padding-bottom: 4px;
        width: 99%;
    }
    .button-bar-right
    {
        text-align: right;
        width: 50%;
    }
    .button-bar-left
    {
        text-align: left;
        width: 50%
    }
    .tab-container
    {
        
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

<table class="button-bar">
    <tr>
        <td class="button-bar-left" nowrap="true">
            <%=PageFlowUtil.buttonLink("Save", "javascript:{}", "onSave()")%>
            <%=PageFlowUtil.buttonLink("Save and Finish", "javascript:{}", "onFinish()")%>
            <%=PageFlowUtil.buttonLink("Cancel", "javascript:{}", "onCancel()")%>
        </td>
        <td class="button-bar-right" nowrap="true">
            <% if(model.canUserDelete()) { %>
            <a href="javascript:{}" onclick="onDeletePage()">
                <img id="<%=ID_PREFIX%>button-delete" src="<%=PageFlowUtil.buttonSrc("Delete Page", "disabled")%>" alt="Delete Page"/>
            </a>
            <% } %>
            <a href="javascript:{}" onclick="showConvertWindow()">
                <img id="<%=ID_PREFIX%>button-change-format" src="<%=PageFlowUtil.buttonSrc("Convert To...")%>" alt="Convert To..."/>
            </a>
            <a href="javascript:{}" onclick="showHideToc()">
                <%=PageFlowUtil.buttonLink("Other Pages", "javascript:{}", "showHideToc()")%>
            </a>
            
        </td>
    </tr>
</table>
<table style="width:99%" cellpadding="0" cellspacing="0">
    <tr>
        <td style="width:99%">
            <table class="form-layout">
                <tr>
                    <td class="ms-searchform">Name</td>
                    <td class="field-content">
                        <input type="text" name="name" id="<%=ID_PREFIX%>name" size="80" onchange="onChangeName()"/>
                    </td>
                </tr>
                <tr>
                    <td class="ms-searchform">Title</td>
                    <td class="field-content">
                        <input type="text" name="title" id="<%=ID_PREFIX%>title" size="80" onchange="setWikiDirty()"/>
                    </td>
                </tr>
                <tr>
                    <td class="ms-searchform">Parent</td>
                    <td class="field-content">
                        <select name="parent" id="<%=ID_PREFIX%>parent" onchange="setWikiDirty()">
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
                            <tr id="wiki-tab-strip" style="display:none">
                                <td class="tab-blank">&nbsp;</td>
                                <td id="wiki-tab-visual" class="tab-active"><a href="javascript:{}" onclick="switchToVisual()">Visual</a></td>
                                <td id="wiki-tab-source" class="tab-inactive"><a href="javascript:{}" onclick="switchToSource()">Source</a></td>
                                <td class="tab-blank" style="width:100%">&nbsp;</td>
                            </tr>
                            <tr>
                                <td colspan="4" id="wiki-tab-content">
                                    <form action="">
                                    <textarea rows="30" cols="80" class="stretch-input" id="<%=ID_PREFIX%>body"
                                              name="body" onchange="setWikiDirty()"></textarea>
                                    </form>
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
        </td>
        <td style="width:1%;vertical-align:top;">
            <div id="wiki-toc-tree" class="extContainer" style="display:none"/>
        </td>
    </tr>
</table>
<div id="wiki-help-HTML-visual" style="display:none">
    <table>
        <tr>
            <td colspan=2><b>Formatting Guide:</b></td>
        </tr>
        <tr>
            <td>Link to a wiki page</td>
            <td>Select text and right click. Then select "Insert/edit link."
             Type the name of the wiki page in "Link URL" textbox.</td>
        </tr>
        <tr>
            <td>Link to an attachment</td>
            <td>Select text and right click. Then select "Insert/edit link."
             Type the name of the attachment with the file extension in "Link URL" textbox.</td>
        </tr>
    </table>
</div>
<div id="wiki-help-HTML-source" style="display:none">
    <table>
        <tr>
            <td colspan=2><b>Formatting Guide:</b></td>
        </tr>
        <tr>
            <td>Link to a wiki page</td>
            <td>&lt;a href="pageName"&gt;My Page&lt;/a&gt;</td>
        </tr>
        <tr>
            <td>Link to an attachment</td>
            <td>&lt;a href="attachment.doc"&gt;My Document&lt;/a&gt;</td>
        </tr>
        <tr>
            <td>Show an attached image</td>
            <td>&lt;img src="imageName.jpg"&gt;</td>
        </tr>
    </table>
</div>

<div id="wiki-help-RADEOX-source" style="display:none">
    <table>
        <tr>
            <td colspan=2><b>Formatting Guide</b> (<a target="_blank" href="<%=(new HelpTopic("wikiSyntax", HelpTopic.Area.SERVER)).getHelpTopicLink()%>">more help</a>):</td>
        </tr>
        <tr>
            <td>link to page in this wiki&nbsp;&nbsp;</td>
            <td>[pagename] or [Display text|pagename]</td>
        </tr>
        <tr>
            <td>external link</td>
            <td>http://www.google.com or {link:Display text|http://www.google.com}</td>
        </tr>
        <tr>
            <td>picture</td>
            <td>[attach.jpg] or {image:http://www.website.com/somepic.jpg}</td>
        </tr>
        <tr>
            <td>bold</td>
            <td>**like this**</td>
        </tr>
        <tr>
            <td>italics</td>
            <td>~~like this~~</td>
        </tr>
        <tr>
            <td>bulleted list</td>
            <td>- list item</td>
        </tr>
        <tr>
            <td>numbered List</td>
            <td>1. list item</td>
        </tr>
        <tr>
            <td>line break (&lt;br&gt;)</td>
            <td>\\</td>
        </tr>
    </table>
</div>

<div id="<%=ID_PREFIX%>window-change-format" class="x-hidden">
    <table cellpadding="2">
        <tr>
            <td>
                <span style="font-weight:bold;color:#FF0000">WARNING:</span>
                Changing the format of your page will change the way
                your page is interpreted, causing it to appear at least differently,
                if not incorrectly. In most cases, manual adjustment to the
                page content will be necessary. You should not perform this
                operation unless you know what you are doing.
            </td>
        </tr>
        <tr>
            <td>
                Convert page format from
                <b id="<%=ID_PREFIX%>window-change-format-from">(from)</b>
                to
                <select id="<%=ID_PREFIX%>window-change-format-to">
                </select>
            </td>
        </tr>
        <tr>
            <td style="text-align: right">
                <%=PageFlowUtil.buttonLink("Convert", "javascript:{}", "convertFormat()")%>
                <%=PageFlowUtil.buttonLink("Cancel", "javascript:{}", "cancelConvertFormat()")%>
            </td>
        </tr>
    </table>
</div>