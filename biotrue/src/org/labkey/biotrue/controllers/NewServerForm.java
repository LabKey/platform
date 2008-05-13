/*
 * Copyright (c) 2007 LabKey Corporation
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

package org.labkey.biotrue.controllers;

import org.labkey.api.view.ViewForm;

public class NewServerForm extends ViewForm
{
    public String ff_name;
    public String ff_wsdlURL;
    public String ff_serviceNamespaceURI;
    public String ff_serviceLocalPart;
    public String ff_username;
    public String ff_password;
    public String ff_physicalRoot;

    public void setFf_name(String ff_name)
    {
        this.ff_name = ff_name;
    }

    public void setFf_wsdlURL(String ff_wsdlURL)
    {
        this.ff_wsdlURL = ff_wsdlURL;
    }

    public void setFf_serviceNamespaceURI(String ff_serviceNamespaceURI)
    {
        this.ff_serviceNamespaceURI = ff_serviceNamespaceURI;
    }

    public void setFf_serviceLocalPart(String ff_serviceLocalPart)
    {
        this.ff_serviceLocalPart = ff_serviceLocalPart;
    }

    public void setFf_username(String ff_username)
    {
        this.ff_username = ff_username;
    }

    public void setFf_password(String ff_password)
    {
        this.ff_password = ff_password;
    }

    public void setFf_physicalRoot(String ff_physicalRoot)
    {
        this.ff_physicalRoot = ff_physicalRoot;
    }
}
