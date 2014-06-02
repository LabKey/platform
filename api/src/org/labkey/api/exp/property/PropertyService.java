/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.fhcrc.cpas.exp.xml.DomainDescriptorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.List;
import java.util.Map;

public class PropertyService
{
    static private Interface instance;

    static public Interface get()
    {
        return instance;
    }

    static public void setInstance(Interface impl)
    {
        instance = impl;
    }

    public interface Interface
    {
        IPropertyType getType(Container container, String domainURI);
        @Nullable Domain getDomain(Container container, String domainURI);
        @Nullable Domain getDomain(int domainId);
        List<DomainKind> getDomainKinds();
        /** Get all the domains in the same project as the specified container. They may not be in use in the container directly */
        List<? extends Domain> getDomains(Container container);
        /** Creates an in-memory Domain. It is not automatically saved to the database */
        @NotNull Domain createDomain(Container container, String typeURI, String name);

        /** Same as QueryService.get().getUserSchema(user, container, schemaName).getDomainURI(queryName) */
        @Nullable String getDomainURI(String schemaName, String queryName, Container container, User user);

        /**
         * Create a Domain from the DomainDescriptorType xmlbean.
         * @param context context in which LSIDs are resolved; may be null
         * @param xDomain the xmlbean containing the Domain description.
         */
        Pair<Domain, Map<DomainProperty, Object>> createDomain(Container container, @Nullable XarContext context, DomainDescriptorType xDomain) throws XarFormatException;
        Pair<Domain, Map<DomainProperty, Object>> createDomain(Container container, DomainDescriptorType xDomain);
        DomainKind getDomainKind(String typeURI);
        DomainKind getDomainKindByName(String name);
        void registerDomainKind(DomainKind type);

        /** register a property validator type */
        void registerValidatorKind(ValidatorKind kind);
        ValidatorKind getValidatorKind(String typeURI);
        IPropertyValidator createValidator(String typeURI);
        List<? extends IPropertyValidator> getPropertyValidators(PropertyDescriptor desc);
        void deleteValidatorsAndFormats(Container c, int propertyDescriptorId);
        void deleteValidatorsAndFormats(Container c);
        List<ConditionalFormat> getConditionalFormats(PropertyDescriptor desc);
        void saveConditionalFormats(User user, PropertyDescriptor pd, List<ConditionalFormat> formats);
    }
}
