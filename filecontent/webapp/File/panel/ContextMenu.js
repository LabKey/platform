
Ext4.define('File.data.panel.ContextMenu', {

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

