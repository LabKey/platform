(function($) {

 // bind triggers, page init, etc
 function onReady() {
  // on document ready
  $('.auth-btn').click(acceptTermsOfUse);
  $('.signin-btn').click(authenticateUser);
  init();
  isAgreeOnly();
  getTermsOfUse();
  getOtherLoginMechanisms();
 }

 function authenticateUser()
 {
  if (document.getElementById('remember').checked == true)
  {
   LABKEY.Utils.setCookie('email', document.getElementById('email').value, true, 360);
  }
  else
  {
   LABKEY.Utils.deleteCookie('email', true);
  }
  LABKEY.Ajax.request({
   url: LABKEY.ActionURL.buildURL('login', 'loginAPI.api', this.containerPath),
   method: 'POST',
   params: {
    remember: document.getElementById('remember').value,
    email: document.getElementById('email').value,
    password: document.getElementById('password').value,
    approvedTermsOfUse: document.getElementById('approvedTermsOfUse').checked,
    termsOfUseType: document.getElementById('termsOfUseType').value,
    returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
    urlHash: document.getElementById('urlhash'),
    'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
   },
   success: function() { window.location = LABKEY.ActionURL.getParameter("returnUrl") },
   failure: LABKEY.Utils.getCallbackWrapper(function(response) {
    document.getElementById('errors').innerHTML = response.exception;
   }, this)
  });
 }


 function acceptTermsOfUse()
 {
  LABKEY.Ajax.request({
   url: LABKEY.ActionURL.buildURL('login', 'acceptTermsOfUseApi.api', this.containerPath),
   method: 'POST',
   params: {
    approvedTermsOfUse: document.getElementById('approvedTermsOfUse').checked,
    termsOfUseType: document.getElementById('termsOfUseType').value,
    returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
    urlHash: document.getElementById('urlhash'),
    'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
   },
   success: function() { window.location = LABKEY.ActionURL.getParameter("returnUrl") },
   failure: LABKEY.Utils.getCallbackWrapper(function(response) {
    document.getElementById('errors').innerHTML = response.exception;
   }, this)
  });
 }

 function getTermsOfUse()
 {
  LABKEY.Ajax.request({
   url: LABKEY.ActionURL.buildURL('login', 'getTermsOfUseAPI.api', this.containerPath),
   method: 'POST',
   params: {
    returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
    urlHash: document.getElementById('urlhash'),
    'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
   },
   success: LABKEY.Utils.getCallbackWrapper(function(response) {
    document.getElementsByClassName('termsOfUseContent')[0].innerHTML = response.termsOfUseContent;
    if (response.termsOfUseContent == null)
    {
     document.getElementsByClassName('termsOfUseSection')[0].hidden = true;
    }
    document.getElementById('termsOfUseType').value = response.termsOfUseType;
   }, this)
  });
 }

 function getOtherLoginMechanisms()
 {
  LABKEY.Ajax.request({
   url: LABKEY.ActionURL.buildURL('login', 'getLoginMechanismsAPI.api', this.containerPath),
   method: 'POST',
   params: {
    returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
    urlHash: document.getElementById('urlhash'),
    'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
   },
   success: LABKEY.Utils.getCallbackWrapper(function(response) {
    document.getElementsByClassName('otherLoginMechanismsContent')[0].innerHTML = response.otherLoginMechanismsContent;
    if (response.otherLoginMechanismsContent == null)
    {
     document.getElementsByClassName('otherLoginMechanismsSection')[0].hidden = true;
    }
   }, this)
  });
 }

 function isAgreeOnly()
 {
  LABKEY.Ajax.request({
   url: LABKEY.ActionURL.buildURL('login', 'isAgreeOnlyAPI.api', this.containerPath),
   method: 'POST',
   params: {
    returnUrl: LABKEY.ActionURL.getParameter("returnUrl"),
    urlHash: document.getElementById('urlhash'),
    'X-LABKEY-CSRF': document.getElementById('X-LABKEY-CSRF')
   },
   success: LABKEY.Utils.getCallbackWrapper(function(response) {
    if (response.isAgreeOnly == true)
    {
     document.getElementsByClassName('auth-credentials-form')[0].hidden = true;
     document.getElementsByClassName('auth-credentials-submit')[0].hidden = true;
     document.getElementsByClassName('agree-only-submit')[0].hidden = false;
    }
    else
    {
     document.getElementsByClassName('auth-credentials-form')[0].hidden = false;
     document.getElementsByClassName('auth-credentials-submit')[0].hidden = false;
     document.getElementsByClassName('agree-only-submit')[0].hidden = true;
    }
   }, this)
  });
 }

 function init()
 {
  // Provide support for persisting the url hash through a login redirect
  if (window && window.location && window.location.hash) { var h = document.getElementById('urlhash'); if (h) { h.value = window.location.hash;}};

  // Issue 22094: Clear password on login page after session timeout has been exceeded
  var timeout = 86400000;
  if (timeout > 0) {
   var passwordField = document.getElementById('password');

   // The function to do the clearing
   var clearPasswordField = function ()
   {
    passwordField.value = '';
   };

   // Start the clock when the page loads
   var timer = setInterval(clearPasswordField, timeout);

   // Any time the value changes reset the clock
   var changeListener = function ()
   {
    if (timer)
    {
     clearInterval(timer);
    }
    timer = setInterval(clearPasswordField, timeout);
   };

   // Wire up the listener for changes to the password field
   passwordField.onchange = changeListener;
   passwordField.onkeypress = changeListener;
  }

  // examine cookies to determine if user wants the email prepoluated on form
  var h = document.getElementById('email'); if (h && LABKEY.Utils.getCookie("email")) { h.value = LABKEY.Utils.getCookie("email"); };
  h = document.getElementById('remember'); if (h && LABKEY.Utils.getCookie("email")) { h.checked = true; } else { h.checked = false; };
 }

 $(onReady);

})(jQuery);
