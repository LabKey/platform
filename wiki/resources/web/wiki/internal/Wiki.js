/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function () {

    function InlineEditor(config)
    {
        if (!config.id && !config.dom)
            throw new Error("element id or dom node required");

        if (!config.save)
            throw new Error("save callback required");

        if (config.hasOwnProperty('updateContentURL')) {
            this.updateContentURL = config.updateContentURL;
        }

        if (this.id)
        {
            this.id = config.id;
            this.dom = document.getElementById(config.id);
        }
        else
        {
            this.dom = config.dom;
            this.id = LABKEY.Utils.generateUUID();
        }

        this.editFieldId = id + "_inline_field";
        this.saving = false;
        this.config = config;
    }

    InlineEditor.prototype.addEditClass = function () {
        tinymce.DOM.addClass(this.dom, "labkey-inline-editor-active");
    };

    InlineEditor.prototype.removeEditClass = function () {
        tinymce.DOM.removeClass(this.dom, "labkey-inline-editor-active");
    };

    InlineEditor.prototype.isEditing = function () {
        return this.ed || tinymce.DOM.hasClass(this.dom, "labkey-inline-editor-active");
    };

    InlineEditor.prototype.addMessage = function (html) {
        tinymce.DOM.show(this.msgbox);
        this.msgbox.innerHTML = "<div class='labkey-message'>" + html + "</div>";
    };

    InlineEditor.prototype.addErrorMessage = function (html) {
        tinymce.DOM.show(this.msgbox);
        this.msgbox.innerHTML = "<div class='labkey-error'>" + html + "</div>";
    };

    InlineEditor.prototype.edit = function () {
        if (this.saving)
            return;

        // Listen for page navigation and prompt if dirty
        this.unloadHandler = this.createOnBeforeUnload();
        window.addEventListener("beforeunload", this.unloadHandler);

        this.addEditClass();

        // save original html
        this.originalValue = this.dom.innerHTML;

        // get the height of the curent content to initialize the size of the inline editor
        var rect = tinymce.DOM.getRect(this.dom);
        var height = Math.max(250, Math.min(500, rect && rect.h));

        // replace content with a textarea and buttons
        var msgboxId = this.editFieldId + "_msgbox";
        var html = "<div class='labkey-inline-editor'>" +
                "<textarea id='" + this.editFieldId + "' style='width:100%; height:" + height + "px;'>" +
                this.originalValue +
                "</textarea>" +
                "<div id='" + msgboxId + "' class='labkey-dataregion-msgbox' style='display:none;'></div>" +
                "<p>" +
                "<a class='labkey-button' name='save'><span>Save</span></a>" +
                "<a class='labkey-button' name='cancel'><span>Cancel</span></a>";

        if (this.updateContentURL) {
            html = html + "<a style='margin-left: 25px' class='labkey-button' name='advanced' href='"+ this.updateContentURL + "'><span>Advanced Editor</span></a>";
        }

        html = html + "</p>" +
                "</div>";
        this.dom.innerHTML = html;

        this.msgbox = document.getElementById(msgboxId);

        // find the buttons and attach click events
        var buttons = this.dom.getElementsByClassName("labkey-button");
        var saveBtn = buttons[0];
        var cancelBtn = buttons[1];

        tinymce.dom.Event.add(saveBtn, 'click', this.onSaveClick, this);
        tinymce.dom.Event.add(cancelBtn, 'click', this.onCancelClick, this);

        // create the editor
        this.ed = new tinymce.Editor(this.editFieldId, {

            height: height,

            // General options
            mode: "none",
            theme: "advanced",
            plugins: "table, advlink, iespell, preview, media, searchreplace, print, paste, " +
                    "contextmenu, fullscreen, noneditable, inlinepopups, style, ",

            // tell tinymce not be be clever about URL conversion.  Dave added it to fix some bug.
            convert_urls: false,

            // Smaller button bar than found on regular wiki edit
            theme_advanced_buttons1 : "fontselect, fontsizeselect, " +
                    "|, bold, italic, underline, " +
                    "|, forecolor, backcolor, " +
                    "|, justifyleft, justifycenter, justifyright, " +
                    "|, bullist, numlist, " +
                    "|, outdent, indent, " +
                    "|, link, unlink, " +
                    "|, image, removeformat, ",

            theme_advanced_buttons2 : null,
            theme_advanced_buttons3 : null,


            theme_advanced_toolbar_location : "top",
            theme_advanced_toolbar_align : "left",
            theme_advanced_statusbar_location : "bottom",
            theme_advanced_resizing : false,

            // this allows firefox and webkit users to see red highlighting of miss-spelled words, even
            // though they can't correct them -- the tiny_mce contextmenu plugin takes over the context menu
            gecko_spellcheck : true,

            content_css : LABKEY.contextPath + "/core/themeStylesheet.view"

            // labkey specific
            //handle_event_callback : tinyMceHandleEvent,

            //init_instance_callback: onInitCallback
        });

        this.ed.render();
    };

    InlineEditor.prototype.cleanup = function () {
        this.removeEditClass();

        if (this.unloadHandler) {
            window.removeEventListener("beforeunload", this.unloadHandler);
        }

        if (this.ed) {
            this.ed.remove();
            this.ed.destroy();
            delete this.ed;
        }

        if (this.msgbox) {
            delete this.msgbox;
        }
    };

    InlineEditor.prototype.cancel = function () {
        if (this.saving)
            return;

        this.cleanup();

        // restore original html
        this.dom.innerHTML = this.originalValue;
    };

    InlineEditor.prototype.save = function () {
        if (this.saving)
            return;

        this.saving = true;

        var content = this.ed.getContent();

        // TODO: Use Ext Observable or something instead
        // invoke save callback
        this.config.save.apply(this.config.scope, [ this, content ]);
    };

    // Handle InlineEditor specific functionality in this callback.
    // clients are responsible for calling this upon successful save.
    InlineEditor.prototype.onSaveSuccess = function (ret, content) {
        this.saving = false;
        this.cleanup();

        // update modified html
        this.dom.innerHTML = content;
    };

    // clients are responsible for calling this upon save failure.
    InlineEditor.prototype.onSaveFailure = function (errorInfo) {
        this.saving = false;
        this.addErrorMessage(errorInfo.exception || "Error saving wiki");
    };

    InlineEditor.prototype.onSaveClick = function () {
        if (this.saving)
            return;

        if (this.ed.isDirty()) {
            this.save();
        } else {
            this.cancel();
        }
    };

    InlineEditor.prototype.onCancelClick = function () {
        if (this.saving)
            return;

        if (this.ed.isDirty()) {
            // prompt "Are you sure?"
        }
        this.cancel();
    };

    InlineEditor.prototype.createOnBeforeUnload = function () {
        var self = this;
        return function (event) {
            if (self.ed && self.ed.isDirty()) {
                return "You have made changes that are not yet saved.  Leaving this page now will abandon those changes.";
            }
        }
    };


    // private
    function isEditing (dom) {
        return tinymce.DOM.hasClass(dom, "labkey-inline-editor-active");
    }


    // private
    // Initialize TinyMCE for inline editing on the given wiki element or id.
    function inlineWikiEdit(config)
    {
        if (!config.dom && !config.id)
            throw new Error("dom node or id required");

        if (isEditing(config.dom))
        {
            console.log("Editor already active");
            return;
        }

        tinymceinit();
        var editor = new InlineEditor({
            dom: config.dom,
            id: config.id,
            updateContentURL: config.updateContentURL,
            save: function (inlineEditor, content) {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL("wiki", "saveWiki"),
                    method: 'POST',
                    success: LABKEY.Utils.getCallbackWrapper(function (resp) {
                        // Handle wiki specific functionality in this callback.
                        if (resp.success && resp.wikiProps)
                        {
                            // Stash the update page version id on the dom node to allow wiki to be edited again after saving.
                            config.dom.pageVersionId = resp.wikiProps.pageVersionId;
                            editor.onSaveSuccess(resp, resp.wikiProps.body);
                        }
                        else
                        {
                            editor.onSaveFailure(resp);
                        }
                    }),
                    failure: LABKEY.Utils.getCallbackWrapper(editor.onSaveFailure, editor, true),
                    jsonData: {
                        entityId: config.entityId,
                        // Use dom node's pageVersionId if we've saved previously.
                        pageVersionId: config.dom.pageVersionId || config.pageVersionId,
                        name: config.name,
                        title: config.title,
                        rendererType: config.rendererType,
                        parentId: config.parentId,
                        showAttachments: config.showAttachments,
                        shouldIndex: config.shouldIndex,
                        body: content
                    },
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
            }
        });

        editor.edit();
    }

    function tinymceinit()
    {
        // convince tinymce that the page is loaded
        tinymce.dom.Event.domLoaded = 1;
    }

    // create namespaces
    if (!LABKEY.wiki) {
        LABKEY.wiki = {};
    }
    if (!LABKEY.wiki.internal) {
        LABKEY.wiki.internal = {};
    }

    LABKEY.wiki.internal.Wiki = {
        adjustAllTocEntries : function(parentId, notify, expand) {
            var tocParent = document.getElementById(parentId);
            var tocTable = tocParent.childNodes.item(0);

            while (tocTable && tocTable.nodeName != "TABLE") {
                tocTable = tocTable.nextSibling;
            }

            if (tocTable) {
                LABKEY.wiki.internal.Wiki.toggleTable(tocTable, expand, notify);
            }
        },
        createWebPartInlineEditor : function (config) {
            if (!config.webPartId)
                throw new Error("webPartId required");

            if (!config.entityId && !config.pageVersionId)
                throw new Error("wiki entityId and pageVersionId required");

            var webpartEl = document.getElementById("webpart_" + config.webPartId);
            var wikiEl = webpartEl.getElementsByClassName("labkey-wiki")[0];
            config.dom = wikiEl;

            var dependencies = [ "tiny_mce/tiny_mce_src.js" ];
            LABKEY.requiresScript(dependencies, true, function () { inlineWikiEdit(config); }, this, false);
        },
        toggleTable : function(tocTable, expand, notify) {
            //Structure of a navtree table:
            //  Each row contains either a node in the tree, or the children of the node in the previous row
            //  For each, row we check the first TD for an anchor.  If it's there, this is a node row and
            //  we toggle it appropriately
            //  We then check the second TD for a table, if it's there we recurse
            //  Note taht some rows have neither (non-expandable nodes)

            if (tocTable)
            {
                if (0 == tocTable.childNodes.length)
                    return false;

                var topics = tocTable.childNodes.item(0);
                while (topics && topics.nodeName != "TBODY")
                { topics = topics.nextSibling; }

                if (!topics)
                    return false;

                for (var i = 0; i < topics.childNodes.length; i++)
                {
                    var topic = topics.childNodes.item(i);
                    if (topic.nodeName == "TR")
                    {
                        var firstTD = topic.childNodes.item(0);
                        while (firstTD && firstTD.nodeName != "TD")
                        { firstTD = firstTD.nextSibling; }
                        if (!firstTD) continue;

                        var link = firstTD.childNodes.item(0);
                        while (link && link.nodeName != "A")
                        { link = link.nextSibling; }
                        if (link != null)
                        {
                            //First we need to get the current state by looking at the img
                            var img = link.childNodes.item(0);
                            var expanded = (img.src.indexOf("minus.gif") != -1) ? true : false;
                            //now if we are expanded and want to collapse, or are collapsed and want to expand, do it
                            if ( (expanded && !expand) || (!expanded && expand))
                                LABKEY.Utils.toggleLink(link, notify);
                            else if (expanded && expand && notify && link.href != null)
                            {
                                //hack to handle the case where the node is expanded because it or a child is in view
                                //but the user has selected 'expand all'.  We still need to notify the server to ensure
                                //that the state is saved
                                LABKEY.Utils.notifyExpandCollapse(link.href, false);
                            }
                        }

                        var secondTD = firstTD.nextSibling;
                        while (secondTD && secondTD.nodeName != "TD")
                        { secondTD = secondTD.nextSibling; }
                        if (!secondTD) continue;
                        var table = secondTD.childNodes.item(0);
                        while (table && table.nodeName != "TABLE")
                        { table = table.nextSibling; }
                        if (table != null)
                        {
                            //if there's a table in the second td, recursively process it
                            LABKEY.wiki.internal.Wiki.toggleTable(table, expand, notify);
                        }
                    }
                }
            }
        }
    };
})();

