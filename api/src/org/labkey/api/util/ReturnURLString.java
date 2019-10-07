/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewServlet;

import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;


/**
 * Use ReturnURLString in Spring form beans to sanitize and convert Strings into ActionURL or URLHelper when binding parameters.
 *
 * User: matthewb
 * Date: Nov 3, 2009
 */
public class ReturnURLString
{
    private static final Logger LOG = Logger.getLogger(ReturnURLString.class);

    private final String _source;

    public static ReturnURLString EMPTY = new ReturnURLString("");

    /**
     * Constructing ReturnURLString manually isn't usually necessary.
     * Spring will call the {@link Converter} when binding parameter to the form bean.
     */
    public ReturnURLString(CharSequence s)
    {
        _source = s == null ? null : scrub(String.valueOf(s));
    }

    public ReturnURLString(URLHelper url)
    {
        _source = url.getLocalURIString();
    }

    private static String scrub(String s)
    {
        // silently ignore non http urls
        if (!URLHelper.isHttpURL(s))
            return null;

        // If there are multiple values of the returnUrl HTTP parameter for this request (say, both GET and
        // POST variants), Spring will concatenate them before converting them to a ReturnURLString. Look
        // for identical values in the string and just grab the first
        String[] split = s.split(",");
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
                s = split[0];
            }
        }

        if (!isAllowableHost(s))
        {
            return null;
        }

        return s;
    }

    private static boolean isAllowableHost(String url)
    {
        // Issue 35986 - Disallow URLs that have a full host name for security reasons
        try
        {
            URLHelper h = new URLHelper(url);
            // We have a returnURL that includes a server host name
            if (h.getHost() != null)
            {
                // Check if it matches the current server's preferred host name, per the base server URL setting
                String allowedHost = null;
                try
                {
                    allowedHost = new URL(AppProps.getInstance().getBaseServerUrl()).getHost();
                }
                catch (MalformedURLException ignored) {}

                if (!h.getHost().equalsIgnoreCase(allowedHost))
                {
                    // Server host name that doesn't match, log and possibly reject based on config
                    // Allow 'localhost' for servers in dev mode
                    boolean isConfigured = AppProps.getInstance().isDevMode() && "localhost".equalsIgnoreCase(h.getHost());

                    //look in the list of configured external redirect hosts
                    for (String externalRedirectHostURL : AppProps.getInstance().getExternalRedirectHosts())
                    {
                        if (StringUtils.isNotBlank(externalRedirectHostURL) && externalRedirectHostURL.equalsIgnoreCase(h.getHost()))
                            isConfigured = true;
                    }

                    if (!isConfigured)
                    {
                        String logMessageDetails = "returnURL value: " + url;
                        HttpServletRequest request = HttpView.currentRequest();
                        if (request != null)
                        {
                            logMessageDetails += " from URL: " + request.getRequestURL();
                            if (request.getHeader("Referer") != null)
                            {
                                logMessageDetails += " with referrer: " + request.getHeader("Referer");
                            }
                        }

                        LOG.warn("Rejected external host redirect " + logMessageDetails +
                                "\nPlease configure external redirect url host from: Admin gear --> Site --> Admin Console --> Settings --> External Redirect Hosts");
                        return false;
                    }
                    else
                    {
                        LOG.debug("Detected configured external host returnURL: " + url);
                    }
                }
            }
        }
        catch (URISyntaxException e)
        {
            return false;
        }
        return true;
    }

    public boolean isEmpty()
    {
        if (StringUtils.isEmpty(_source))
            return true;
        return null == getURLHelper();
    }


    public String getSource()
    {
        return _source;
    }

    @Override @NotNull
    public String toString()
    {
        try
        {
            if (StringUtils.isEmpty(_source))
                return "";
            new ActionURL(getSource());
            return _source;
        }
        catch (Exception x)
        {
            return "";
        }
    }


    @Nullable
    public ActionURL getActionURL()
    {
        try
        {
            if (StringUtils.isEmpty(_source))
                return null;
            return new ActionURL(_source);
        }
        catch (Exception x)
        {
            return null;
        }
    }

    @Nullable
    public ActionURL getActionURL(ActionURL defaultURL)
    {
        ActionURL url = getActionURL();
        return url == null ? defaultURL : url;
    }


    @Nullable
    public URLHelper getURLHelper()
    {
        try
        {
            if (StringUtils.isEmpty(_source))
                return null;
            return new URLHelper(_source);
        }
        catch (Exception x)
        {
            return null;
        }
    }

    @Nullable
    public URLHelper getURLHelper(URLHelper defaultURL)
    {
        URLHelper url = getURLHelper();
        return url == null ? defaultURL : url;
    }

    public static class Converter implements org.apache.commons.beanutils.Converter
    {
		private static ConvertHelper.DateFriendlyStringConverter _impl = new ConvertHelper.DateFriendlyStringConverter();

        public Object convert(Class type, Object value)
        {
            if (value == null)
                return ReturnURLString.EMPTY;
            if (value instanceof ReturnURLString)
                return value;
            CharSequence seq;
            if (value instanceof CharSequence)
                seq = (CharSequence)value;
            else
                seq = (String)_impl.convert(String.class, value);

            if (!ViewServlet.validChars(seq))
                throw new ConversionException("Invalid characters in string");

            return new ReturnURLString(seq);
        }
    }
}
