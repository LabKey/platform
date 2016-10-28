/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * Modifies tree.Column to take CSS font icons rather than image-based icons.
 *
 * @private
 */

Ext4.define('LABKEY.dataview.FaTreeColumn', {
    extend: 'Ext.tree.Column',
    alias: 'widget.fatreecolumn',

    // causes problems for font icons, since it sets the font size to 1 pixel, so innerCls is removed here
    innerCls: '',

    cellTpl: [
        '<tpl for="lines">',
            '<img src="{parent.blankUrl}" class="{parent.childCls} {parent.elbowCls}-img ',
            '{parent.elbowCls}-<tpl if=".">line<tpl else>empty</tpl>"/>',
        '</tpl>',
        '<img src="{blankUrl}" class="{childCls} {elbowCls}-img {elbowCls}',
            '<tpl if="isLast">-end</tpl><tpl if="expandable">-plus {expanderCls}</tpl>"/>',
        '<tpl if="checked !== null">',
            '<input type="button" role="checkbox" <tpl if="checked">aria-checked="true" </tpl>',
                ' class="{childCls} {checkboxCls}<tpl if="checked"> {checkboxCls}-checked</tpl>"/>',
        '</tpl>',

        '<tpl if="iconCls"><span<tpl else><img src="{blankUrl}"</tpl>',
        ' class="{childCls} {baseIconCls} ',
        // NOTE: The {baseIconCls}-leaf is removed from the default template as
        // it would display the default icon on top of the font icon
        '<tpl if="!leaf">{baseIconCls}-parent </tpl>',
        '{iconCls} dataview-icon" ',
        '<tpl if="iconCls">',
            '></span>', // end <span>
        '<tpl else>',
            '<tpl if="icon">',
                'style="background-image:url({icon})"',
            '</tpl>',
            '/>', // end <img>
        '</tpl>',

        '<tpl if="href">',
            '<a href="{href}" target="{hrefTarget}" class="{textCls} {childCls}">{value}</a>',
        '<tpl else>',
            '<span class="{textCls} {childCls}">{value}</span>',
        '</tpl>'
    ]
});