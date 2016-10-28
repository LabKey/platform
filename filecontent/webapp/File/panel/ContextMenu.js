/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('File.panel.ContextMenu', {

    extend: 'Ext.menu.Menu',

    alias: ['widget.fileContextMenu'],

    /**
     * Associates this menu with a specific file or folder.
     * @param {File.data.webdav.XMLResponse} file/folder item
     */
    setItem: function(item) {
        this.item = item;
    },

    /**
     * Gets the list associated with this menu
     * @return {File.data.webdav.XMLResponse}
     */
    getItem: function() {
        return this.item;
    }
});

