/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.settings;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.CustomTLDEnabler;
import org.apache.commons.validator.routines.UrlValidator;
import org.labkey.api.util.URLHelper;

import java.net.URISyntaxException;

/**
 * Created by adam on 11/24/2015.
 */
public class BaseServerProperties
{
    private final String _scheme;
    private final String _serverName;
    private final int _serverPort;

    static
    {
        CustomTLDEnabler.initialize();
    }

    // Validate and parse, returning properties
    public static BaseServerProperties parseAndValidate(String baseServerUrl) throws URISyntaxException
    {
        if (StringUtils.isEmpty(baseServerUrl))
            throw new URISyntaxException(baseServerUrl, "Empty URL is not valid");

        // Validate URL using Commons Validator
        if (!new UrlValidator(new String[] {"http", "https"}, UrlValidator.ALLOW_LOCAL_URLS).isValid(baseServerUrl))
            throw new URISyntaxException(baseServerUrl, "Invalid URL");

        // Divide up the parts and validate some more
        URLHelper url = new URLHelper(baseServerUrl);

        if (url.getParsedPath().size() > 0)
            throw new URISyntaxException(baseServerUrl, "Too many path parts");

        BaseServerProperties props = parse(baseServerUrl);

        if (props.getServerPort() == - 1)
            throw new URISyntaxException(baseServerUrl, "Invalid scheme");

        return props;
    }

    // Parse without validating
    public static BaseServerProperties parse(String baseServerUrl)
    {
        URLHelper url;
        try
        {
            url = new URLHelper(baseServerUrl);
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException("Invalid base server URL", e);
        }

        String scheme = url.getScheme();
        String serverName = url.getHost();

        int serverPort;

        if (url.getPort() != -1)
        {
            serverPort = url.getPort();
        }
        else
        {
            switch (scheme)
            {
                case "http":
                    serverPort = 80;
                    break;
                case "https":
                    serverPort = 443;
                    break;
                default:
                    serverPort = -1;
            }
        }

        return new BaseServerProperties(scheme, serverName, serverPort);
    }

    // Just validate
    public static void validate(String baseServerUrl) throws URISyntaxException
    {
        parseAndValidate(baseServerUrl);
    }

    public BaseServerProperties(String scheme, String serverName, int serverPort)
    {
        _scheme = scheme;
        _serverName = serverName;
        _serverPort = serverPort;
    }

    public String getScheme()
    {
        return _scheme;
    }

    public String getServerName()
    {
        return _serverName;
    }

    public int getServerPort()
    {
        return _serverPort;
    }
}
