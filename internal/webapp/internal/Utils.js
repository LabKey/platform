LABKEY.Internal = LABKEY.Internal || {};

LABKEY.Internal.Utils = new function(){
    return {
        /**
         * A utility that allows client-side code to log messages to the server's log.  This can
         * be used to log any information, but was originally designed to allow client-side code
         * to log errors to a central location.  The user must have ReadPermission in the current container
         * to log this error.
         * @param config The config object, which supports the following options:
         * <ul>
         * <li>message: The message to log</li>
         * <li>level: The error level, either ERROR, WARN, INFO or DEBUG.  Defaults to ERROR</li>
         * <li>includeContext: if true, the following will automatially be appended to the message string: LabKey user, URL of the current page, browser and platform
         * </ul>
         */
        logToServer: function(config){
            if(!config || !config.message){
                alert('ERROR: Must provide a message to log');
            }

            if(config.includeContext){
                config.message += '\n' + [
                    "User: " + LABKEY.Security.currentUser.email,
                    "ReferrerURL: " + document.URL,
                    ("Browser: " + (navigator && navigator.userAgent || "Unknown")),
                    ("Platform: " + (navigator && navigator.platform  || "Unknown"))
                ].join('\n');
            }

            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('admin', 'log'),
                params: {
                    message: config.message,
                    level: config.level
                },
                method : 'POST',
                scope: this,
                failure: function(response){
                    console.error('Unable to log message to server');
                    console.error(response);
                }
            });
        }
    }
}