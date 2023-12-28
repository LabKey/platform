/*
 * Copyright (c) 2013-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

const TabNames = Object.freeze({
    Preview: 'preview',
    Source: 'source',
    Visual: 'visual',
});

(function($) {
    //
    // CONSTANTS
    //
    const _idPrefix = 'wiki-input-'; // This should be the same as the ID_PREFIX specified in wikiEdit.jsp
    const _idSel = '#' + _idPrefix;
    const _editorId = _idSel + 'body';
    const _editableProps = ['name', 'title', 'parent', 'body', 'shouldIndex', 'showAttachments'];
    let _finished = false;
    let _newAttachmentIndex = 0;
    let _tocTree;

    /**
     *  Handles save keyboard shortcut event properties
     *
     * @param event Keydown event
     * @returns {boolean} false to stop tinyMCE from handling or passing on ctrl [+ shift] + s
     */
    const handleSaveShortcut = (event) => {
        if (event.ctrlKey || event.metaKey) {
            if (String.fromCharCode(event.which).toLowerCase() === 's') {
                event.shiftKey ? onFinish() : onSave();

                event.preventDefault();
                event.stopPropagation();
                return false;
            }
        }

        return true;
    }

    //
    // Variables
    //
    let _attachments = [],
            _cancelUrl = '',
            _convertWin,
            _doingSave = false,
            _editor = TabNames.Source,
            _formats = {},
            _redirUrl = '',
            _wikiProps = {};

    const initTinymce = function  (initializedCallback) {
        tinymce.init({
            selector: 'textarea' + _editorId,

            browser_spellcheck : true,
            forced_root_block: 'div',
            plugins: [
                "advlist",
                "anchor",
                "autolink",
                "charmap",
                "code",
                "codesample",
                "emoticons",
                "help",
                "image",
                "insertdatetime",
                "link",
                "lists",
                "media",
                "preview",
                "quickbars",
                "searchreplace",
                "table",
                "visualblocks",
                "visualchars",
            ],

            // Prevents elements from being stripped by the Source editor https://www.tiny.cloud/docs/tinymce/6/content-filtering/#protect
            extended_valid_elements: 'i/em[*],+script[*],+form[*],+style[*]',
            valid_children: '+body[style]',
            protect: [
                /\<\/?(i .*)\>/g,
                /\<\/?(script.*)\>/g,
                /\<\/?(form.*)\>/g,
            ],

            menubar: 'edit insert view format table tools help',
            promotion: false,  // Remove sales logo in upper corner
            branding: false,   // Remove sales logo in bottom corner, Logo's link breaks crawler tests due to bad rel attribute.
            quickbars_insert_toolbar: 'anchor quickimage quicktable codesample hr accordion accordionremove',
            removed_menuitems: 'code, preview',
            theme: "silver",
            toolbar: "undo redo | styles fontsize  | bold italic underline strikethrough | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent | link image codesample",
            toolbar_sticky: true,

            //TODO in the near future: add style sheets for tables and things: https://www.tiny.cloud/docs/tinymce/6/add-css-options/#add-css-and-styles-to-the-editor
            //content_css:['mycss1.css', 'mycss2.css'],

            setup: (editor) => {
                editor.on('keydown', (evt) => {
                    return handleSaveShortcut(evt);
                });
            }
        }).then(initializedCallback);
    }

    const getVisualTab = function() { return $('#wiki-tab-visual'); };
    const getSourceTab = function() { return $('#wiki-tab-source'); };
    const getEditingTab = function() { return $('#wiki-tab-markdown-edit')};
    const getPreviewTab = function() { return $('#wiki-tab-markdown-preview')};

    const _init = function() {
        bindControls(_wikiProps);
        updateControls(_wikiProps);
        updateExistingAttachments(_attachments);
        enableDeleteButton(null != _wikiProps.entityId);

        // If the format is HTML, switch to visual/source if there are problematic elements
        if (_wikiProps.rendererType == "HTML") {
            if (textContainsNonVisualElements(_wikiProps.body)) {
                switchToSource();
                setStatus("Switching to source editing mode because your page has elements that are not supported by the visual editor.", true);
            }
            else if(_wikiProps.useVisualEditor)
                switchToVisual(true);
            else
                switchToSource();
        }

        showEditingHelp(_wikiProps.rendererType);
        loadToc();
        $(_idSel + 'name').focus();
    };

    var addNewAttachmentInput = function() {
        var linkId = 'wiki-file-link';
        var table = getNewAttachmentsTable();
        var row = table.insertRow(-1);
        row.id = "wiki-na-" + _newAttachmentIndex;

        var cell = row.insertCell(0);
        cell.innerHTML = "<input type='file' name='formFiles[" + _newAttachmentIndex + "]' size='60'>";
        const nodeIndex = _newAttachmentIndex;
        cell.childNodes[0]['onchange'] = function() { LABKEY._wiki.onAddAttachment(this, nodeIndex); };

        cell = row.insertCell(1);
        cell.id = "wiki-na-name-" + _newAttachmentIndex;
        cell.innerHTML = "&nbsp;";

        ++_newAttachmentIndex;

        updateInstructions(document.getElementById(linkId), _newAttachmentIndex);
    };

    var createTocTree = function(pages, cb) {

        getExt4(function() {
            _tocTree = Ext4.create('Ext.tree.Panel', {
                renderTo: 'wiki-toc-tree',
                width: 300,
                height: 587,
                autoScroll: true,
                store: {
                    xtype: 'tree',
                    root: {
                        expanded: true,
                        text: LABKEY.ActionURL.getContainer(),
                        children: pages
                    }
                }
            });
            cb.call(this);
        });

    };

    /**
     * Binds all events to the form attributes of the editor.
     * @param props
     */
    var bindControls = function(props) {
        // form controls
        var setDirty = function(){LABKEY.setDirty(true)};
        $(_idSel + 'name').keypress(setDirty).change(setDirty);
        $(_idSel + 'title').keypress(setDirty).change(setDirty);
        $(_idSel + 'parent').keypress(setDirty).change(setDirty);
        $(_idSel + 'body').keypress(setDirty).change(setDirty);
        $(_idSel + 'shouldIndex').keypress(setDirty).change(setDirty);
        $(_idSel + 'showAttachments').keypress(setDirty).change(setDirty);
        $('#wiki-file-link').click(addNewAttachmentInput);

        // active tab
        getVisualTab().find('a').click(userSwitchToVisual);
        getSourceTab().find('a').click(userSwitchToSource);

        // register for the document keydown event to trap ctrl+s for save
        $(document).keydown(handleSaveShortcut);
    };

    var clearNewAttachments = function() {
        var table = getNewAttachmentsTable();
        while (table.hasChildNodes())
            table.removeChild(table.childNodes[0]);
    };

    var convertFormat = function() {
        var newType = $(_idSel + 'window-change-format-to').val();

        gatherProps(); //to get current body
        updateSourceFromVisual();

        setStatus("Converting Format...");
        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL("core", "transformWiki"),
            method : 'POST',
            jsonData : {
                body: $(_idSel + 'body').val(),
                fromFormat: _wikiProps.rendererType,
                toFormat: newType
            },
            success: onConvertSuccess,
            failure: LABKEY.Utils.getCallbackWrapper(function(exceptionInfo) {
                setError("Unable to convert your page to the new format for the following reason:<br/>" + exceptionInfo.exception);
            }, this, true)
        });
    };

    var enableDeleteButton = function(enable) {
        var btn = $(_idSel + 'button-delete');
        if (enable) {
            btn.bind("click", onDeletePage).attr('class', 'labkey-button');
        }
        else {
            btn.unbind("click", onDeletePage).attr('class', 'labkey-button labkey-disabled-button');
        }
    };

    var gatherProps = function() {
        // init the return obj with the read-only values
        var ret = $.extend({}, _wikiProps);

        updateSourceFromVisual();

        // get editable props from controls
        for (var i=0; i < _editableProps.length; i++) {
            var propName = _editableProps[i], val = undefined;
            if ($(_idSel + propName + ':checkbox').length > 0 || $(_idSel + propName + ':radio').length > 0) {
                val = $(_idSel + propName).prop('checked');
            }
            else {
                val = $(_idSel + propName).val();
            }

            if (val !== undefined) {
                if (propName === 'body') {
                    val = LABKEY.Utils.wafEncode(val);
                }
                ret[_editableProps[i]] = val;
            }
        }

        return ret;
    };

    var getExistingAttachmentIconImg = function(index) {
        var img = document.getElementById("wiki-ea-icon-img-" + index);
        if (!img)
            LABKEY.Utils.alert("Error", "Could not get img element for existing attachment!");
        return img;
    };

    var getExistingAttachmentRow = function(index) {
        var row = document.getElementById("wiki-ea-" + index);
        if (!row)
            LABKEY.Utils.alert("Error", "Could not access the existing attachment table row!");
        return row;
    };

    var getExt4 = function(cb) {
        LABKEY.requiresExt4Sandbox(function() { Ext4.onReady(cb); });
    };

    var getFileName = function(pathname) {
        if (pathname.indexOf('/') > -1) {
            return pathname.substring(pathname.lastIndexOf('/') + 1, pathname.length);
        }
        return pathname.substring(pathname.lastIndexOf('\\') + 1, pathname.length);
    };

    // returns the redir URL, which can be a little tricky to determine in some
    // scenarios. If the incoming redir url is null or empty, this function
    // should return a url to the page view for the page, if it has a valid name
    // if not, it should return a url for the project home page
    var getRedirUrl = function() {
        if (!_redirUrl || _redirUrl.length == 0) {
            if (_wikiProps.name && _wikiProps.name.length > 0)
                return LABKEY.ActionURL.buildURL('wiki', 'page', null, {name: _wikiProps.name});
            return LABKEY.ActionURL.buildURL('project', 'begin');
        }
        return _redirUrl;
    };

    var isDirty = function() {
        var isBodyDirty = tinymce.activeEditor?.isDirty();
        return isBodyDirty || LABKEY.isDirty();
    };

    var loadToc = function() {
        // kick off a request to get the wiki toc
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('wiki', 'getWikiToc.api'),
            method: 'GET',
            params: {currentPage: _wikiProps.name},
            success: function(response) {
                var json = LABKEY.Utils.decode(response.responseText);
                if (json.pages) {
                    createTocTree(json.pages, function() {
                        if (json.displayToc) {
                            showHideToc(true, false);
                        }
                    });
                }
            },
            failure: LABKEY.Utils.getCallbackWrapper(function() {
                setError("Unable to load the wiki table of contents for this folder: " + exceptionInfo.exception);
            }, undefined, true)
        });
    };

    var onAddAttachment = function(fileInput, index) {
        // update the name column
        var cell = $('#wiki-na-name-' + index).attr('nobreak', '1').html('<a class="labkey-button"><span>remove</span></a>&nbsp;' + getFileName(fileInput.value));

        // mark the attachments as dirty
        LABKEY.setDirty(true);
    };

    var onAttachmentSuccess = function(response) {
        //parse the response JSON
        var respJson = LABKEY.Utils.decode(response.responseText);
        if (!respJson.success)
        {
            onAttachmentFailure(respJson);
            return;
        }
        if (respJson.attachments) {
            _attachments = respJson.attachments;
            updateExistingAttachments(_attachments, false);
            clearNewAttachments();
        }

        var status = "Saved.";
        if (respJson.warnings) {
            status = 'Your changes were saved but the following warnings were returned:<span style="text-align: left;"><ul>';
            $.each(respJson.warnings, function(i, warn) {
                status += '<li>' + LABKEY.Utils.encodeHtml(warn) + '</li>';
            });
            status += '</ul></span>';
        }
        onSaveComplete(status);
    };

    var onAttachmentFailure = function(exceptionInfo) {
        _doingSave = false;
        _finished = false;
        setError("Unable to save attachments: " + exceptionInfo.exception);
    };

    var onCancel = function() {
        // per bug 5957, don't prompt about losing changes if the user clicks cancel
        LABKEY.setDirty(false);
        setBodyClean();

        _finished = true;
        window.location = _cancelUrl ? _cancelUrl : getRedirUrl();
    };

    var onConvertSuccess = function(response) {
        var respJson = LABKEY.Utils.decode(response.responseText);

        _wikiProps.rendererType = respJson.toFormat;

        //if the new type is not html, switch to source and hide the tab strip
        if (respJson.toFormat === "HTML") {
            updateControl("body", respJson.body);

            // if the new type is HTML, switch to visual appropriate editor
            _editor === TabNames.Source ? switchToSource() : switchToVisual();
        }
        else {
            switchToSource();
            setTabStripVisible(false);
            updateControl("body", respJson.body);
        }

        LABKEY.setDirty(true);

        // hide the convert window
        if (_convertWin) {
            _convertWin.hide();
        }

        setStatus("Converted. Click Save to save the converted content, or Cancel to abandon all your changes and exit.", true);
        updateBodyFormatCaption(_wikiProps.rendererType);
        showEditingHelp(_wikiProps.rendererType);
    };

    // TODO: We shouldn't need to expose either of these methods, rather hookup the onclicks after appending the elements
    var onDeleteAttachment = function(index) {
        var row = getExistingAttachmentRow(index);

        getExistingAttachmentIconImg(index).src = LABKEY.ActionURL.getContextPath() + "/_icons/_deleted.gif";
        row.cells[1].style.textDecoration = "line-through";
        row.cells[2].innerHTML = "<a><span>&nbsp; un-delete</span></a>"
                + "<input type='hidden' name='toDelete' value=\"" + LABKEY.Utils.encodeHtml(_attachments[index].name) + "\"/>";
        const nodeIndex = index;
        row.cells[2].childNodes[0]['onclick'] = function() { LABKEY._wiki.onUndeleteAttachment(nodeIndex); };

        //add a prop so we know we need to save the attachments
        LABKEY.setDirty(true);
    };

    var getNewAttachmentsTable = function() {
        var table = document.getElementById("wiki-new-attachments");
        if (!table)
            LABKEY.Utils.alert("Error", "Could not get the new attachments table!");
        return table;
    };

    var onUndeleteAttachment = function(index) {
        var row = getExistingAttachmentRow(index);

        getExistingAttachmentIconImg(index).src = _attachments[index].iconUrl;
        row.cells[1].style.textDecoration = "";
        row.cells[2].innerHTML = "<a>&nbsp; delete</a>";
        const nodeIndex = index;
        row.cells[2].childNodes[0]['onclick'] = function(){ LABKEY._wiki.onDeleteAttachment(nodeIndex); };
    };

    var onDeletePage = function() {
        window.location.href = LABKEY.ActionURL.buildURL('wiki', 'delete', null, {name: _wikiProps.name, rowId: _wikiProps.rowId});
    };

    const onError = function (exceptionInfo) {
        _doingSave = false;
        _finished = false;
        setError("There was a problem while saving: " + exceptionInfo.exception);
    };

    const onFinish = function() {
        _finished = true;
        onSave();
    };

    const onSave = function() {
        if (_doingSave)
            return;
        _doingSave = true;


        if (_wikiProps.entityId && !isDirty()) {
            onSaveComplete();
            return;
        }

        setStatus("Saving...");
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('wiki', 'saveWiki'),
            method: 'POST',
            jsonData: gatherProps(),
            success: onSuccess,
            failure: LABKEY.Utils.getCallbackWrapper(onError, this, true)
        });
    };

    const onSaveComplete = function(statusMessage) {
        LABKEY.setDirty(false);
        setBodyClean();
        _doingSave = false;
        if (!statusMessage) {
            statusMessage = "Saved.";
        }

        setStatus(statusMessage, true);
        enableDeleteButton(true);

        if (_finished) {
            window.location = getRedirUrl();
        }
        else {
            loadToc();
        }
    };

    const onSuccess = function(response) {
        // parse the response JSON
        var respJson = LABKEY.Utils.decode(response.responseText);
        if (respJson.success) {
            // update the current wiki props
            if (respJson.wikiProps) {
                _wikiProps = respJson.wikiProps;
                updateControls(_wikiProps);
            }

            if (isDirty()) {
                setStatus("Saving...");
                getExt4(function() {
                    // bah, for now we have to use Ext4 to do this post since it is an upload
                    Ext4.Ajax.request({
                        params: {entityId: _wikiProps.entityId},
                        url: LABKEY.ActionURL.buildURL('wiki', 'attachFiles.api'),
                        method : 'POST',
                        form: 'form-files',
                        isUpload: true,
                        success: onAttachmentSuccess,
                        failure: LABKEY.Utils.getCallbackWrapper(onAttachmentFailure, this, true)
                    });
                });
            }
            else {
                // no attachments to save
                onSaveComplete();
            }
        }
        else {
            _doingSave = false;
            _finished = false;
            // report validaton errors
            if (respJson.errors) {
                var msg = "Unable to save changes due to the following validation errors:<span style='text-align:left'><ul>";
                for(var err in respJson.errors)
                    msg += "<li>" + LABKEY.Utils.encodeHtml(respJson.errors[err]) + "</li>";
                msg += "</ul></span>";
                setError(msg);
            }
            else {
                setError("Unable to save changes for an unknown reason.");
            }
        }
    };

    var updateBodyFormatCaption = function(rendererType) {
        $('#wiki-current-format').html('(' + _formats[rendererType] + ')');
    };

    var updateControl = function(propName, propValue) {
        if ($(_idSel + propName + ':checkbox').length > 0 || $(_idSel + propName + ':radio').length > 0) {
            $(_idSel + propName).prop('checked', propValue === true);
        }
        else {
            $(_idSel + propName).val(null == propValue ? "" : propValue);
        }
    };

    /**
     * Updates the values associated with the wiki form to what is set in wikiProps
     * @param allProps
     */
    var updateControls = function(allProps) {
        $.each(_editableProps, function(i, eprop) {
            updateControl(eprop, allProps[eprop]);
        });
        updateBodyFormatCaption(allProps.rendererType);
    };

    var saveEditorPreference = function(editor) {
        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL("wiki", "setEditorPreference"),
            method : 'POST',
            jsonData : { useVisual: (editor === TabNames.Visual) },
            success: function() {},
            failure: LABKEY.Utils.getCallbackWrapper(function(exceptionInfo) {
                setError("There was a problem while saving your editor preference: " + exceptionInfo.exception);
            }, this, true)
        });
    };

    var setBodyClean = function() {
        tinymce.activeEditor?.setDirty(false);
    };

    var setError = function(msg) {
        if (_finished)
            return;
        $('#status').text(msg).attr('class', 'labkey-status-error').show();
    };

    var setStatus = function(msg, autoClear) {
        $('#status').html(msg).attr('class', 'labkey-status-info').show();
        if (autoClear) {
            setTimeout(function() { $('#status').html('').hide(); }, 5000);
        }
    };

    var setTabStripVisible = function(isVisible) {
        if (isVisible) {
            getVisualTab().show();
            getSourceTab().show();
            $('#wiki-tab-strip-spacer').show();
        }
        else {
            getVisualTab().hide();
            getSourceTab().hide();
            $('#wiki-tab-strip-spacer').hide();
        }
    };

    var showConvertWindow = function() {
        // initialize the from and possible to formats
        $(_idSel + 'window-change-format-from').html(_formats[_wikiProps.rendererType]);
        var toSelect = $(_idSel + 'window-change-format-to');
        toSelect.children().remove();

        $.each(_formats, function(fmt, label) {
            if (fmt !== _wikiProps.rendererType) {
                toSelect.append('<option value=\"' + fmt + '\">' + label + '</option>');
            }
        });

        if (!_convertWin) {
            getExt4(function() {
                _convertWin = Ext4.create('Ext.Window', {
                    title: 'Change Format',
                    autoShow: true,
                    animateTarget: _idPrefix + "button-change-format",
                    contentEl: _idPrefix + "window-change-format",
                    width: 400,
                    modal: true,
                    resizable: false,
                    closeAction: 'hide',
                    bbar: [
                        '->',
                        {text: 'Convert', handler: convertFormat },
                        {text: 'Cancel', handler: function() { _convertWin.hide(); } }
                    ]
                });
            });
        }
        else {
            _convertWin.show(_idPrefix + "button-change-format");
        }
    };

    var showEditingHelp = function(format) {
        // hide all
        for (var fmt in _formats)
        {
            setEditingHelpDisplayed("wiki-help-" + fmt, false);
            setEditingHelpDisplayed("wiki-help-" + fmt + "-visual", false);
            setEditingHelpDisplayed("wiki-help-" + fmt + "-source", false);
        }

        // show the proper one
        setEditingHelpDisplayed("wiki-help-" + format + "-" + _editor, true);
    };

    var showHideToc = function(show, savePref)
    {
        var el = $('#wiki-toc-tree');
        if (el && el.length > 0) {
            var displayed = show || el.is(':hidden');
            displayed ? el.show() : el.hide();
            $(_idSel + 'button-toc').html((displayed ? "Hide " : "Show ") + "Page Tree");

            // save preference
            if (savePref == undefined || savePref) {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('wiki', 'setTocPreference'),
                    method: 'POST',
                    jsonData: {displayed: el.is(':visible')},
                    success: function() {},
                    failure: LABKEY.Utils.getCallbackWrapper(function(exceptionInfo) {
                        setError("There was a problem while saving your show/hide page tree preference: " + exceptionInfo.exception);
                    }, this, true)
                });
            }
        }
    };

    var setEditingHelpDisplayed = function(id, isDisplayed) {
        var el = $('#' + id);
        if (el) { isDisplayed ? el.show() : el.hide(); }
    };

    var switchToSource = function() {
        setTabStripVisible(true);
        getVisualTab().attr('class', 'labkey-tab-inactive');
        getSourceTab().attr('class', 'labkey-tab-active');
        tinymce.activeEditor?.hide();
        _editor = TabNames.Source;
        showEditingHelp(_wikiProps.rendererType);
    };

    var switchToVisual = function(confirmOverride, savePreference) {
        //check for elements that get mangled by the visual editor
        if (!confirmOverride) {
            getExt4(function() {
                Ext4.Msg.show({
                    title: 'Warning',
                    msg: "Some elements are not supported by the visual editor and may be removed. Are you sure you want to switch to the visual editor?",
                    buttons: Ext4.Msg.YESNO,
                    animEl: 'wiki-tab-visual',
                    icon: Ext4.Msg.QUESTION,
                    fn: function(btn) {
                        if (btn == "yes") {
                            switchToVisual(true, savePreference);
                        }
                    }
                });
            });
        }
        else if (!tinymce.activeEditor) {
            LABKEY.requiresScript('tinymce/js/tinymce/tinymce.min.js', () => {
                initTinymce(()=> switchToVisual(confirmOverride, savePreference));
            }, this);
        }
        else {
            setTabStripVisible(true);
            getVisualTab().attr('class', 'labkey-tab-active');
            getSourceTab().attr('class', 'labkey-tab-inactive');
            tinymce.activeEditor?.show();
            _editor = TabNames.Visual;
            showEditingHelp(_wikiProps.rendererType);
            if (savePreference)
                saveEditorPreference(_editor);
        }
    };

    var textContainsNonVisualElements = function(content) {
        var bodyText = new String(content);
        bodyText.toLowerCase();

        // look for form, script tag
        // used to also block pre tag, but tinymc3.4 handles <pre> properly, so removed from blacklist
        return  null != bodyText.match(/<script[\s>]/) ||
                null != bodyText.match(/<form[\s>]/) ||
                null != bodyText.match(/<i[\s>]/) ||
                null != bodyText.match(/<style[\s>]/)
    };

    var updateExistingAttachments = function(attachments, encodeNames) {
        if (!attachments)
            return;
        if (undefined === encodeNames)
            encodeNames = true;

        var table = document.getElementById("wiki-existing-attachments");
        if (!table)
            return;

        //clear the table
        while (table.hasChildNodes())
            table.removeChild(table.childNodes[0]);

        var row, cell;
        if (attachments.length === 0) {
            row = table.insertRow(0);
            cell = row.insertCell(0);
            cell.innerHTML = "[none]";
        }
        else {
            //add a row for each attachment
            for (var idx = 0; idx < attachments.length; ++idx) {
                row = table.insertRow(idx);
                row.id = "wiki-ea-" + idx;
                cell = row.insertCell(0);
                cell.id = "wiki-ea-icon-" + idx;
                cell.innerHTML = "<img src='" + attachments[idx].iconUrl + "' id='wiki-ea-icon-img-" + idx + "'/>";

                cell = row.insertCell(1);
                cell.id = "wiki-ea-name-" + idx;
                cell.innerHTML = "<a target='_blank' href='" + attachments[idx].downloadUrl + "'>"
                    + "&nbsp;" + (encodeNames ? LABKEY.Utils.encodeHtml( attachments[idx].name) : attachments[idx].name) + "</a>";

                cell = row.insertCell(2);
                cell.id = "wiki-ea-del-" + idx;
                cell.innerHTML = "<a>&nbsp; delete</a>";
                const nodeIdx = idx;
                cell.childNodes[0]['onclick'] = function() { LABKEY._wiki.onDeleteAttachment(nodeIdx); };
            }
        }
    };

    var updateSourceFromVisual = function() {
        if (_editor === TabNames.Visual)
            tinymce.triggerSave();
    };

    var userSwitchToSource = function() {
        switchToSource();
        saveEditorPreference(_editor);
    };

    var userSwitchToVisual = function() {
        switchToVisual(false, true);
    };

    $(window).on('beforeunload', function() {
        if (isDirty()) {
            return "You have made changes that are not yet saved. Leaving this page now will abandon those changes.";
        }
    });

    LABKEY._wiki = new function() {
        return {
            getProps: function() { // used by tests
                return $.extend({}, _wikiProps);
            },
            onAddAttachment: onAddAttachment,
            onCancel: onCancel,
            onDeleteAttachment: onDeleteAttachment,
            onFinish: onFinish,
            onSave: onSave,
            onUndeleteAttachment: onUndeleteAttachment,
            setAttachments: function(atth) {
                _attachments = atth;
            },
            setFormats: function(formats) {
                _formats = formats;
            },
            setProps: function(props) {
                _wikiProps = props;
            },
            setURLs: function(redirURL, cancelURL) {
                _redirUrl = redirURL; _cancelUrl = cancelURL;
            },
            showConvertWindow: showConvertWindow,
            showHideToc: showHideToc
        };
    };

    LABKEY.Utils.onReady(_init);
})(jQuery);
