/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.defaults;

import org.labkey.api.view.ViewForm;
import org.labkey.api.util.HString;
import org.labkey.api.util.ReturnURLString;/*
 * User: brittp
 * Date: Mar 2, 2009
 * Time: 5:17:59 PM
 */

public class DomainIdForm extends ViewForm
{
    private int _domainId;
    private ReturnURLString _returnUrl;

    public int getDomainId()
    {
        return _domainId;
    }

    public void setDomainId(int domainId)
    {
        _domainId = domainId;
    }

    public ReturnURLString getReturnUrl()
    {
        return _returnUrl;
    }

    public void setReturnUrl(ReturnURLString returnUrl)
    {
        _returnUrl = returnUrl;
    }
}