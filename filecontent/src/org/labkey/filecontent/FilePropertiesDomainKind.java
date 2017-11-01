/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: klum
 * Date: May 4, 2010
 * Time: 4:24:36 PM
 */
public class FilePropertiesDomainKind extends AbstractDomainKind
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
        Set<String> s = new CaseInsensitiveHashSet(RESERVED_FIELDS);

        for (ExpDataTable.Column col : ExpDataTable.Column.values())
            s.add(col.name());
        _reservedFieldSet = Collections.unmodifiableSet(s);
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
        return new ActionURL(FileContentController.DesignerAction.class, domain.getContainer());
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return _reservedFieldSet;
    }
}
