/*
 * Copyright (c) 2010-2026 LabKey Corporation
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
package org.labkey.filecontent;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.BaseAbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: klum
 * Date: May 4, 2010
 * Time: 4:24:36 PM
 */
public class FilePropertiesDomainKind extends BaseAbstractDomainKind
{
    private static final List<String> RESERVED_FIELDS = Collections.unmodifiableList(Arrays.asList(
            "name",
            "iconHref",
            "modified",
            "size",
            "createdBy",
            "description",
            "actionHref",
            "fileExt",
            "absolutePath",
            FileQueryUpdateService.KEY_COL_ID
    ));
    private static final Set<String> _reservedFieldSet;

    static {
        Set<String> s = new HashSet<>(RESERVED_FIELDS);

        s.addAll(Arrays.stream(ExpDataTable.Column.values()).map(Enum::name).collect(Collectors.toSet()));
        _reservedFieldSet = Collections.unmodifiableSet(DomainUtil.getNamesAndLabels(s));
    }

    @Override
    public String getKindName()
    {
        return FileContentServiceImpl.NAMESPACE_PREFIX;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return FileContentServiceImpl.NAMESPACE_PREFIX.equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return new SQLFragment("NULL");
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return null;
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain);
    }

    @Override
    protected @NotNull Set<String> getKindReservedPropertyNames(Domain domain, User user, boolean forCreate)
    {
        return _reservedFieldSet;
    }

    @Override
    public boolean showDefaultValueSettings()
    {
        return true;
    }

    @Override
    public DefaultValueType[] getDefaultValueOptions(Domain domain)
    {
        return new DefaultValueType[] { DefaultValueType.FIXED_EDITABLE, DefaultValueType.FIXED_NON_EDITABLE };
    }

    @Override
    public boolean supportsPhiLevel()
    {
        return ComplianceService.get().isComplianceSupported();
    }
}
