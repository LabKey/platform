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
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocol.AssayDomainType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.fhcrc.cpas.exp.xml.DomainDescriptorType;

import java.util.Map;
import java.util.List;
import java.util.HashMap;

/**
 * User: kevink
 * Date: Dec 10, 2008 2:20:38 PM
 */
public class ModuleAssayProvider extends AbstractAssayProvider
{

    private String name;
    private Map<AssayDomainType, DomainDescriptorType> domainsDescriptors = new HashMap<ExpProtocol.AssayDomainType, DomainDescriptorType>();

    public ModuleAssayProvider(String name)
    {
        super(name + "Protocol", name + "Run", TsvDataHandler.DATA_TYPE);
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

    public ExpData getDataForDataRow(Object dataRowId)
    {
        return null;
    }

    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return null;
    }

    public TableInfo createDataTable(UserSchema schema, String alias, ExpProtocol protocol)
    {
        return null;
    }

    public FieldKey getParticipantIDFieldKey()
    {
        return null;
    }

    public FieldKey getVisitIDFieldKey(Container targetStudy)
    {
        return null;
    }

    public FieldKey getRunIdFieldKeyFromDataRow()
    {
        return null;
    }

    public FieldKey getDataRowIdFieldKey()
    {
        return null;
    }

    public FieldKey getSpecimenIDFieldKey()
    {
        return null;
    }

    public ActionURL publish(User user, ExpProtocol protocol, Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors)
    {
        return null;
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return null;
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

    protected Domain createDataDomain(Container c, User user)
    {
        return createDomain(c, user, AssayDomainType.Data);
    }

    @Override
    public List<Domain> createDefaultDomains(Container c, User user)
    {
        List<Domain> domains = super.createDefaultDomains(c, user);

        Domain dataDomain = createDataDomain(c, user);
        if (dataDomain != null)
            domains.add(dataDomain);

        return domains;
    }
}
