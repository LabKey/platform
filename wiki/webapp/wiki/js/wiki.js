/*
 * Copyright (c) 2013 LabKey Corporation
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
        // labkey-wiki-inlineedit-active
    };

    InlineEditor.prototype.removeEditClass = function () {
        // labkey-wiki-inlineedit-inactive
    };

    InlineEditor.prototype.setStatus = function (msg) {
        // UNDONE: display the message somewhere
        console.log(msg);
    };

    InlineEditor.prototype.edit = function () {
        if (this.saving)
            return;

        this.addEditClass();

        // save original html
        this.originalValue = this.dom.innerHTML;

        // replace content with a textarea and buttons
        var html = "<form class='labkey-inline-editor' style='display:inline; margin:0; padding:0'>" +
                "<a class='labkey-button' name='save'><span>Save</span></a>" +
                "<a class='labkey-button' name='cancel'><span>Cancel</span></a>" +
                "<p>" +
                "<textarea id='" + this.editFieldId + "'>" +
                this.originalValue +
                "</textarea>" +
                "</form>";
        this.dom.innerHTML = html;

        // find the buttons and attach click events
        var buttons = this.dom.getElementsByClassName("labkey-button");
        var saveBtn = buttons[0];
        var cancelBtn = buttons[1];

        tinymce.dom.Event.add(saveBtn, 'click', this.onSaveClick, this);
        tinymce.dom.Event.add(cancelBtn, 'click', this.onCancelClick, this);

        // create the editor
        this.ed = new tinymce.Editor(this.editFieldId, {

            // General options
            mode: "none",
            theme: "advanced",
            plugins: "table, advlink, iespell, preview, media, searchreplace, print, paste, " +
                    "contextmenu, fullscreen, noneditable, inlinepopups, style, ", //pdw ",

            // tell tinymce not be be clever about URL conversion.  Dave added it to fix some bug.
            convert_urls: false,

            // Smaller button bar than found on regular wiki edit
            theme_advanced_buttons1 : "fontselect, fontsizeselect, " +
                    "|, bold, italic, underline, " +
                    "|, forecolor, backcolor, " +
                    "|, justifyleft, justifycenter, justifyright, " +
                    "|, bullist, numlist, " +
                    "|, outdent, indent, " +
                    "|, link, unlink, ",

            theme_advanced_buttons2 : null,
            theme_advanced_buttons3 : null,


            theme_advanced_toolbar_location : "top",
            theme_advanced_toolbar_align : "left",
            theme_advanced_statusbar_location : "bottom",
            theme_advanced_resizing : false,

            // this allows firefox and webkit users to see red highlighting of miss-spelled words, even
            // though they can't correct them -- the tiny_mce contextmenu plugin takes over the context menu
            gecko_spellcheck : true,

            content_css : LABKEY.contextPath + "/core/themeStylesheet.view",

            // PDW (third-party) Toggle Toolbars settings.  see http://www.neele.name/pdw_toggle_toolbars
            //pdw_toggle_on : 1,
            //pdw_toggle_toolbars : "2",

            // labkey specific
            //handle_event_callback : tinyMceHandleEvent,

            //init_instance_callback: onInitCallback
        });

        this.ed.render();
    };

    InlineEditor.prototype.cancel = function () {
        if (this.saving)
            return;

        this.removeEditClass();

        this.ed.remove();
        this.ed.destroy();

        // restore original html
        this.dom.innerHTML = this.originalValue;
    };

    InlineEditor.prototype.save = function () {
        if (this.saving)
            return;

        this.saving = true;
        this.setStatus("Saving...");

        var content = this.ed.getContent();

        // TODO: Use Ext Observable or something
        // invoke save callback
        this.config.save.apply(this.config.scope, [ this, content ]);
    };

    // clients are responsible for calling this upon successful save.
    InlineEditor.prototype.onSaveSuccess = function (ret) {
        if (ret.success)
        {
            this.removeEditClass();

            // BUGBUG: Use submitted content rather than using 'getContent' again.
            var content = this.ed.getContent();

            this.ed.remove();
            this.ed.destroy();

            // update modified html
            this.dom.innerHTML = content;
        }
        else
        {
            this.onSaveFailure(ret);
        }
    };

    // clients are responsible for calling this upon save failure.
    InlineEditor.prototype.onSaveFailure = function (errorInfo) {
        // UNDONE: display save error message to user
        console.warn("Failed to save wiki", errorInfo);
    };

    InlineEditor.prototype.onSaveClick = function () {
        if (this.saving)
            return;

        this.save();
    };

    InlineEditor.prototype.onCancelClick = function () {
        if (this.saving)
            return;

        if (this.ed.isDirty()) {
            // prompt "Are you sure?"
        }
        this.cancel();
    };


    // private
    // Initialize TinyMCE for inline editing on the given element id.
    function inlineEdit(config)
    {
        if (!config.dom && !config.id)
            throw new Error("dom node or id required");

        tinymceinit();
        var editor = new InlineEditor({
            dom: config.dom,
            id: config.id,
            save: function (inlineEditor, content) {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL("wiki", "saveWiki"),
                    method: 'POST',
                    success: LABKEY.Utils.getCallbackWrapper(editor.onSaveSuccess, editor),
                    failure: LABKEY.Utils.getCallbackWrapper(editor.onSaveFailure, editor, true),
                    jsonData: {
                        entityId: config.entityId,
                        pageVersionId: config.pageVersionId,
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

    LABKEY.Internal.Wiki = {
        createWebPartInlineEditor : function (config) {
            if (!config.entityId && !config.pageVersionId)
                throw new Error("wiki entityId and pageVersionId required");

            if (!config.webPartId)
                throw new Error("webPartId required");

            var webpartEl = document.getElementById("webpart_" + config.webPartId);
            var wikiEl = webpartEl.getElementsByClassName("labkey-wiki")[0];
            config.dom = wikiEl;

            var dependencies = [ "tiny_mce/tiny_mce_src.js" ];
            LABKEY.requiresScript(dependencies, true, function () { inlineEdit(config); }, this, true);
        }

    };
})();

