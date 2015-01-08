Ext.define('LABKEY.app.Application', {
    extend: 'Ext.app.Application',

    defaultController: undefined,

    defaultLoginController: undefined,

    getService : function(name) {
        // For now, just ask controllers who have the 'isService' flag.
        var service = this.getController(name);

        if (service && service.isService === true) {
            return service;
        }

        return undefined;
    },

    resolveDefaultController : function() {
        return this.defaultController;
    }
});