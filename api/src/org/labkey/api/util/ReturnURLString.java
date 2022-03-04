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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewServlet;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * Use ReturnURLString in Spring form beans to sanitize and convert Strings into ActionURL or URLHelper when binding parameters.
 *
 * User: matthewb
 * Date: Nov 3, 2009
 */
public class ReturnURLString
{
    private static final Logger LOG = LogHelper.getLogger(ReturnURLString.class, "Validates that returnURL parameters are safe");

    private final @Nullable URLHelper _url;

    public static ReturnURLString EMPTY = new ReturnURLString("");

    /**
     * Constructing ReturnURLString manually isn't usually necessary.
     * Spring will call the {@link Converter} when binding parameter to the form bean.
     */
    public ReturnURLString(CharSequence s)
    {
        this(scrub(s));
    }

    public ReturnURLString(@Nullable URLHelper url)
    {
        _url = url;
    }

    @Nullable
    private static URLHelper scrub(CharSequence cs)
    {
        if (!ViewServlet.validChars(cs))
            throw new ConversionException("Invalid characters in string");

        final String s = Objects.toString(cs, null);
        if (StringUtils.isBlank(s))
            return null;

        URLHelper url = null;

        // If there are multiple values of the returnUrl HTTP parameter for this request (say, both GET and
        // POST variants), Spring will concatenate them before converting them to a ReturnURLString. Look
        // for identical values in the string and just grab the first
        String[] split = s.split(",");
        if (split.length > 1)
        {
            List<URLHelper> urls = Arrays.stream(split)
                    .map(ReturnURLString::createValidURL)
                    .filter(Objects::nonNull).toList();

            if (!urls.isEmpty())
            {
                URLHelper first = urls.get(0);
                String s0 = first.toString();

                // See if all the parts are identical
                if (urls.stream().allMatch(u -> s0.equals(u.toString())))
                {
                    url = first;
                }
            }
        }

        if (url == null)
        {
            url = createValidURL(s);
        }

        return url;
    }

    @Nullable
    private static URLHelper createValidURL(String s)
    {
        if (StringUtils.isBlank(s))
            return null;

        try
        {
            URLHelper url = new URLHelper(s);

            // silently ignore non http/https urls
            if (!url.isHttpURL())
                return null;

            if (!isAllowableHost(url))
                return null;

            return url;
        }
        catch (URISyntaxException e)
        {
            StringBuilder sb = new StringBuilder("Bad returnUrl");
            if (e.getIndex() >= 0 && e.getIndex() < s.length())
            {
                char c = s.charAt(e.getIndex());
                sb.append(" at char '").append(c).append("'");
            }
            LOG.debug(sb.append(": ").append(e.getMessage()));
            return null;
        }
    }

    // Issue 35896 - Disallow external redirects to URLs not on the whitelist
    private static boolean isAllowableHost(@NotNull URLHelper h)
    {
        return h.isAllowableHost();
    }

    public boolean isEmpty()
    {
        return _url == null;
    }


    @Override @NotNull
    public String toString()
    {
        if (_url == null)
            return "";
        return _url.getLocalURIString();
    }


    @Nullable
    public ActionURL getActionURL()
    {
        try
        {
            if (_url == null)
                return null;
            return new ActionURL(_url.getLocalURIString());
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
        return _url;
    }

    @Nullable
    public URLHelper getURLHelper(URLHelper defaultURL)
    {
        URLHelper url = getURLHelper();
        return url == null ? defaultURL : url;
    }

    public static class Converter implements org.apache.commons.beanutils.Converter
    {
        private static final ConvertHelper.DateFriendlyStringConverter _impl = new ConvertHelper.DateFriendlyStringConverter();

        @Override
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

            return new ReturnURLString(seq);
        }
    }
}
