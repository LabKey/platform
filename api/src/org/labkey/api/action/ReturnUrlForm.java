/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ActionURLException;
import org.labkey.api.util.ReturnURLString;

/**
* User: adam
* Date: Nov 22, 2007
* Time: 1:27:34 PM
*/
public class ReturnUrlForm
{
    public enum Params
    {
        returnUrl
    }

    private ReturnURLString _returnUrl;

    public ReturnURLString getReturnUrl()
    {
        return _returnUrl;
    }

    public void setReturnUrl(ReturnURLString returnUrl)
    {
        _returnUrl = returnUrl;
    }

    public ActionURL getReturnActionURL()
    {
        try
        {
            if (null == _returnUrl)
                return null;
            return new ActionURL(_returnUrl);
        }
        catch (IllegalArgumentException e)
        {
            throw new ActionURLException(_returnUrl.getSource(), "returnUrl parameter", e);
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
        catch (ActionURLException e)
        {
        }
        return defaultURL;
    }
}
