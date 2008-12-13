/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.study.assay;

import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayPublishKey;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.RunDataTable;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.AssayDataDetailsAction;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocol.AssayDomainType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HtmlView;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.fhcrc.cpas.exp.xml.DomainDescriptorType;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.io.*;

/**
 * User: kevink
 * Date: Dec 10, 2008 2:20:38 PM
 */
public class ModuleAssayProvider extends TsvAssayProvider
{

    private String name;
    private Map<AssayDomainType, DomainDescriptorType> domainsDescriptors = new HashMap<AssayDomainType, DomainDescriptorType>();
    private Map<AssayDomainType, File> viewFiles = new HashMap<AssayDomainType, File>();

    public ModuleAssayProvider(String name)
    {
        super(name + "Protocol", name + "Run");
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public boolean canPublish()
    {
        return false;
    }

    @Override
    public boolean isPlateBased()
    {
        return false;
    }

    public void addDomain(AssayDomainType domainType, DomainDescriptorType xDomain)
    {
        domainsDescriptors.put(domainType, xDomain);
    }

    protected Domain createDomain(Container c, User user, AssayDomainType domainType)
    {
        DomainDescriptorType xDomain = domainsDescriptors.get(domainType);
        if (xDomain != null)
        {
            return PropertyService.get().createDomain(c, xDomain);
        }
        return null;
    }

    @Override
    protected Domain createUploadSetDomain(Container c, User user)
    {
        Domain domain = createDomain(c, user, AssayDomainType.Batch);
        if (domain != null)
            return domain;
        return super.createUploadSetDomain(c, user);
    }

    @Override
    protected Domain createRunDomain(Container c, User user)
    {
        Domain domain = createDomain(c, user, AssayDomainType.Run);
        if (domain != null)
            return domain;
        return super.createRunDomain(c, user);
    }

    @Override
    protected Domain createDataDomain(Container c, User user)
    {
        Domain domain = createDomain(c, user, AssayDomainType.Data);
        if (domain != null)
            return domain;
        return super.createDataDomain(c, user);
    }

    @Override
    public TableInfo createDataTable(UserSchema schema, String alias, ExpProtocol protocol)
    {
        RunDataTable table = (RunDataTable)super.createDataTable(schema, alias, protocol);
        if (table == null)
            return null;
        File dataDetailsView = viewFiles.get(AssayDomainType.Data);
        if (dataDetailsView != null)
        {
            ActionURL dataDetailsURL = new ActionURL(AssayDataDetailsAction.class, schema.getContainer());
            dataDetailsURL.addParameter("rowId", protocol.getRowId());
            Map<String, String> params = new HashMap<String, String>();
            params.put("dataRowId", "ObjectId"); // map from parameter->column?
            table.addDetailsURL(new DetailsURL(dataDetailsURL, params));
        }
        return table;
    }

    public void addView(AssayDomainType domainType, File viewFile)
    {
        viewFiles.put(domainType, viewFile);
    }

    @Override
    public ModelAndView createRunDataView(ViewContext context, ExpProtocol protocol)
    {
        File runDataView = viewFiles.get(AssayDomainType.Run);
        if (runDataView == null || !runDataView.canRead())
            return null;

        return new HtmlView(PageFlowUtil.getFileContentsAsString(runDataView));
    }

    @Override
    public ModelAndView createDataDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object objectId)
    {
        File dataDetailsView = viewFiles.get(AssayDomainType.Data);
        if (dataDetailsView == null || !dataDetailsView.canRead())
            HttpView.throwNotFound("assay data details view not found");
        // module.getResourceStreamIfChanged(path, prevous);

        return new HtmlView(PageFlowUtil.getFileContentsAsString(dataDetailsView));
    }

}
