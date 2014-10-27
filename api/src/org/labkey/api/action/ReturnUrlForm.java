/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.URLException;

import java.net.URISyntaxException;


/**
* User: adam
* Date: Nov 22, 2007
* Time: 1:27:34 PM
*/
public class ReturnUrlForm
{
    private ReturnURLString _returnUrl;
    private String urlhash;

    // Generate a hidden form field to post a return URL with the standard name used by this form
    public static String generateHiddenFormField(URLHelper returnUrl)
    {
        return "<input type=\"hidden\" name=\"" + ActionURL.Param.returnUrl + "\" value=\"" + PageFlowUtil.filter(returnUrl) + "\">";
    }

    public ReturnURLString getReturnUrl()
    {
        return _returnUrl;
    }

    public void setUrlhash(String urlhash)
    {
        this.urlhash = urlhash;
    }

    public String getUrlhash()
    {
        return this.urlhash;
    }

    public void setReturnUrl(ReturnURLString returnUrl)
    {
        if (null == returnUrl || returnUrl.isEmpty())
        {
            _returnUrl = null;
            return;
        }

        // silently ignore non http urls
        if (!URLHelper.isHttpURL(returnUrl.getSource()))
            return;

        // If there are multiple values of the returnUrl HTTP parameter for this request (say, both GET and
        // POST variants), Spring will concatenate them before converting them to a ReturnURLString. Look
        // for identical values in the string and just grab the first
        String[] split = returnUrl.getSource().split(",");
        if (split.length > 1)
        {
            boolean identical = true;
            for (int i = 1; i < split.length; i++)
            {
                // See if all of the pieces are identical
                if (!split[i].equals(split[i - 1]))
                {
                    identical = false;
                    break;
                }
            }
            if (identical)
            {
                // We appear to have dupes, so just use one of them
                returnUrl = new ReturnURLString(split[0]);
            }
        }
        _returnUrl = returnUrl;
    }

    protected URLHelper getDefaultReturnURLHelper()
    {
        return null;
    }

    @Nullable
    public URLHelper getReturnURLHelper()
    {
        try
        {
            return (null == _returnUrl ? getDefaultReturnURLHelper() : new URLHelper(_returnUrl));
        }
        catch (URISyntaxException e)
        {
            throw new URLException(_returnUrl.getSource(), "returnUrl parameter", e);
        }
    }

    @Nullable
    public ActionURL getReturnActionURL()
    {
        try
        {
            // 17526
            return (null == _returnUrl ? null : new ActionURL(_returnUrl));
        }
        catch (IllegalArgumentException e)
        {
            throw new URLException(_returnUrl.getSource(), "returnUrl parameter", e);
        }
    }

    // Return the passed-in default URL if returnURL param is missing or unparseable
    public ActionURL getReturnActionURL(ActionURL defaultURL)
    {
        try
        {
            ActionURL url = getReturnActionURL();
            if (null != url)
                return url;
        }
        catch (URLException ignored) {}
        return defaultURL;
    }

    // Return the passed-in default URL if returnURL param is missing or unparseable
    public URLHelper getReturnURLHelper(URLHelper defaultURL)
    {
        try
        {
            URLHelper url = getReturnURLHelper();
            if (null != url)
                return url;
        }
        catch (URLException ignored) {}
        return defaultURL;
    }

    // when we convert code to use ReturnUrlForm we may leave behind bookmarks using "returnURL"
    @Deprecated
    public ReturnURLString getReturnURL()
    {
        return _returnUrl;
    }

    @Deprecated
    public void setReturnURL(ReturnURLString returnUrl)
    {
        setReturnUrl(returnUrl);
    }

    /** Applies the return URL from this form (if any) to the given URL */
    public void propagateReturnURL(ActionURL urlNeedingParameter)
    {
        if (getReturnUrl() != null)
        {
            urlNeedingParameter.addParameter(ActionURL.Param.returnUrl, getReturnUrl());
        }
    }
}
