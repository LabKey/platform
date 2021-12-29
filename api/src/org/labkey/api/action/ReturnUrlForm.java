/*
 * Copyright (c) 2008-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.action;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;

/**
 * Simple form bean that includes a returnUrl property, typically used to send the user back to the page where they initiated the action.
 * Often subclassed to add usage-specific additional properties.
 * User: adam
 * Date: Nov 22, 2007
 */
public class ReturnUrlForm
{
    public static final Logger LOG = LogManager.getLogger(ReturnUrlForm.class);

    private ReturnURLString _returnUrl;
    private ReturnURLString _cancelUrl;
    private ReturnURLString _successUrl;
    private String _urlhash;

    /**
     * Generate a hidden form field to post a return URL with the standard name used by this form.
     * @see org.labkey.api.jsp.JspBase#generateReturnUrlFormField(ReturnUrlForm)
     */
    public static HtmlString generateHiddenFormField(URLHelper returnUrl)
    {
        return HtmlString.unsafe("<input type=\"hidden\" name=\"" + ActionURL.Param.returnUrl + "\" value=\"" + PageFlowUtil.filter(returnUrl) + "\">");
    }

    /**
     * Should not typically be used, this getter/setter pair is for Spring parameter binding.
     * Use {@link .getReturnActionURL()} instead.
     */
    @Nullable
    public String getReturnUrl()
    {
        return null == _returnUrl ? null : StringUtils.trimToNull(_returnUrl.toString());
    }

    /**
     * Should not typically be used, this getter/setter pair is for Spring parameter binding.
     */
    public void setReturnUrl(String s)
    {
        setReturnUrl(new ReturnURLString(s));
    }

    @JsonIgnore // Otherwise on deserialization, there's a conflict with the overloaded setter
    private void setReturnUrl(ReturnURLString returnUrl)
    {
        _returnUrl = (null == returnUrl || returnUrl.isEmpty()) ? null : returnUrl;
    }

    @SuppressWarnings("unused")
    public void setUrlhash(String urlhash)
    {
        _urlhash = urlhash;
    }

    public String getUrlhash()
    {
        return _urlhash;
    }

    // TODO: Remove this. There is only one override in List
    @Deprecated
    protected URLHelper getDefaultReturnURLHelper()
    {
        return null;
    }

    @Nullable
    public URLHelper getReturnURLHelper()
    {
        return firstOf(
                _returnUrl != null ? _returnUrl.getURLHelper() : null,
                getDefaultReturnURLHelper());
    }

    @Nullable
    public ActionURL getReturnActionURL()
    {
        return null == _returnUrl ? null : _returnUrl.getActionURL();
    }

    /**
     * Get the first non-null URL from <code>returnUrl</code> or the <code>defaultURL</code> parameter.
     */
    public ActionURL getReturnActionURL(ActionURL defaultURL)
    {
        return firstOf(getReturnActionURL(), defaultURL);
    }

    /**
     * Get the first non-null URL from <code>returnUrl</code> or the <code>defaultURL</code> parameter.
     */
    public URLHelper getReturnURLHelper(URLHelper defaultURL)
    {
        return firstOf(getReturnURLHelper(), defaultURL);
    }

    /**
     * Not typically used, this getter/setter pair is for Spring parameter binding.
     */
    public void setCancelUrl(ReturnURLString cancelUrl)
    {
        _cancelUrl = cancelUrl;
    }

    /**
     * Not typically used, this getter/setter pair is for Spring parameter binding.
     * Use {@link .getCancelActionURL()} instead.
     */
    public ReturnURLString getCancelUrl()
    {
        return _cancelUrl;
    }

    /**
     * Get the first non-null URL from <code>cancelUrl</code> or <code>returnUrl</code>.
     */
    public ActionURL getCancelActionURL()
    {
        return firstOf(
                _cancelUrl != null ? _cancelUrl.getActionURL() : null,
                getReturnActionURL());
    }

    /**
     * Get the first non-null URL from <code>cancelUrl</code>, <code>returnUrl</code>, or the <code>defaultURL</code> parameter.
     */
    public ActionURL getCancelActionURL(ActionURL defaultURL)
    {
        return firstOf(
                _cancelUrl != null ? _cancelUrl.getActionURL() : null,
                getReturnActionURL(),
                defaultURL);
    }

    /**
     * Not typically used, this getter/setter pair is for Spring parameter binding.
     */
    public void setSuccessUrl(ReturnURLString successUrl)
    {
        _successUrl = successUrl;
    }

    /**
     * Not typically used, this getter/setter pair is for Spring parameter binding.
     * Use {@link .getSuccessActionURL()} instead.
     */
    public ReturnURLString getSuccessUrl()
    {
        return _successUrl;
    }

    /**
     * Get the first non-null URL from <code>successUrl</code> or <code>returnUrl</code>.
     */
    public ActionURL getSuccessActionURL()
    {
        return firstOf(
                _successUrl != null ? _successUrl.getActionURL() : null,
                getReturnActionURL());
    }

    /**
     * Get the first non-null URL from <code>successUrl</code>, <code>returnUrl</code>, or the <code>defaultURL</code> parameter.
     */
    public ActionURL getSuccessActionURL(ActionURL defaultURL)
    {
        return firstOf(
                _successUrl != null ? _successUrl.getActionURL() : null,
                getReturnActionURL(),
                defaultURL);
    }

    // when we convert code to use ReturnUrlForm we may leave behind bookmarks using "returnURL"
    @Deprecated
    public ReturnURLString getReturnURL()
    {
        throwBadParam();
        return _returnUrl;
    }

    @Deprecated
    public void setReturnURL(ReturnURLString returnUrl)
    {
        throwBadParam();
        setReturnUrl(returnUrl);
    }

    /** Applies the return URL from this form (if any) to the given URL */
    public void propagateReturnURL(ActionURL urlNeedingParameter)
    {
        if (getReturnURLHelper() != null)
        {
            urlNeedingParameter.addReturnURL(getReturnURLHelper());
        }
    }


    private static <X> X firstOf(X... urls)
    {
        for (X url : urls)
            if (url != null)
                return url;

        return null;
    }

    /**
     * Report a bad returnUrl usage.
     * Some views don't show Spring binding errors from the thrown exception so
     * log an ERROR message to the console and let the test framework report it.
     */
    public static void throwBadParam()
    {
        throwBadParam("returnURL");
    }

    /**
     * Report a bad returnUrl usage.
     * Some views don't show Spring binding errors from the thrown exception so
     * log an ERROR message to the console and let the test framework report it.
     */
    public static void throwBadParam(String badParamName)
    {
        StringBuilder msg = new StringBuilder("Use 'returnUrl' instead of '").append(badParamName).append("'");
        if (HttpView.hasCurrentView())
            msg.append(" from URL: ").append(HttpView.currentContext().getRequest().getRequestURI());
        LOG.error(msg.toString());
        if (AppProps.getInstance().isDevMode())
            throw new UnsupportedOperationException(msg.toString());
    }

}
