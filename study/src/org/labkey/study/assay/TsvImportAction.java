/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.assay;

import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.study.actions.ImportAction;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.Pair;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Dec 30, 2010
 * Time: 12:58:09 PM
 */
@RequiresPermissionClass(DesignAssayPermission.class)
public class TsvImportAction extends ImportAction
{
    @Override
    protected ModelAndView createGWTView(Map<String, String> properties)
    {
        ImportForm form = getForm();
        AssayProvider provider = AssayService.get().getProvider(form.getProviderName());

        // don't import columns already included in the base results domain
        Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> template = provider.getAssayTemplate(getViewContext().getUser(), getContainer());

        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : template.getValue())
        {
            String uri = domainInfo.getKey().getTypeURI();
            Lsid uriLSID = new Lsid(uri);

            if (uriLSID.getNamespacePrefix() != null && uriLSID.getNamespacePrefix().startsWith(ExpProtocol.ASSAY_DOMAIN_DATA))
            {
                StringBuilder sb = new StringBuilder();
                String delim = "";
                for (DomainProperty prop : domainInfo.getKey().getProperties())
                {
                    sb.append(delim);
                    sb.append(prop.getName());

                    delim = ",";
                }
                properties.put("baseColumnNames", sb.toString());
            }
        }
        properties.put("showInferredColumns", "true");

        return super.createGWTView(properties);
    }
}
