/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

package org.labkey.api.exp.property;

import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.List;
import java.util.Map;
import java.util.Set;

abstract public class DomainKind
{
    abstract public String getKindName();
    abstract public String getTypeLabel(Domain domain);
    abstract public boolean isDomainType(String domainURI);
    abstract public SQLFragment sqlObjectIdsInDomain(Domain domain);

    /**
     * Create a DomainURI for a Domain that may or may not exist yet.
     */
    abstract public String generateDomainURI(String schemaName, String queryName, Container container, User user);

    // UNUSED: remove?
    abstract public Pair<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers);
    abstract public ActionURL urlShowData(Domain domain);
    abstract public ActionURL urlEditDefinition(Domain domain);
    abstract public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user);

    abstract public boolean canCreateDefinition(User user, Container container);

    abstract public boolean canEditDefinition(User user, Domain domain);

    // Override to customize the nav trail on shared pages like edit domain
    abstract public void appendNavTrail(NavTree root, Container c, User user);

    // Do any special handling before a PropertyDescriptor is deleted -- do nothing by default
    abstract public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd);

    /**
     * Return the set of names that should not be allowed for properties. E.g.
     * the names of columns from the hard table underlying this type
     * @return set of strings containing the names. This will be compared ignoring case
     */
    abstract public Set<String> getReservedPropertyNames(Domain domain);

    // CONSIDER: have DomainKind supply and IDomainInstance or similiar
    // so that it can hold instance data (e.g. a DatasetDefinition)

    /**
     * Create a Domain appropriate for this DomainKind.
     * @param domain The domain design.
     * @param arguments Any extra arguments.
     * @param container Container
     * @param user User
     * @return The newly created Domain.
     */
    abstract public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user);

    /**
     * Update a Domain definition appropriate for this DomainKind.
     * @param original The original domain definition.
     * @param update The updated domain definition.
     * @param container Container
     * @param user User
     * @return A list of errors collected during the update.
     */
    abstract public List<String> updateDomain(GWTDomain original, GWTDomain update, Container container, User user);

    abstract public Set<PropertyStorageSpec> getBaseProperties();

}
