/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.query.browser.Button', {

    extend: 'Ext.button.Button',

    alias: 'widget.querybutton',

    closable: false,

    stacked: false,

    hideIcon: false,

    hideText: false,

    fontCls: undefined,

    stackedCls: undefined,

    padding: '2px 6px',

    renderTpl: [
        '<span id="{id}-btnEl" class="iconbtn">',
            '<tpl if="stacked">',
                '<span class="fa-stack fa-1x labkey-fa-stacked-wrapper">',
                    '<span class="fa {fontCls} fa-stack-2x"></span>',
                    '<span class="fa fa-stack-1x {stackedCls}"></span>',
                '</span>',
            '<tpl else>',
                '<span class="fa {fontCls}"></span>',
            '</tpl>',
            '<span id="{id}-btnInnerEl" class="iconbtn-label">',
                '<tpl if="text.length &gt; 0 && !hideText">',
                    '&nbsp;{text:htmlEncode}',
                '</tpl>',
            '</span>',
        '</span>'
    ],

    getTemplateArgs : function() {
        return {
            text: this.text || '',
            stacked: this.stacked === true,
            stackedCls: this.stackedCls,
            fontCls: this.fontCls,
            hideText: this.hideText === true
        };
    }
});