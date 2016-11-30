/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.issue.query;

import com.google.common.collect.Sets;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainTemplate;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.issues.AbstractIssuesListDefDomainKind;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by klum on 4/6/2016.
 */
public class IssueDefDomainKind extends AbstractIssuesListDefDomainKind
{
    public static final String NAME = "IssueDefinition";
    private static final Set<PropertyStorageSpec> REQUIRED_PROPERTIES;
    private static final Set<String> RESERVED_NAMES;
    private static final Set<String> MANDATORY_PROPERTIES;
    private static final Set<PropertyStorageSpec.ForeignKey> FOREIGN_KEYS;

    public static final String ISSUE_LOOKUP_TEMPLATE_GROUP = "issue-lookup";
    public static final String PRIORITY_LOOKUP = "priority";
    public static final String TYPE_LOOKUP = "type";
    public static final String AREA_LOOKUP = "area";
    public static final String MILESTONE_LOOKUP = "milestone";
    public static final String RESOLUTION_LOOKUP = "resolution";
    private static final Set<String> OPTIONAL_NAMES = new HashSet<>();

    public static final String DEFAULT_ENTRY_TYPE_SINGULAR = "Issue";
    public static final String DEFAULT_ENTRY_TYPE_PLURAL = "Issues";

    static
    {
        // required property descriptors, initialized at domain creation time
        Set<PropertyStorageSpec> requiredProperties = Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec("Title", JdbcType.VARCHAR, 255).setNullable(false),
                new PropertyStorageSpec("Type", JdbcType.VARCHAR, 200),
                new PropertyStorageSpec("Area", JdbcType.VARCHAR, 200),
                new PropertyStorageSpec("NotifyList", JdbcType.VARCHAR),
                new PropertyStorageSpec("Priority", JdbcType.INTEGER).setNullable(false).setDefaultValue(3),
                new PropertyStorageSpec("Milestone", JdbcType.VARCHAR, 200)
        ));
        requiredProperties.addAll(BASE_REQUIRED_PROPERTIES);
        REQUIRED_PROPERTIES = Collections.unmodifiableSet(requiredProperties);
        OPTIONAL_NAMES.addAll(Arrays.asList("Type", "Area", "Priority", "Milestone"));

        FOREIGN_KEYS = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec.ForeignKey(PRIORITY_LOOKUP, "Lists", PRIORITY_LOOKUP, "value", null, false),
                new PropertyStorageSpec.ForeignKey(TYPE_LOOKUP, "Lists", TYPE_LOOKUP, "value", null, false),
                new PropertyStorageSpec.ForeignKey(AREA_LOOKUP, "Lists", AREA_LOOKUP, "value", null, false),
                new PropertyStorageSpec.ForeignKey(MILESTONE_LOOKUP, "Lists", MILESTONE_LOOKUP, "value", null, false),
                new PropertyStorageSpec.ForeignKey(RESOLUTION_LOOKUP, "Lists", RESOLUTION_LOOKUP, "value", null, false)
        )));

        RESERVED_NAMES = BASE_PROPERTIES.stream().map(PropertyStorageSpec::getName).collect(Collectors.toSet());
        RESERVED_NAMES.addAll(Arrays.asList("RowId", "Name"));
        RESERVED_NAMES.addAll(REQUIRED_PROPERTIES
                .stream()
                .filter(p -> !OPTIONAL_NAMES.contains(p.getName()))
                .map(PropertyStorageSpec::getName)
                .collect(Collectors.toSet()));

        // field names that are contained in the issues table that get's joined to the provisioned table
        RESERVED_NAMES.addAll(Arrays.asList("IssueId", "AssignedTo", "Modified", "ModifiedBy",
                                            "Created", "CreatedBy", "Resolved", "ResolvedBy", "Status", "BuildFound",
                                            "Tag", "Resolution", "Duplicate", "ClosedBy", "Closed", "LastIndexed", "IssueDefId"));

        MANDATORY_PROPERTIES = REQUIRED_PROPERTIES
                .stream()
                .filter(p -> !OPTIONAL_NAMES.contains(p.getName()))
                .map(PropertyStorageSpec::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public String getKindName()
    {
        return NAME;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return RESERVED_NAMES;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return INDEXES;
    }

    public Set<PropertyStorageSpec> getRequiredProperties()
    {
        return REQUIRED_PROPERTIES;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        return MANDATORY_PROPERTIES;
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container)
    {
        return FOREIGN_KEYS;
    }

    @Override
    public void deleteDomain(User user, Domain domain)
    {
        try
        {
            // delete any of the built in lookups that are created automatically
            deleteLookup(domain, user, PRIORITY_LOOKUP);
            deleteLookup(domain, user, AREA_LOOKUP);
            deleteLookup(domain, user, TYPE_LOOKUP);
            deleteLookup(domain, user, MILESTONE_LOOKUP);
            deleteLookup(domain, user, RESOLUTION_LOOKUP);
            domain.delete(user);
        }
        catch (DomainNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create the lists for any of the built in lookup fields (priority, type, area and milestone)
     */
    public void createLookupDomains(Container domainContainer, User user, String domainName) throws BatchValidationException
    {
        DomainTemplateGroup templateGroup = DomainTemplateGroup.get(domainContainer, ISSUE_LOOKUP_TEMPLATE_GROUP);

        if (templateGroup != null)
        {
            DomainTemplate priorityTemplate = templateGroup.getTemplate(PRIORITY_LOOKUP);
            priorityTemplate.createAndImport(domainContainer, user, getLookupTableName(domainName, PRIORITY_LOOKUP), true, true);

            DomainTemplate typeTemplate = templateGroup.getTemplate(TYPE_LOOKUP);
            typeTemplate.createAndImport(domainContainer, user, getLookupTableName(domainName, TYPE_LOOKUP), true, true);

            DomainTemplate areaTemplate = templateGroup.getTemplate(AREA_LOOKUP);
            areaTemplate.createAndImport(domainContainer, user, getLookupTableName(domainName, AREA_LOOKUP), true, false);

            DomainTemplate milestoneTemplate = templateGroup.getTemplate(MILESTONE_LOOKUP);
            milestoneTemplate.createAndImport(domainContainer, user, getLookupTableName(domainName, MILESTONE_LOOKUP), true, false);

            DomainTemplate resolutionTemplate = templateGroup.getTemplate(RESOLUTION_LOOKUP);
            resolutionTemplate.createAndImport(domainContainer, user, getLookupTableName(domainName, RESOLUTION_LOOKUP), true, true);
        }
        else
            throw new BatchValidationException(new ValidationException("Unable to load the issue-lookup domain template group. The issue module may not be enabled for this folder."));
    }

    @Override
    public List<FieldKey> getDefaultColumnNames()
    {
        List<FieldKey> columns = new ArrayList<>();
        columns.add(FieldKey.fromParts("IssueId"));
        columns.add(FieldKey.fromParts("Type"));
        columns.add(FieldKey.fromParts("Area"));
        columns.add(FieldKey.fromParts("Title"));
        columns.add(FieldKey.fromParts("AssignedTo"));
        columns.add(FieldKey.fromParts("Priority"));
        columns.add(FieldKey.fromParts("Status"));
        columns.add(FieldKey.fromParts("Milestone"));
        return columns;
    }

    @Override
    public String getDefaultSingularName()
    {
        return DEFAULT_ENTRY_TYPE_SINGULAR;
    }

    @Override
    public String getDefaultPluralName()
    {
        return DEFAULT_ENTRY_TYPE_PLURAL;
    }
}

