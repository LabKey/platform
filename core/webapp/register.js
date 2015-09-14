/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function ($)
{
// bind triggers, page init, etc
    function onReady() {
        // on document ready
        $('.register-btn').click(registerUser);
    }

    function registerUser() {
        var email = document.getElementById('email').value;
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('login', 'registerUser.api', this.containerPath),
            method: 'POST',
            params: {
                email: email,
                emailConfirmation: document.getElementById('emailConfirmation').value,
                'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
            },
            success: LABKEY.Utils.getCallbackWrapper(function (response) {
                if (response.email)
                {
                    document.getElementById('errors').innerHTML = "";
                    document.getElementById('registration-content').innerHTML = "We have sent a registration email to " + LABKEY.Utils.encodeHtml(response.email) + ".  Please follow the link in the email to continue the registration process.";
                }
                else
                {
                    document.getElementById('errors').innerHTML = "Unknown error in creating account or sending email.  Please contact your administrator.";
                }
            }, this),
            failure: LABKEY.Utils.getCallbackWrapper(function (response) {
                if(document.getElementById('errors') && response && response.exception) {
                    document.getElementById('errors').innerHTML = response.exception;
                }
            }, this)
        });
    }

    $(onReady);
})(jQuery);