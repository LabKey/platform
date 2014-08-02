/*
 * Copyright (c) 2008-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var _finished = false;
var _newAttachmentIndex = 0;
var _doingSave = false;
var _editor = "source";
var _tocTree;
var _tinyMCEInitialized = false;

// TinyMCE returns true from isDirty() if it's not done initializing, so keep track of whether it's safe to ask or not
function onInitCallback()
{
    _tinyMCEInitialized = true;
}

//you must init the tinyMCE before the page finishes loading
//if you don't, you'll get a blank page an an error
//seems to be a limitation of the tinyMCE.

//
// I coppied these options from Tinymce version 3.4 word.html example page, then trimmed down to remove
// unneeded buttons and plugins.  Added one third-party plugin, pwd, which toggles the lower two
// toolbars on and off.
//
tinyMCE.init({


    // General options
    mode : "none",
    theme : "advanced",
    plugins : "table, advlink, iespell, preview, media, searchreplace, print, paste, " +
        "contextmenu, fullscreen, noneditable, inlinepopups, style, pdw ",

    // tell tinymce not be be clever about URL conversion.  Dave added it to fix some bug.
    convert_urls: false,

    // Button bar -- rearraged based on my on aestheic judgement, not customer requests -georgesn
    theme_advanced_buttons1 : "pdw_toggle, |, undo, redo, |, search, |, formatselect, bold, italic, underline, |, " +
            "bullist, numlist, |, link, unlink, |, image, removeformat, fullscreen ",

    theme_advanced_buttons2 : "cut, copy, paste, pastetext, pasteword, iespell, |, justifyleft, justifycenter, justifyright, |, " +
            "outdent, indent, |, fontselect, fontsizeselect, forecolor, backcolor, ",

    theme_advanced_buttons3 : "preview, print, |, tablecontrols, |, hr, media, anchor, charmap, styleprops, |, help",


    theme_advanced_toolbar_location : "top",
    theme_advanced_toolbar_align : "left",
    theme_advanced_statusbar_location : "bottom",
    theme_advanced_resizing : true,

    // this allows firefox and webkit users to see red highlighting of miss-spelled words, even
    // though they can't correct them -- the tiny_mce contextmenu plugin takes over the context menu
    gecko_spellcheck : true,

    content_css : LABKEY.contextPath + "/core/themeStylesheet.view",

    // PDW (third-party) Toggle Toolbars settings.  see http://www.neele.name/pdw_toggle_toolbars
    pdw_toggle_on : 1,
    pdw_toggle_toolbars : "2,3",

    // labkey specific
    handle_event_callback : "tinyMceHandleEvent",

    init_instance_callback: onInitCallback
});


//the onReady function will execute after all elements
//have been loaded and parsed into the DOM
Ext.onReady(function(){
    updateControls(_wikiProps);
    updateExistingAttachments(_attachments);
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
        else if(_useVisualEditor)
            switchToVisual();
        else
            switchToSource();
    }

    //register for the document keypress event to trap ctrl+s for save
    Ext.EventManager.addListener(document, "keydown", onKeyDown);

    showEditingHelp(_wikiProps.rendererType);
    loadToc();
    Ext.get(_idPrefix+"name").focus();
});

//called by tinyMCE for *all* events so make sure to filter
//for keydown. return false to stop tinyMCE from handling or passing on
function tinyMceHandleEvent(evt)
{
    var handled = false;

    if(evt && "keydown" == evt.type && evt.ctrlKey
       && !evt.altKey && 83 == evt.keyCode) //ctrl+s
    {
        if(evt.shiftKey)
            onFinish();
        else
            onSave();
        handled = true;
    }

    if(handled)
    {
        //stop default handling and propogation
        if(evt.stopPropagation)
            evt.stopPropagation();
        else
            evt.cancelBubble = true;
        if(evt.preventDefault)
            evt.preventDefault();
        else
            evt.returnValue = false;
        return false;
    }
    else
        return true;
}

//Ext event handler--the evt object is an Ext.EventObject
//not the normal browser event
function onKeyDown(evt)
{
    var handled = false;
    if(evt.ctrlKey && !evt.altKey && 83 == evt.getKey()) //ctrl+s or ctrl+shift+s
    {
        if(evt.shiftKey)
            onFinish();
        else
            onSave();
        handled = true;
    }

    if(handled)
    {
        evt.preventDefault();
        evt.stopPropagation();
    }
}

function loadToc()
{
    //kick off a request to get the wiki toc
    Ext.Ajax.request({
        url: LABKEY.ActionURL.buildURL('wiki', 'getWikiToc'),
        method: 'GET',
        success: onTocSuccess,
        failure: LABKEY.Utils.getCallbackWrapper(onTocError, undefined, true),
        params: {currentPage: _wikiProps.name}
    });
}

function onTocSuccess(response)
{
    var json = Ext.util.JSON.decode(response.responseText);
    if(json.pages)
        createTocTree(json.pages);
    if(json.displayToc)
        showHideToc(true, false);
}

function onTocError(exceptionInfo)
{
    setError("Unable to load the wiki table of contents for this folder: " + exceptionInfo.exception);
}

function createTocTree(pages)
{
    var root = new Ext.tree.TreeNode({
        expanded: true,
        text: LABKEY.ActionURL.getContainer(),
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
    for(var idx = 0; idx < pages.length; ++idx)
    {
        var page = pages[idx];
        if(!page)
            continue;

        var childNode = new Ext.tree.TreeNode({
            id: page.name,
            text: page.title + " (" + page.name + ")",
            leaf: (null == page.children),
            singleClickExpand: true,
            icon: LABKEY.contextPath + "/_images/page.png",
            href: page.pageLink,
            expanded: page.expanded
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

    if(!isDirty() && _wikiProps.entityId)
    {
        onSaveComplete();
        return;
    }

    var wikiDataNew = gatherProps();

    setStatus("Saving...");
    Ext.Ajax.request({
        url : LABKEY.ActionURL.buildURL("wiki", "saveWiki"),
        method : 'POST',
        success: onSuccess,
        failure: LABKEY.Utils.getCallbackWrapper(onError, this, true),
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
    //per bug 5957, don't prompt about losing changes if the user clicks cancel
    setClean();
    _finished = true;
    window.location.href = _cancelUrl ? _cancelUrl : getRedirUrl();
}

function onDeletePage()
{
    window.location.href = LABKEY.ActionURL.buildURL("wiki", "delete") + "?name=" + encodeURIComponent(_wikiProps.name) + "&rowId=" + _wikiProps.rowId;
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
    else
        setWikiDirty();
}

function onChangeNameConfirmed(buttonId)
{
    if(buttonId == "yes")
    {
       setWikiDirty();
       _redirUrl = "";    //clear the redir URL since it will be referring to the old name
       onSave();
    }
    else
    {
        updateControl("name", _wikiProps.name);
    }
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

        if (_attachments.isDirty)
        {
            setStatus("Saving file attachments...");
            Ext.Ajax.request({
                params: {entityId: _wikiProps.entityId},
                url: LABKEY.ActionURL.buildURL("wiki", "attachFiles"),
                method : 'POST',
                form: 'form-files',
                isUpload: true,
                success: onAttachmentSuccess,
                failure: LABKEY.Utils.getCallbackWrapper(onAttachmentFailure, this, true)
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
                msg += "<li>" + LABKEY.Utils.encodeHtml(respJson.errors[err]) + "</li>";
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
        updateExistingAttachments(_attachments, false);
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

function onAttachmentFailure(exceptionInfo)
{
    _doingSave = false;
    _finished = false;
    setError("Unable to save attachments: " + exceptionInfo.exception);
}

function onError(exceptionInfo)
{
    _doingSave = false;
    _finished = false;
    setError("There was a problem while saving: " + exceptionInfo.exception);
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
        window.location.href = getRedirUrl();
    else
        loadToc();
}

//returns the redir URL, which can be a little tricky to determine in some
//scenarios. If the incoming redir url is null or empty, this function
//should return a url to the page view for the page, if it has a valid name
//if not, it should return a url for the project home page
function getRedirUrl()
{
    if(!_redirUrl || _redirUrl.length == 0)
    {
        if(_wikiProps.name && _wikiProps.name.length > 0)
            return LABKEY.ActionURL.buildURL("wiki", "page") + "?name=" + encodeURIComponent(_wikiProps.name);
        else
            return LABKEY.ActionURL.buildURL("project", "begin");
    }
    else
        return _redirUrl;
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
        {
            if(input.dom.type == "checkbox" || input.dom.type == "radio")
                ret[_editableProps[prop]] = input.dom.checked;
            else
                ret[_editableProps[prop]] = input.getValue();
        }
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
    tinymce.EditorManager.triggerSave();
}

function updateVisaulFromSource()
{
}

function updateControls(wikiProps)
{
    for(var prop in _editableProps)
        updateControl(_editableProps[prop], wikiProps[_editableProps[prop]]);
    updateBodyFormatCaption(wikiProps.rendererType);
}

function updateControl(propName, propValue)
{
    var elem = Ext.get(_idPrefix + propName);
    if(elem)
    {
        if (elem.dom.type == "checkbox" || elem.dom.type == "radio")
            elem.dom.checked = propValue;
        else
            elem.dom.value = null == propValue ? "" : propValue;
    }
}

function updateBodyFormatCaption(rendererType)
{
    Ext.get("wiki-current-format").update("(" + _formats[rendererType] + ")");
}

function setError(msg)
{
    if (_finished)
        return;
    var elem = Ext.get("status");
    elem.update(msg);
    elem.dom.className = "labkey-status-error";
    elem.setVisible(true);
}

function setStatus(msg, autoClear)
{
    var elem = Ext.get("status");
    elem.update(msg);
    elem.dom.className = "labkey-status-info";
    elem.setDisplayed(true);
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

function userSwitchToSource()
{
    switchToSource();
    saveEditorPreference(_editor);
}

function switchToSource()
{
    setTabStripVisible(true);
    document.getElementById("wiki-tab-visual").className = "labkey-tab-inactive";
    document.getElementById("wiki-tab-source").className = "labkey-tab-active";
    if(tinyMCE.get(_idPrefix + "body"))
        tinyMCE.execCommand('mceRemoveControl', false, _idPrefix + "body");
    _editor = "source";
    showEditingHelp(_wikiProps.rendererType);
}

function userSwitchToVisual()
{
    switchToVisual(false, true);
}

function switchToVisual(confirmOverride, savePreference)
{
    //check for elements that get mangled by the visual editor
    if(!confirmOverride && textContainsNonVisualElements(Ext.get(_idPrefix+"body").getValue()))
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
        document.getElementById("wiki-tab-visual").className = "labkey-tab-active";
        document.getElementById("wiki-tab-source").className = "labkey-tab-inactive";
        if(!tinyMCE.get(_idPrefix + "body"))
            tinyMCE.execCommand('mceAddControl', false, _idPrefix + "body");
        _editor = "visual";
        showEditingHelp(_wikiProps.rendererType);
        if(savePreference)
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
        failure: LABKEY.Utils.getCallbackWrapper(onSaveEditorPrefError, this, true),
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

function onSaveEditorPrefError(exceptionInfo)
{
    setError("There was a problem while saving your editor preference: " + exceptionInfo.exception);
}


function textContainsNonVisualElements(content)
{
    var bodyText = new String(content);
    bodyText.toLowerCase();

    // look for form, script tag
    // used to also block pre tag, but tinymc3.4 handles <pre> properly, so removed from blacklist
    return  null != bodyText.match(/<script[\s>]/) ||
            null != bodyText.match(/<form[\s>]/) ||
            null != bodyText.match(/<style[\s>]/)

}

function setTabStripVisible(isVisible)
{
    Ext.get("wiki-tab-visual").setDisplayed(isVisible);
    Ext.get("wiki-tab-source").setDisplayed(isVisible);
    Ext.get("wiki-tab-strip-spacer").setDisplayed(isVisible);
}

function updateExistingAttachments(attachments, encodeNames)
{
    if(!attachments)
        return;
    if(undefined === encodeNames)
        encodeNames = true;

    var table = document.getElementById("wiki-existing-attachments");
    if(!table)
        return;

    //clear the table
    while(table.hasChildNodes())
        table.removeChild(table.childNodes[0]);

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
            cell.innerHTML = "<a target='_blank' href='" + attachments[idx].downloadUrl + "'>"
                    + (encodeNames ? LABKEY.Utils.encodeHtml(attachments[idx].name) : attachments[idx].name) + "</a>";

            cell = row.insertCell(2);
            cell.id = "wiki-ea-del-" + idx;
            cell.innerHTML = "<a class='labkey-button' onclick='onDeleteAttachment(" + idx + ")'><span>delete</span></a>";
        }
    }
}

function onDeleteAttachment(index)
{
    var row = getExistingAttachmentRow(index);

    getExistingAttachmentIconImg(index).src = LABKEY.ActionURL.getContextPath() + "/_icons/_deleted.gif";
    row.cells[1].style.textDecoration = "line-through";
    row.cells[2].innerHTML = "<a class='labkey-button' onclick='onUndeleteAttachment(" + index + ")'><span>undelete</span></a>"
            + "<input type='hidden' name='toDelete' value=\"" + Ext.util.Format.htmlEncode(_attachments[index].name) + "\"/>";

    //add a prop so we know we need to save the attachments
    _attachments.isDirty = true;
}

function onUndeleteAttachment(index)
{
    var row = getExistingAttachmentRow(index);

    getExistingAttachmentIconImg(index).src = _attachments[index].iconUrl;
    row.cells[1].style.textDecoration = "";
    row.cells[2].innerHTML = "<a class='labkey-button' onclick='onDeleteAttachment(" + index + ")'><span>delete</span></a>";
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
    while(table.hasChildNodes())
        table.removeChild(table.childNodes[0]);
}

function addNewAttachmentInput(linkId)
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

    updateInstructions(document.getElementById(linkId), _newAttachmentIndex);
}

function onAddAttachment(fileInput, index)
{
    //update the name column
    var cell = document.getElementById("wiki-na-name-" + index);
    if(cell)
    {
        cell.setAttribute("nobreak", "1");
        cell.innerHTML = "<a class='labkey-button' onclick='onRemoveNewAttachment(" + index + ")'><span>remove</span></a>&nbsp;"
                + getFileName(fileInput.value);
    }

    //mark the attachments as dirty
    _attachments.isDirty = true;
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

    if(tinyMCE.get(_idPrefix + "body"))
        tinyMCE.get(_idPrefix + "body").isNotDirty = 1;

}

function isDirty()
{
    if (_wikiProps.isDirty || _attachments.isDirty)
    {
        return true;
    }
    if (_tinyMCEInitialized && tinyMCE.get(_idPrefix + "body") && tinyMCE.get(_idPrefix + "body").isDirty())
    {
        return true;
    }
    return false;
}

var _convertWin;
function showConvertWindow()
{
    //initialize the from and possible to formats
    Ext.get(_idPrefix + "window-change-format-from").update(_formats[_wikiProps.rendererType]);
    var toSelect = Ext.get(_idPrefix + "window-change-format-to").dom;
    while(toSelect.hasChildNodes())
        toSelect.removeChild(toSelect.childNodes[0]);

    for(var fmt in _formats)
    {
        if(fmt != _wikiProps.rendererType)
        {
            var opt = document.createElement("option");
            opt.value = fmt;
            opt.text = _formats[fmt];
            try
            {
                toSelect.add(opt, null);
            }
            catch(e)
            {
                //IE doesn't quite support the standard here.
                //they expect the second arg to be an index
                //not an object, and definitely not null
                toSelect.add(opt);
            }
        }
    }

    if(!_convertWin)
    {
        _convertWin = new Ext.Window({
            animateTarget: _idPrefix + "button-change-format",
            contentEl: _idPrefix + "window-change-format",
            title: "Change Format",
            width: 400,
            autoHeight: true,
            modal: true,
            resizable: false,
            closeAction: 'hide'
        });
    }
    _convertWin.show(_idPrefix + "button-change-format");
}

function convertFormat()
{
    var newType = Ext.get(_idPrefix + "window-change-format-to").getValue();

    gatherProps(); //to get current body
    var transData = {body: _wikiProps.body, fromFormat: _wikiProps.rendererType, toFormat: newType};

    updateSourceFromVisual();
    transData.body = Ext.get(_idPrefix + "body").getValue();

    setStatus("Converting Format...");
    Ext.Ajax.request({
        url : LABKEY.ActionURL.buildURL("wiki", "transformWiki"),
        method : 'POST',
        success: onConvertSuccess,
        failure: LABKEY.Utils.getCallbackWrapper(onConvertError, this, true),
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

        //if the new type is HTML, switch to visual appropriate editor
        if(_editor == "source")
            switchToSource();
        else
            switchToVisual();
    }

    setWikiDirty();

    //hide the convert window
    _convertWin.hide();

    setStatus("Converted. Click Save to save the converted content, or Cancel to abandon all your changes and exit.", true);
    updateBodyFormatCaption(_wikiProps.rendererType);
    showEditingHelp(_wikiProps.rendererType);
}

function onConvertError(exceptionInfo)
{
    setError("Unable to convert your page to the new format for the following reason:<br/>" + exceptionInfo.exception);
}

function cancelConvertFormat()
{
    if(_convertWin)
        _convertWin.hide();
}

function enableDeleteButton(enable)
{
    var elem = document.getElementById(_idPrefix+"button-delete");
    if (elem == null)
        return;

    if (enable)
    {
        elem.className = "labkey-button";
        elem.onclick = onDeletePage;
    }
    else
    {
        elem.className = "labkey-disabled-button";
        elem.onclick = function() {};
    }
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

function showHideToc(show, savePref)
{
    var elem = Ext.get("wiki-toc-tree");
    if(!elem)
        return;

    var displayed = show || !elem.isDisplayed();
    elem.setDisplayed(displayed);
    var button = Ext.get(_idPrefix + "button-toc");
    if(button)
        button.dom.innerHTML = displayed ? "Hide Page Tree" : "Show Page Tree";

    //save preference
    if(savePref == undefined || savePref)
    {
        var params = {displayed: (elem.isDisplayed())};
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("wiki", "setTocPreference"),
            method : 'POST',
            success: onSaveTocPrefSuccess,
            failure: LABKEY.Utils.getCallbackWrapper(onSaveTocPrefError, this, true),
            jsonData : params,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }
}

function onSaveTocPrefSuccess(response)
{
    //do nothing for now
}

function onSaveTocPrefError(exceptionInfo)
{
    setError("There was a problem while saving your show/hide page tree preference: " + exceptionInfo.exception);
}

window.onbeforeunload = function(){
    if(isDirty())
        return "You have made changes that are not yet saved. Leaving this page now will abandon those changes.";
};

