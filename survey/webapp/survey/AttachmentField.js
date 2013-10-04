/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * The file input field that has an optional remove button
 */
Ext4.define('LABKEY.form.field.AttachmentFile', {
    extend  : 'Ext.form.field.File',
    alias   : 'widget.attachmentfile',
    childEls: [
        /**
         * @property {Ext.Element} removeButton
         * A reference to the element wrapping the remove button. Only set after the field has been rendered.
         */
        'removeButton'
    ],

    constructor : function(config) {

        Ext4.applyIf(config, {
            frame   : false,
            border  : false,
            hasRemoveButton : true,
            allowBlank: true,
            readOnly : true
        });

        this.callParent([config]);
    },

    /**
     * Gets the markup to be inserted into the subTplMarkup.
     */
    getTriggerMarkup: function() {
        var me = this, result;

        if (this.hasRemoveButton) {

            var removeBtn = Ext4.widget('button', Ext.apply({
                id  : me.id + '-removeButton',
                ui  : me.ui,
                disabled: me.disabled,
                text    : 'Remove',
                cls     : Ext4.baseCSSPrefix + 'form-file-btn',
                style: me.buttonOnly ? '' : 'margin-left:' + me.buttonMargin + 'px'
            }, me.buttonConfig));

            var removeBtnCfg = removeBtn.getRenderTree();
            result = '<td id="' + this.id + '-browseButtonWrap"></td><td style="width : 70px;">' + Ext4.DomHelper.markup(removeBtnCfg) + '</td>';
            removeBtn.destroy();
        }
        else
        {
            result = '<td id="' + this.id + '-browseButtonWrap"></td>';
        }
        return result;
    },

    onRender: function() {
        var me = this;

        me.callParent(arguments);

        if (me.removeButton) {

            // register a listener for the remove button
            me.mon(me.removeButton, {
                click: me.onRemoveButtonClick,
                scope: me
            });
        }
    },

    onRemoveButtonClick : function(e) {
        this.fireEvent('removebuttonclick', e, this);

    }
});

/**
 * Combines a file input field with the ability to add additional file input fields (and remove them)
 */
Ext4.define('LABKEY.form.field.Attachment', {
    extend: 'Ext.form.FieldContainer',
    alias: 'widget.attachmentfield',
    childEls: [
        /**
         * @property {Ext.Element} attachFile
         * A reference to the element wrapping the attach a file link. Only set after the field has been rendered.
         */
        'attachFile'
    ],

    constructor : function(config) {
        Ext4.applyIf(config, {
            frame   : false,
            border  : false,
            margin  : 10,
            width   : 800,
            allowBlank : true,
            multipleFiles : false, // if this component can support adding more than one attachment
            showFirstFile : false  // if allowing multiple, whether or not to show the first file field on init
        });

        this.callParent([config]);
    },

    initComponent : function() {

        var items = [];

        Ext4.define('LABKEY.data.Attachment', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'icon', type : 'string' },
                {name : 'name', type : 'string'},
                {name : 'downloadURL', type : 'string'},
                {name : 'atId', type : 'int'}
            ]
        });

        this.attachmentStore = Ext4.create('Ext.data.Store',{
            model : 'LABKEY.data.Attachment',
            data : this.attachment || {}
        });

        var attachmentTpl = new Ext4.XTemplate(
            '<table class="attachmentDoc" id="attach-{atId}">',
                '<tpl for=".">',
                    '<tr><td>&nbsp;<img src="{icon}" talt="icon"/></td>',
                    '<td>&nbsp;<a href="{downloadURL}">{name}</a></td>',
                    '<td>&nbsp;&nbsp;<a class="removelink" href="javascript:void(0);" title="remove file"><img src="{[LABKEY.contextPath + "/_images/partdeleted.gif"]}"></a></td></tr>',
                '</tpl>',
            '</table>&nbsp;'
        );

        var readOnlyAttachmentTpl = new Ext4.XTemplate(
            '<table class="attachmentDoc" id="attach-{atId}">',
                '<tpl for=".">',
                    '<tr><td><img src="{icon}" talt="icon"/></td>',
                    '<td>&nbsp;<a href="{downloadURL}">{name}</a></td>',
                '</tpl>',
            '</table>&nbsp;'
        );

        // dataview for existing attachments
        if (this.attachment)
        {
            items.push({
                xtype : 'dataview',
                store : this.attachmentStore,
                tpl : this.readOnly ? readOnlyAttachmentTpl : attachmentTpl,
                itemSelector :  'a.removelink',
                listeners : {
                    itemclick : {fn : this.removeAttachment, scope : this}
                }
            });
        }

        if (!this.readOnly)
        {
            if (this.multipleFiles)
            {
                if (this.showFirstFile)
                    items.push(this.createFileUploadComponent());

                items.push({
                    xtype : 'box',
                    id : this.id + '-attachFile',
                    html : '<a href="javascript:void(0);"><img src="' + LABKEY.contextPath + '/_images/paperclip.gif">&nbsp;&nbsp;Attach a file</a>'
                });
            }
            else if (!this.attachment)
            {
                items.push(this.createFileUploadComponent());
            }
        }

        this.formPanel = Ext4.create('Ext.form.Panel', {
            frame   : false,
            border  : false,
            items : items
        });

        this.items = [this.formPanel];
        this.callParent();
    },

    removeAttachment : function(cmp, rec, item, idx) {

        if (!this.readOnly)
        {
            this.attachmentStore.removeAt(idx);

            // if in single file mode, allow the user to add a new file
            if (!this.multipleFiles)
                this.onAttachFile();
        }
    },

    getFormPanel : function() {
        return this.formPanel;
    },

    onRender: function() {
        var me = this;

        me.callParent(arguments);

        if (me.attachFile) {

            // register a listener for the remove button
            me.mon(me.attachFile, {
                click: me.onAttachFile,
                scope: me
            });
        }
    },

    onAttachFile : function() {
        if (!this.readOnly)
        {
            // if we are showing multiple, add the new component right before the attach file paperclip
            var filesArr = Ext4.ComponentQuery.query('attachmentfile');
            var index = filesArr.length > 0 ? filesArr.length : 0;
            this.formPanel.insert(index, this.createFileUploadComponent());
        }
    },

    createFileUploadComponent : function() {

        var fileField = Ext4.create('LABKEY.form.field.AttachmentFile', {
            labelAlign : 'right',
            buttonText : 'Browse',
            width : this.fieldWidth,
            hasRemoveButton : this.multipleFiles,
            allowBlank : this.allowBlank,
            name : 'attachmentfile' + (Ext4.ComponentQuery.query('attachmentfile').length) // for selenium testing
        });
        fileField.on('removebuttonclick', function(e, cmp){this.formPanel.remove(cmp);}, this);

        return fileField;
    }
});


