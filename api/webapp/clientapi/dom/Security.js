/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.Security = new function(impl) {

    /**
     * @private
     */
    impl.Impersonation = new function() {

        // Helper for lazy initializing impersonation dependencies
        var _display = function(componentName)
        {
            LABKEY.requiresExt4ClientAPI(function() {
                LABKEY.requiresScript('Impersonate.js', function() {
                    Ext4.onReady(function() {
                        Ext4.create(componentName).show();
                    });
                });
            });
        };

        return {
            showImpersonateUser: function() { _display('LABKEY.Security.ImpersonateUser'); },
            showImpersonateGroup: function() { _display('LABKEY.Security.ImpersonateGroup'); },
            showImpersonateRole: function() { _display('LABKEY.Security.ImpersonateRoles'); }
        };
    };

    return impl;

}(LABKEY.Security);