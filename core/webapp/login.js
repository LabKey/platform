/*
 * Copyright (c) 2015-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

(function($) {
    var submitting = false, delay = false;
    var DELAY_MS = 500;

    // bind triggers, page init, etc
    function onReady() {
        // on document ready
        $('.signin-btn').click(authenticateUser);
        $('.loginSubmitButton').click(authenticateUser);
        init();
        getTermsOfUse();
        getOtherLoginMechanisms();
        toggleRegistrationLink();
    }

    function authenticateUser() {
        if (document.getElementById('remember').checked === true) {
            LABKEY.Utils.setCookie('email', encodeURIComponent(document.getElementById('email').value), false, 360);
        }
        else {
            LABKEY.Utils.deleteCookie('email', true);
            LABKEY.Utils.deleteCookie('email', false);
        }

        //the intent of this is to allow custom login pages to supply an element where id=returnUrl, which will always redirect the user after login
        var returnUrlElement = document.getElementById('returnUrl');

        setSubmitting(true, '');

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('login', 'loginApi.api', this.containerPath),
            method: 'POST',
            params: {
                remember: document.getElementById('remember').value,
                email: document.getElementById('email').value,
                password: document.getElementById('password').value,
                approvedTermsOfUse: document.getElementById('approvedTermsOfUse').checked,
                termsOfUseType: document.getElementById('termsOfUseType').value,
                returnUrl: returnUrlElement && returnUrlElement.value ? returnUrlElement.value : LABKEY.ActionURL.getParameter("returnUrl"),
                skipProfile: LABKEY.ActionURL.getParameter("skipProfile") || 0,
                urlhash: document.getElementById('urlhash').value
            },
            success: LABKEY.Utils.getCallbackWrapper(function(response) {
                setSubmitting(false, '');
                if (response && response.returnUrl) {
                    window.location = response.returnUrl;
                }
            }, this),
            failure: LABKEY.Utils.getCallbackWrapper(function(response) {
                setSubmitting(false, response ? response.exception : '');
                if (response && response.returnUrl) {
                    window.location = response.returnUrl;
                }
            }, this)
        });
    }

    function setSubmitting(isSubmitting, errorMsg) {
        // no-op if already submitting
        if (submitting && isSubmitting) {
            return;
        }
        // and if we are in the delay, re-queue this call to setSubmitting
        else if (delay) {
            setTimeout(function() {
                setSubmitting(isSubmitting, errorMsg);
            }, DELAY_MS);
            return;
        }

        submitting = isSubmitting;

        if (submitting) {
            _toggleDelay();
        }

        // update the button text for "Sign In"
        $('.signin-btn > span').html('Sign' + (submitting ? 'ing' : '') + ' In');

        // hide/show the Signing In message (if element is present)
        var msgEl = document.getElementsByClassName('signing-in-msg');
        if (msgEl && msgEl.length > 0) {
            msgEl[0].hidden = !submitting;
        }

        _setErrors(errorMsg);
    }

    function _toggleDelay() {
        delay = !delay;

        // enable the delay for a min set time to prevent flashing of buttons/spinner
        if (delay) {
            setTimeout(_toggleDelay, DELAY_MS);
        }
    }

    function _setErrors(text) {
        var el = document.getElementById('errors');
        if (el) {
            el.innerHTML = text;
        }
    }

    function acceptTermsOfUse() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('login', 'acceptTermsOfUseApi.api', this.containerPath),
            method: 'POST',
            params: {
                approvedTermsOfUse: document.getElementById('approvedTermsOfUse').checked,
                termsOfUseType: document.getElementById('termsOfUseType').value,
                returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
                skipProfile: LABKEY.ActionURL.getParameter("skipProfile") || 0,
                urlhash: document.getElementById('urlhash').value
            },
            success: function () {
                window.location = LABKEY.ActionURL.getParameter("returnUrl")
            },
            failure: LABKEY.Utils.getCallbackWrapper(function (response) {
                if (response && response.exception) {
                    _setErrors(response.exception);
                }
            }, this)
        });
    }

    function getTermsOfUse() {
        if (LABKEY.login.requiresTermsOfUse)
        {
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('login', 'getTermsOfUseApi.api', this.containerPath),
                method: 'POST',
                params: {
                    returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
                    skipProfile: LABKEY.ActionURL.getParameter("skipProfile") || 0,
                    urlhash: document.getElementById('urlhash').value
                },
                success: LABKEY.Utils.getCallbackWrapper(function (response)
                {
                    var termsContents = document.getElementsByClassName('termsOfUseContent');
                    var termsSections = document.getElementsByClassName('termsOfUseSection');
                    if (termsSections && termsSections.length >= 1 && termsContents && termsContents.length >= 1)
                    {
                        if (response.termsOfUseContent)
                        {
                            termsContents[0].innerHTML = response.termsOfUseContent;
                            termsSections[0].hidden = false;
                        }
                        else
                        {
                            termsSecitons[0].hidden = true;
                        }
                    }
                    if (document.getElementById('termsOfUseType') && response && response.termsOfUseType)
                    {
                        document.getElementById('termsOfUseType').value = response.termsOfUseType;
                    }
                }, this)
            });
        }
    }

    function getOtherLoginMechanisms() {
        if (LABKEY.login.hasOtherLoginMechanisms)
        {
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('login', 'getLoginMechanismsApi.api', this.containerPath),
                method: 'POST',
                params: {
                    returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
                    skipProfile: LABKEY.ActionURL.getParameter("skipProfile") || 0,
                    urlhash: document.getElementById('urlhash').value
                },
                success: LABKEY.Utils.getCallbackWrapper(function (response)
                {
                    var otherLoginContents = document.getElementsByClassName('otherLoginMechanismsContent');
                    var otherLoginSections = document.getElementsByClassName('otherLoginMechanismsSection');
                    if (otherLoginSections && otherLoginSections.length >= 1 && otherLoginContents && otherLoginContents.length >= 1)
                    {
                        if (response && response.otherLoginMechanismsContent)
                        {
                            otherLoginSections[0].hidden = false;
                            otherLoginContents[0].innerHTML = [
                                '<ul class="sso-list">',
                                response.otherLoginMechanismsContent,
                                '</ul>'
                            ].join('');
                        }
                        else
                        {
                            otherLoginSections[0].hidden = true;
                        }
                    }
                }, this)
            });
        }
    }


    function toggleRegistrationLink() {
        if (LABKEY.login.registrationEnabled)
        {
            var registrationSections = document.getElementsByClassName('registrationSection');
            if (registrationSections && registrationSections.length >= 1)
            {
                registrationSections[0].hidden = !LABKEY.login.registrationEnabled;
            }
        }
    }

    function init() {
        // Provide support for persisting the url hash through a login redirect
        if (window && window.location && window.location.hash) {
            var h = document.getElementById('urlhash');
            if (h) {
                h.value = window.location.hash;
            }
        }

        // Issue 22094: Clear password on login page after session timeout has been exceeded
        var timeout = 86400000;
        if (timeout > 0) {
            var passwordField = document.getElementById('password');

            // The function to do the clearing
            var clearPasswordField = function () {
                passwordField.value = '';
            };

            // Start the clock when the page loads
            var timer = setInterval(clearPasswordField, timeout);

            // Any time the value changes reset the clock
            var changeListener = function () {
                if (timer) {
                    clearInterval(timer);
                }
                timer = setInterval(clearPasswordField, timeout);
            };

            // Wire up the listener for changes to the password field
            passwordField.onchange = changeListener;
            passwordField.onkeypress = changeListener;
        }

        // examine cookies to determine if user wants the email pre-populated on form
        var h = document.getElementById('email');
        if (h && LABKEY.Utils.getCookie("email")) {
            h.value = decodeURIComponent(LABKEY.Utils.getCookie("email"));
        }
        h = document.getElementById('remember');
        h.checked = h && LABKEY.Utils.getCookie("email");

        // set autofocus to email field if email is blank otherwise set it to password field
        if (!document.getElementById('email').value) {
            document.getElementById('email').focus();
        }
        else {
            document.getElementById('password').focus();
        }
    }

    $(onReady);
})(jQuery);
