/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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