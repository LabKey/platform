// This file is intended to be loaded via the <includeScripts> on <ButtonBarOptions> for query metadata
var QWPDemoBar = new function() {
    return {
        confirm: new function() {
            return {
                render: function() {
                    // This flag is used by testButtonBarConfig(). See QWPDemo.js
                    window.testQWPRendered = true;
                }
            }
        }
    }
};