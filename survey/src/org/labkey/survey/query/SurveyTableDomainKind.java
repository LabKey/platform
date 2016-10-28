/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.survey.query;

import org.labkey.api.data.Container;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.module.SimpleModule;
import org.labkey.api.query.SimpleTableDomainKind;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;

/**
 * User: klum
 * Date: 1/14/13
 */
public class SurveyTableDomainKind extends SimpleTableDomainKind
{
    public static final String NAME = "SurveyTable";

    @Override
    public String getKindName()
    {
        return NAME;
    }

    public static Container getDomainContainer(Container c)
    {
        if (c != null)
            return c.getProject();

        return c;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return super.urlCreateDefinition(schemaName, queryName, getDomainContainer(container), user);
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user, TemplateInfo templateInfo)
    {
        return super.createDomain(domain, arguments, getDomainContainer(container), user, templateInfo);
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        String namespacePrefix = lsid.getNamespacePrefix();
        String objectId = lsid.getObjectId();
        if (namespacePrefix == null || objectId == null)
        {
            return null;
        }
        return (namespacePrefix.equalsIgnoreCase(SimpleModule.NAMESPACE_PREFIX + "-survey") && objectId.equalsIgnoreCase("users")) ? Handler.Priority.MEDIUM : null;
    }


}
