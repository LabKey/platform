/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.issue;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainTemplate;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.Group;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.issue.model.CustomColumn;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.KeywordManager;
import org.labkey.issue.query.IssueDefDomainKind;
import org.labkey.issue.query.IssuesListDefTable;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 6/2/2016.
 */
public class IssuesUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(IssuesUpgradeCode.class);
    private static final String DEFAULT_SITE_ISSUE_LIST_NAME = "siteIssues";
    private static final String DEFAULT_FOLDER_ISSUE_LIST_NAME = "folderIssues";

    /**
     * Invoked by issues-16.10-16.20.sql
     *
     * Upgrade to migrate existing issues to provisioned tables and to migrate legacy issues
     * admin setting to the domain and new tables
     */
    @DeferredUpgrade
    public void upgradeIssuesTables(final ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        _log.info("Analyzing site-wide configuration to generate the issue list migration plan");
        MultiValuedMap<Container, IssueMigrationPlan> migrationPlans = generateMigrationPlan();
        User upgradeUser = new LimitedUser(UserManager.getGuestUser(), new int[0], Collections.singleton(RoleManager.getRole(SiteAdminRole.class)), false);
        ObjectFactory<LegacyIssue> factory = ObjectFactory.Registry.getFactory(LegacyIssue.class);

        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            // create the issue list definitions and new domains
            for (Map.Entry<Container, Collection<IssueMigrationPlan>> entry : migrationPlans.asMap().entrySet())
            {
                Container defContainer = entry.getKey();

                for (IssueMigrationPlan plan : entry.getValue())
                {
                    IssueListDef existingDef = IssueManager.getIssueListDef(defContainer, plan.getIssueDefName());
                    if (existingDef == null)
                    {
                        _log.info("Creating the issue def domain named : " + plan.getIssueDefName() + " in folder : " + defContainer.getPath());
                        IssueListDef def = createNewIssueListDef(upgradeUser, plan.getIssueDefName(), defContainer);

                        // initialize the domain with the saved settings
                        configureIssueDomain(upgradeUser, def, plan);

                        // now create individual issue lists that share this configuration (if in a different container)
                        for (Container folder : plan.getFolders())
                        {
                            if (!defContainer.equals(folder))
                            {
                                createNewIssueListDef(upgradeUser, plan.getIssueDefName(), folder);
                            }

                            // migrate issue records specific to this folder
                            _log.info("Populating provisioned table in folder : " + folder.getPath());
                            populateProvisionedTable(folder, upgradeUser, factory, plan);

                            _log.info("Migrating custom views for issues query in folder : " + folder.getPath());
                            migrateCustomViews(folder, upgradeUser, plan);
                            migrateIssueProperties(folder, upgradeUser, plan);
                        }
                    }
                    else
                        _log.error("An issue definition of name : " + plan.getIssueDefName() + " already exists in folder : " + defContainer.getPath());
                }
            }
            transaction.commit();
        }
        catch (Exception e)
        {
            _log.error(e.getMessage());
        }
    }

    /**
     * Populate the new provisioned tables with existing data from issues.issues. We want to write directly
     * to the hard tables instead of using the query update service to avoid double writing to the legacy
     * issues table.
     */
    void populateProvisionedTable(Container c, User user, ObjectFactory<LegacyIssue> factory, IssueMigrationPlan plan) throws SQLException
    {
        IssueListDef issueListDef = IssueManager.getIssueListDef(c, plan.getIssueDefName());
        if (issueListDef != null)
        {
            TableInfo table = issueListDef.createTable(user);
            if (table != null)
            {
                // add any custom parameters
                Map<String, String> columnNameMap = new LinkedCaseInsensitiveMap<>();
                plan.getConfig().getColumnConfiguration().getCustomColumns()
                    .stream()
                    .filter(cc -> cc.getName().startsWith("string") || cc.getName().startsWith("int"))
                    .forEach(cc -> {
                        String colName = ColumnInfo.legalNameFromName(cc.getCaption());
                        columnNameMap.put(cc.getName(), colName);
                    });
                List<Map<String, Object>> rows = new ArrayList<>();

                new TableSelector(IssuesSchema.getInstance().getTableInfoIssues(), SimpleFilter.createContainerFilter(c), null).forEachBatch(batch -> {
                    rows.clear();

                    for (LegacyIssue issue : batch)
                    {
                        Map<String, Object> row = new CaseInsensitiveHashMap<>();
                        factory.toMap(issue, row);

                        row.putIfAbsent("AssignedTo", 0);

                        for (Map.Entry<String, String> entry : columnNameMap.entrySet())
                        {
                            if (row.get(entry.getKey()) != null)
                                row.put(entry.getValue(), row.get(entry.getKey()));
                        }
                        rows.add(row);
                    }

                    for (Map<String, Object> row : rows)
                    {
                        Table.insert(user, table, row);
                    }
                }, LegacyIssue.class, 1000);

                // need to set the issue def id in the issues table to be able to determine the issue definition for
                // each individual issue record
                SQLFragment sql = new SQLFragment("UPDATE ").append(IssuesSchema.getInstance().getTableInfoIssues(), "").
                        append(" SET IssueDefId = ? WHERE Container = ?");
                sql.addAll(issueListDef.getRowId(), c);
                new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(sql);
            }
        }
    }

    void migrateCustomViews(Container c, User user, IssueMigrationPlan plan) throws SQLException
    {
        // only need to change for non-default issue def names
        TableInfo tinfoCustomView = DbSchema.get("query", DbSchemaType.Module).getTable("CustomView");

        if (tinfoCustomView != null)
        {
            SQLFragment sql = new SQLFragment("UPDATE ").append(tinfoCustomView, "").
                    append(" SET QueryName = ? WHERE QueryName = ? AND Container = ?");
            sql.addAll(plan.getIssueDefName(), "Issues", c);

            new SqlExecutor(tinfoCustomView.getSchema()).execute(sql);
        }
        else
            _log.error("Unable to get the table info for query.CustomView");
    }

    void migrateIssueProperties(Container c, User user, IssueMigrationPlan plan)
    {
        Container adminContainer = IssueManager.getInheritFromOrCurrentContainer(c);
        IssueManager.EntryTypeNames typeNames = IssueManager.getEntryTypeNames(adminContainer);
        Group assignedToGroup = IssueManager.getAssignedToGroup(c);
        User defaultUser = IssueManager.getDefaultAssignedToUser(c);
        Sort.SortDirection sortDirection = IssueManager.getCommentSortDirection(adminContainer);

        IssueManager.saveEntryTypeNames(c, plan.getIssueDefName(), typeNames);
        IssueManager.saveAssignedToGroup(c, plan.getIssueDefName(), assignedToGroup);
        IssueManager.saveDefaultAssignedToUser(c, plan.getIssueDefName(), defaultUser);
        IssueManager.saveCommentSortDirection(c, plan.getIssueDefName(), sortDirection);
    }

    IssueListDef createNewIssueListDef(User user, String name, Container container)
    {
        // ensure the issue module is enabled for this folder
        Module issueModule = ModuleLoader.getInstance().getModule(IssuesModule.NAME);
        Set<Module> activeModules = container.getActiveModules();
        if (!activeModules.contains(issueModule))
        {
            Set<Module> newActiveModules = new HashSet<>();
            newActiveModules.addAll(activeModules);
            newActiveModules.add(issueModule);

            container.setActiveModules(newActiveModules);
        }
        IssueListDef def = new IssueListDef();
        def.setName(name);
        def.setLabel(name);
        def.setKind(IssueDefDomainKind.NAME);
        def.beforeInsert(user, container.getId());

        return def.save(user);
    }

    /**
     * Initializes the issue definition domain, adding any custom field configurations and legacy picklists
     */
    void configureIssueDomain(User user, IssueListDef def, IssueMigrationPlan plan) throws Exception
    {
        IssueAdminConfig config = plan.getConfig();
        Domain domain = def.getDomain(user);
        if (domain != null)
        {
            if (!config.isEmpty())
            {
                Map<String, Collection<KeywordManager.Keyword>> keywordMap = config.getKeywordMap();
                Set<String> keywordSet = new CaseInsensitiveHashSet(keywordMap.keySet());
                Set<String> requiredFields = new CaseInsensitiveHashSet();
                Map<String, String> defaultValueMap = new HashMap<>();

                if (config.getRequiredFields() != null)
                {
                    for (String field : config.getRequiredFields().split(";"))
                        requiredFields.add(field.trim());
                }

                for (CustomColumn col : config.getColumnConfiguration().getCustomColumns())
                {
                    DomainProperty prop = domain.getPropertyByName(col.getName());
                    String colName = col.getName();

                    if (prop == null)
                    {
                        prop = domain.addProperty();
                        colName = ColumnInfo.legalNameFromName(col.getCaption());

                        prop.setName(colName);
                        prop.setPropertyURI(domain.getTypeURI() + "#" + colName);

                        if (col.getName().toLowerCase().contains("string"))
                            prop.setType(PropertyService.get().getType(domain.getContainer(), PropertyType.STRING.getXmlName()));
                        else
                            prop.setType(PropertyService.get().getType(domain.getContainer(), PropertyType.INTEGER.getXmlName()));
                    }
                    prop.setLabel(col.getCaption());

                    if (requiredFields.contains(col.getName()))
                    {
                        if (!hasNullValues(plan.getFolders(), col.getName()))
                            prop.setRequired(true);
                        else
                            _log.warn("Unable to mark field: '" + col.getName() + "' as required due to existing NULL values.");
                    }
                    if (!col.getPermission().equals(ReadPermission.class))
                        prop.setPhi(PHI.Limited);

                    if (col.isPickList())
                    {
                        // need to create the lookup
                        Lookup lookup = prop.getLookup();
                        if (lookup == null)
                        {
                            // create the lookup as a list using the preexisting domain templates
                            DomainTemplateGroup templateGroup = DomainTemplateGroup.get(domain.getContainer(), IssueDefDomainKind.ISSUE_LOOKUP_TEMPLATE_GROUP);
                            String lookupTableName = IssueDefDomainKind.getLookupListName(domain.getName(), colName);
                            if (templateGroup != null)
                            {
                                String templateName = prop.getPropertyType().equals(PropertyType.STRING) ? IssueDefDomainKind.AREA_LOOKUP : IssueDefDomainKind.PRIORITY_LOOKUP;
                                DomainTemplate template = templateGroup.getTemplate(templateName);
                                template.createAndImport(domain.getContainer(), user, lookupTableName, true, false);
                            }
                            lookup = new Lookup(domain.getContainer(), "lists", lookupTableName);
                            prop.setLookup(lookup);
                        }

                        // populate the lookup
                        if (lookup != null && keywordMap.containsKey(col.getName()))
                        {
                            keywordSet.remove(col.getName());
                            String defaultValue = populateLookupTable(domain.getContainer(), user, prop, lookup, keywordMap.get(col.getName()));
                            if (defaultValue != null)
                                defaultValueMap.put(prop.getName(), defaultValue);
                        }
                    }
                }

                // standard fields (type, area, priority, milestone...) may have keywords without having a custom column entry
                // deal with them in this pass
                for (String colName : keywordSet)
                {
                    DomainProperty prop = domain.getPropertyByName(colName);
                    if (requiredFields.contains(prop.getName()))
                    {
                        if (!hasNullValues(plan.getFolders(), colName))
                            prop.setRequired(true);
                        else
                            _log.warn("Unable to mark field: '" + colName + "' as required due to existing NULL values.");
                    }

                    if (prop != null && prop.getLookup() != null)
                    {
                        String defaultValue = populateLookupTable(domain.getContainer(), user, prop, prop.getLookup(), keywordMap.get(colName));
                        if (defaultValue != null)
                            defaultValueMap.put(prop.getName(), defaultValue);
                    }
                }
                domain.save(user);

                // set any default values
                Map<DomainProperty, Object> defaultValues = new HashMap<>();
                for (Map.Entry<String, String> entry : defaultValueMap.entrySet())
                {
                    DomainProperty prop = domain.getPropertyByName(entry.getKey());

                    if (prop != null)
                    {
                        prop.setDefaultValueTypeEnum(DefaultValueType.FIXED_EDITABLE);
                        defaultValues.put(prop, entry.getValue());
                    }
                }

                if (!defaultValues.isEmpty())
                    DefaultValueService.get().setDefaultValues(domain.getContainer(), defaultValues);
            }
        }
    }

    private boolean hasNullValues(Collection<Container> folders, String colName)
    {
        if (!folders.isEmpty())
        {
            SqlDialect dialect = CoreSchema.getInstance().getSqlDialect();
            SQLFragment sql = new SQLFragment("SELECT * FROM ").append(IssuesSchema.getInstance().getTableInfoIssues(), "x").append(" WHERE ");

            sql.append("x.").append(dialect.makeLegalIdentifier(colName.toLowerCase())).append(" IS NULL");
            sql.append(" AND x.container ");
            dialect.appendInClauseSql(sql, folders);

            return new SqlSelector(IssuesSchema.getInstance().getSchema(), sql).exists();
        }
        return false;
    }

    /**
     * Populates the lookup table with the contents of the legacy keywords
     * @return the default value for the keyword set (if any)
     */
    @Nullable
    private String populateLookupTable(Container c, User user, DomainProperty prop, Lookup lookup, Collection<KeywordManager.Keyword> keywords) throws Exception
    {
        UserSchema userSchema = QueryService.get().getUserSchema(user, lookup.getContainer(), lookup.getSchemaName());
        TableInfo table = userSchema.getTable(lookup.getQueryName());
        String defaultValue = null;

        if (table != null)
        {
            QueryUpdateService qus = table.getUpdateService();
            if (qus != null)
            {
                // delete any existing rows
                final List<Map<String, Object>> rowsToDelete = new ArrayList<>();
                new TableSelector(table).forEachMap(rowsToDelete::add);
                if (!rowsToDelete.isEmpty())
                {
                    qus.deleteRows(user, c, rowsToDelete, null, null);
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                BatchValidationException errors = new BatchValidationException();

                for (KeywordManager.Keyword keyword : keywords)
                {
                    rows.add(new CaseInsensitiveHashMap<>(Collections.singletonMap("value", keyword.getKeyword())));
                    if (keyword.isDefault())
                        defaultValue = keyword.getKeyword();
                }
                qus.insertRows(user, c, rows, errors, null, null);
            }
        }
        return defaultValue;
    }

    /**
     * Generate the migration plan for every container that needs to be upgraded.
     * @return
     */
    private MultiValuedMap<Container, IssueMigrationPlan> generateMigrationPlan()
    {
        // get the set of folders that will need to be mapped to new IssueListDefs
        final Set<Container> issueListSet = new HashSet<>();

        SQLFragment sql = new SQLFragment("SELECT DISTINCT(Container) FROM ").append(IssuesSchema.getInstance().getTableInfoIssues(), "");
        new SqlSelector(IssuesSchema.getInstance().getSchema(), sql).forEach(container -> {

            Container c = ContainerManager.getForId(container);
            if (c != null)
            {
                issueListSet.add(c);
            }
            else
            {
                _log.warn("Unable to resolve container for id : " + container);
            }
        }, String.class);

        // create the maps of issue list to admin settings both for lists that do and do not inherit
        // settings

        // create the map of issue admin configurations that are shared, grouped by project
        Map<Container, MultiValuedMap<IssueAdminConfig, Container>> settingsProjectMap = new HashMap<>();
        MultiValuedMap<IssueAdminConfig, Container> sharedSettingsMap = new ArrayListValuedHashMap<>();
        Map<Container, IssueAdminConfig> allConfigs = new HashMap<>();
        Set<Container> inheritedContainers = new HashSet<>();

        for (Container c : issueListSet)
        {
            IssueAdminConfig config = new IssueAdminConfig();
            Container project = c.getProject();

            CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(c);
            config.setColumnConfiguration(ccc);
            config.setRequiredFields(IssueManager.getRequiredIssueFields(c, null));

            // get the picklists for any of the custom keywords
            if (ccc != null)
            {
                config.setKeywordMap(getKeywordMap(ccc, c));
            }
            Container inheritContainer = IssueManager.getInheritFromContainer(c);
            if (inheritContainer == null)
            {
                if (!settingsProjectMap.containsKey(project))
                {
                    settingsProjectMap.put(project, new ArrayListValuedHashMap<>());
                }
                config.setIgnoreKeywords(false);
                settingsProjectMap.get(project).put(config, c);
                allConfigs.put(c, config);
            }
            else
            {
                config.setIgnoreRequiredFields(true);
                sharedSettingsMap.put(config, c);
                inheritedContainers.add(inheritContainer);
                allConfigs.put(c, config);
            }
        }

        // need to ensure that inherited configs are moved to the shared setting map so they get scoped correctly
        for (Container c : inheritedContainers)
        {
            IssueAdminConfig inheritConfig = allConfigs.get(c);
            if (inheritConfig != null)
            {
                MultiValuedMap<IssueAdminConfig, Container> mvm = settingsProjectMap.get(c.getProject());
                if (mvm != null && mvm.containsKey(inheritConfig))
                {
                    mvm.removeMapping(inheritConfig, c);
                    inheritConfig.setIgnoreRequiredFields(true);
                    sharedSettingsMap.put(inheritConfig, c);
                }
            }
        }

        // create the new issue definitions
        MultiValuedMap<Container, IssueMigrationPlan> migrationPlans = new ArrayListValuedHashMap<>();

        for (Map.Entry<Container, MultiValuedMap<IssueAdminConfig, Container>> project : settingsProjectMap.entrySet())
        {
            if (project.getValue().size() == 1)
            {
                // for a single issues list in the project, create the issue definition at the project root
                IssueAdminConfig config = project.getValue().keySet().iterator().next();
                Collection<Container> folders = project.getValue().values();

                migrationPlans.put(project.getKey(), new IssueMigrationPlan(config, folders, null));
            }
            else
            {
                for (Map.Entry<IssueAdminConfig, Collection<Container>> entry : project.getValue().asMap().entrySet())
                {
                    if (entry.getValue().size() > 1)
                    {
                        // for multiple folder dependencies, create at the project level
                        migrationPlans.put(project.getKey(), new IssueMigrationPlan(entry.getKey(), entry.getValue(), null));
                    }
                    else
                    {
                        // single folder dependency, create at the folder level
                        Container folder = entry.getValue().iterator().next();
                        migrationPlans.put(folder, new IssueMigrationPlan(entry.getKey(), entry.getValue(), getNameFromFolder(folder.getName())));
                    }
                }
            }
        }

        Set<String> siteNames = new HashSet<>();
        // now process the configurations that are configured to be inherited
        for (Map.Entry<IssueAdminConfig, Collection<Container>> entry : sharedSettingsMap.asMap().entrySet())
        {
            if (entry.getValue().size() == 1)
            {
                // for single container dependencies, create at the folder level
                Container folder = entry.getValue().iterator().next();
                migrationPlans.put(folder, new IssueMigrationPlan(entry.getKey(), entry.getValue(), getNameFromFolder(folder.getName())));
            }
            else if (entry.getValue().size() > 1)
            {
                Set<Container> projects = new HashSet<>();
                for (Container c : entry.getValue())
                {
                    Container project = c.getProject();
                    if (!projects.contains(project))
                        projects.add(project);
                }

                if (projects.size() > 1)
                {
                    // for multiple project dependencies, create at the site level in the shared folder
                    String name = createUniqueName(DEFAULT_SITE_ISSUE_LIST_NAME, siteNames);
                    siteNames.add(name);
                    migrationPlans.put(ContainerManager.getSharedContainer(), new IssueMigrationPlan(entry.getKey(), entry.getValue(), name));
                }
                else
                {
                    // if all dependencies are within a single project, create at the project level
                    migrationPlans.put(projects.iterator().next(), new IssueMigrationPlan(entry.getKey(), entry.getValue(), null));
                }
            }
        }

        // need to fixup issue list definition names at the project level so they are unique, but we will allow the first non-default
        // configuration to use the default issue name of 'issues'
        for (Map.Entry<Container, Collection<IssueMigrationPlan>> entry : migrationPlans.asMap().entrySet())
        {
            if (entry.getKey().isProject() && !ContainerManager.getSharedContainer().equals(entry.getKey()))
            {
                if (entry.getValue().size() == 1)
                {
                    entry.getValue().iterator().next().setIssueDefName(IssueListDef.DEFAULT_ISSUE_LIST_NAME);
                }
                else
                {
                    Set<String> projectNames = new HashSet<>();
                    for (IssueMigrationPlan plan : entry.getValue())
                    {
                        boolean hasRequiredFields = plan.getConfig().getRequiredFields() != null;
                        boolean hasCustomColumns = !plan.getConfig().getColumnConfiguration().getCustomColumns().isEmpty();

                        if (plan.getIssueDefName() == null && (hasRequiredFields | hasCustomColumns))
                        {
                            plan.setIssueDefName(IssueListDef.DEFAULT_ISSUE_LIST_NAME);
                            projectNames.add(IssueListDef.DEFAULT_ISSUE_LIST_NAME);
                            break;
                        }
                    }

                    for (IssueMigrationPlan plan : entry.getValue())
                    {
                        if (plan.getIssueDefName() == null)
                        {
                            String name = createUniqueName(IssueListDef.DEFAULT_ISSUE_LIST_NAME, projectNames);
                            plan.setIssueDefName(name);
                            projectNames.add(name);
                        }
                    }
                }
            }
        }
        return migrationPlans;
    }

    private Map<String, Collection<KeywordManager.Keyword>>  getKeywordMap(@NotNull CustomColumnConfiguration ccc, Container c)
    {
        Map<String, Collection<KeywordManager.Keyword>> keywordMap = new CaseInsensitiveHashMap<>();

        for (CustomColumn cc : ccc.getCustomColumns())
        {
            if (cc.isPickList())
            {
                ColumnType type = ColumnTypeEnum.forName(cc.getName());
                if (type != null)
                {
                    keywordMap.put(cc.getName(), KeywordManager.getKeywords(c, type));
                }
            }
        }

        // check for any keywords for any of the standard columns
        for (String colName : Arrays.asList("type", "area", "priority", "milestone", "resolution"))
        {
            Collection<KeywordManager.Keyword> keywords = KeywordManager.getKeywords(c, ColumnTypeEnum.forName(colName));
            if (!keywords.isEmpty())
                keywordMap.put(colName, keywords);
        }

        return keywordMap;
    }

    private String createUniqueName(String base, Set<String> names)
    {
        String name = base;
        int i = 1;
        while (names.contains(name))
        {
            name = base + i++;
        }
        return name;
    }

    /**
     * Generates an issue list name from a folder name.
     *
     * Issue : 29290 - The name of the issue list name is used to construct the default lookup lists name (area, priority, resolution, etc).
     * Lists names are limited to 64 characters, to be conservative if the folder name exceeds 30 characters we will use
     * the default : 'folderissues' name otherwise we allow the issue list name to be the name of the folder.
     */
    private String getNameFromFolder(String folderName)
    {
        if (folderName.length() > 30)
            return DEFAULT_FOLDER_ISSUE_LIST_NAME;
        else
            return folderName;
    }
    /**
     * Repair any lookup values that may not have gotten migrated properly
     * @param context
     */
    public void repairIssueLookups(final ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try
        {
            _log.info("Checking for issue list lookups that need to be repaired");
            User upgradeUser = new LimitedUser(UserManager.getGuestUser(), new int[0], Collections.singleton(RoleManager.getRole(SiteAdminRole.class)), false);
            for (IssueListDef issueListDef : IssueManager.getIssueListDefs(null))
            {
                Container c = ContainerManager.getForId(issueListDef.getContainerId());
                // only inspecting non-inherited configurations since this represented the problem scenario
                if (IssueManager.getInheritFromContainer(c) == null)
                {
                    _log.info("Evaluating issue list definition : " + issueListDef.getName() + " in folder : " + c.getPath());

                    CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(c);
                    if (ccc != null)
                    {
                        Map<String, Collection<KeywordManager.Keyword>> keywordMap = getKeywordMap(ccc, c);

                        // verify the automatically created lookups have the complete set of values
                        Domain domain = issueListDef.getDomain(upgradeUser);
                        if (domain != null)
                        {
                            for (Map.Entry<String, Collection<KeywordManager.Keyword>> entry : keywordMap.entrySet())
                            {
                                String lookupTableName = IssueDefDomainKind.getLookupListName(domain.getName(), entry.getKey());
                                ensureLookupValues(domain.getContainer(), upgradeUser, lookupTableName, entry.getValue());
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            _log.error(e.getMessage());
        }
    }

    private void ensureLookupValues(Container c, User user, String tableName, Collection<KeywordManager.Keyword> keywords) throws Exception
    {
        UserSchema userSchema = QueryService.get().getUserSchema(user, c, "lists");
        TableInfo table = userSchema.getTable(tableName);

        if (table != null)
        {
            QueryUpdateService qus = table.getUpdateService();
            if (qus != null)
            {
                Set<String> existingValues = new CaseInsensitiveHashSet();
                new TableSelector(table).forEachMap(row -> existingValues.add(String.valueOf(row.get("value"))));

                List<Map<String, Object>> rows = new ArrayList<>();
                BatchValidationException errors = new BatchValidationException();

                for (KeywordManager.Keyword keyword : keywords)
                {
                    if (!existingValues.contains(keyword.getKeyword()))
                    {
                        _log.info("Lookup table : " + tableName + " is missing legacy value of : " + keyword.getKeyword());
                        rows.add(new CaseInsensitiveHashMap<>(Collections.singletonMap("value", keyword.getKeyword())));
                    }
                }
                if (!rows.isEmpty())
                    qus.insertRows(user, c, rows, errors, null, null);
            }
        }
    }

    static class IssueAdminConfig
    {
        private CustomColumnConfiguration _columnConfiguration;
        private String _requiredFields;
        private Map<String, Collection<KeywordManager.Keyword>> _keywordMap;
        private boolean _ignoreKeywords = true;
        private boolean _ignoreRequiredFields = false;

        public boolean isEmpty()
        {
            return _columnConfiguration.getCustomColumns().isEmpty() &&
                    _requiredFields == null &&
                    _keywordMap.isEmpty();
        }

        public CustomColumnConfiguration getColumnConfiguration()
        {
            return _columnConfiguration;
        }

        public void setColumnConfiguration(CustomColumnConfiguration columnConfiguration)
        {
            _columnConfiguration = columnConfiguration;
        }

        public String getRequiredFields()
        {
            return _requiredFields;
        }

        public void setRequiredFields(String requiredFields)
        {
            _requiredFields = requiredFields;
        }

        public Map<String, Collection<KeywordManager.Keyword>> getKeywordMap()
        {
            return _keywordMap;
        }

        public void setKeywordMap(Map<String, Collection<KeywordManager.Keyword>> keywordMap)
        {
            _keywordMap = keywordMap;
        }

        public boolean isIgnoreKeywords()
        {
            return _ignoreKeywords;
        }

        public void setIgnoreKeywords(boolean ignoreKeywords)
        {
            _ignoreKeywords = ignoreKeywords;
        }

        public boolean isIgnoreRequiredFields()
        {
            return _ignoreRequiredFields;
        }

        public void setIgnoreRequiredFields(boolean ignoreRequiredFields)
        {
            _ignoreRequiredFields = ignoreRequiredFields;
        }

        @Override
        public int hashCode()
        {
            int result = 0;

            if (!_ignoreRequiredFields)
                result = 31 * result + (_requiredFields != null ? _requiredFields.hashCode() : 0);
            result = 31 * result + (_columnConfiguration != null ? _columnConfiguration.hashCode() : 0);

            // intentionally not comparing keywords, some settings differ very slightly in options but
            // are essentially the same
            if (!_ignoreKeywords)
                result = 31 * result + (_keywordMap != null ? _keywordMap.hashCode() : 0);

            return result;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IssueAdminConfig that = (IssueAdminConfig) o;

            if (!_ignoreRequiredFields)
            {
                if (_requiredFields != null ? !_requiredFields.equals(that._requiredFields) : that._requiredFields != null) return false;
            }

            if (_columnConfiguration != null ? !_columnConfiguration.equals(that._columnConfiguration) : that._columnConfiguration != null) return false;

            if (!_ignoreKeywords)
            {
                if (_keywordMap != null ? !_keywordMap.equals(that._keywordMap) : that._keywordMap != null)  return false;
            }
            return true;
        }
    }

    static class IssueMigrationPlan
    {
        private IssueAdminConfig _config;
        private Collection<Container> _folders;         // folders which use this configuration
        private String _issueDefName;

        public IssueMigrationPlan(IssueAdminConfig config, Collection<Container> folders, String name)
        {
            _config = config;
            _folders = folders;
            _issueDefName = IssuesListDefTable.nameFromLabel(name);
        }

        public IssueAdminConfig getConfig()
        {
            return _config;
        }

        public Collection<Container> getFolders()
        {
            return _folders;
        }

        public String getIssueDefName()
        {
            return _issueDefName;
        }

        public void setIssueDefName(String issueDefName)
        {
            _issueDefName = IssuesListDefTable.nameFromLabel(issueDefName);
        }
    }

    /**
     * Invoked by issues-16.20-16.30.sql
     *
     * Migrate createdBy, modifiedBy, and modified fields from the original issues.issues table to the
     * provisioned tables. This addresses issues 27105 and 27106 properly and allows us to eventually drop
     * columns in the original table.
     */
    @DeferredUpgrade
    public void upgradeSpecialFields(final ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        _log.info("Analyzing site-wide configuration to generate the issue list migration plan");
        MultiValuedMap<Container, IssueMigrationPlan> migrationPlans = generateMigrationPlan();
        User upgradeUser = new LimitedUser(UserManager.getGuestUser(), new int[0], Collections.singleton(RoleManager.getRole(SiteAdminRole.class)), false);
        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            // create the issue list definitions and new domains
            for (Map.Entry<Container, Collection<IssueMigrationPlan>> entry : migrationPlans.asMap().entrySet())
            {
                Container defContainer = entry.getKey();

                for (IssueMigrationPlan plan : entry.getValue())
                {
                    IssueListDef existingDef = IssueManager.getIssueListDef(defContainer, plan.getIssueDefName());
                    if (existingDef != null)
                    {
                        // migrate special fields specific to this folder
                        _log.info("Copying special fields to provisioned table named : " + existingDef.getName());
                        copySpecialFields(existingDef, upgradeUser);
                    }
                    else
                        _log.warn("An expected issue definition of name : " + plan.getIssueDefName() + " was not found in folder : " + defContainer.getPath());
                }
            }
            transaction.commit();
        }
        catch (Exception e)
        {
            _log.error(e.getMessage());
        }
    }

    /**
     * Copy createdBy, modifiedBy, and modified fields from the legacy issues.issues table
     * to the new provisioned tables.
     */
    void copySpecialFields(IssueListDef issueListDef, User user) throws SQLException
    {
        if (issueListDef != null)
        {
            TableInfo table = issueListDef.createTable(user);
            if (table != null)
            {
                String tableName = table.getSelectName();
                SQLFragment sql = new SQLFragment("UPDATE ").append(tableName).
                        append(" SET CreatedBy = Issues.CreatedBy, ModifiedBy = Issues.ModifiedBy, Modified = Issues.Modified ").
                        append("FROM ").append(IssuesSchema.getInstance().getTableInfoIssues(), "Issues").
                        append(" WHERE ").append(tableName).append(".entityId = Issues.entityId AND ").append(tableName).append(".container = Issues.container");

                int result = new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(sql);
                _log.info("Number of rows updated for table : " + table.getName() + " was : " + result);
            }
        }
    }

    /**
     * Invoked by issues-16.20-16.30.sql
     *
     * Drop legacy fields from the issues.issues table, this needs to be performed in a deferred java
     * upgrade script because it must be ordered to run after previous deferred upgrade scripts:
     */
    @DeferredUpgrade
    public void dropLegacyFields(final ModuleContext context)
    {
        _log.info("Deleting obsolete fields from the issues.issues table");

        List<String> columns = Arrays.asList("Title", "Status", "AssignedTo", "Type", "Area",
                "Priority", "Milestone", "BuildFound", "Modified", "ModifiedBy",
                "Created", "CreatedBy", "Tag", "ResolvedBy", "Resolved", "Resolution",
                "ClosedBy", "Closed", "Int1", "Int2", "String1", "String2", "String3", "String4",
                "String5", "NotifyList");

        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            for (String column : columns)
            {
                SQLFragment sql = new SQLFragment("ALTER TABLE ").append(IssuesSchema.getInstance().getTableInfoIssues(), "").
                        append(" DROP COLUMN ").append(column);

                new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(sql);
            }
            transaction.commit();
        }
    }

    /**
     * Bean to represent the legacy issue table which is needed to migrate data from the old to new structures.
     */
    public static class LegacyIssue extends Issue
    {
        private String string1;
        private String string2;
        private String string3;
        private String string4;
        private String string5;
        private Integer int1;
        private Integer int2;

        public String getString1()
        {
            return string1;
        }

        public void setString1(String string1)
        {
            this.string1 = string1;
        }

        public String getString2()
        {
            return string2;
        }

        public void setString2(String string2)
        {
            this.string2 = string2;
        }

        public String getString3()
        {
            return string3;
        }

        public void setString3(String string3)
        {
            this.string3 = string3;
        }

        public String getString4()
        {
            return string4;
        }

        public void setString4(String string4)
        {
            this.string4 = string4;
        }

        public String getString5()
        {
            return string5;
        }

        public void setString5(String string5)
        {
            this.string5 = string5;
        }

        public Integer getInt1()
        {
            return int1;
        }

        public void setInt1(Integer int1)
        {
            this.int1 = int1;
        }

        public Integer getInt2()
        {
            return int2;
        }

        public void setInt2(Integer int2)
        {
            this.int2 = int2;
        }

        @Override
        public boolean equals(Object o)
        {
            if (!super.equals(o))
                return false;

            LegacyIssue issue = (LegacyIssue) o;

            if (int1 != null ? !int1.equals(issue.int1) : issue.int1 != null) return false;
            if (int2 != null ? !int2.equals(issue.int2) : issue.int2 != null) return false;
            if (string1 != null ? !string1.equals(issue.string1) : issue.string1 != null) return false;
            if (string2 != null ? !string2.equals(issue.string2) : issue.string2 != null) return false;
            if (string3 != null ? !string3.equals(issue.string3) : issue.string3 != null) return false;
            if (string4 != null ? !string4.equals(issue.string4) : issue.string4 != null) return false;
            if (string5 != null ? !string5.equals(issue.string5) : issue.string5 != null) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = super.hashCode();

            result = 31 * result + (string1 != null ? string1.hashCode() : 0);
            result = 31 * result + (string2 != null ? string2.hashCode() : 0);
            result = 31 * result + (string3 != null ? string3.hashCode() : 0);
            result = 31 * result + (string4 != null ? string4.hashCode() : 0);
            result = 31 * result + (string5 != null ? string5.hashCode() : 0);
            result = 31 * result + (int1 != null ? int1.hashCode() : 0);
            result = 31 * result + (int2 != null ? int2.hashCode() : 0);

            return result;
        }
    }

    /**
     * Invoked by issues-16.20-16.30.sql
     *
     * Drop properties from the issues provisioned tables that are present in the issues.issues hardtable,
     * this needs to be performed in a deferred java upgrade script because it must be ordered to run
     * after previous deferred upgrade scripts:
     */
    @DeferredUpgrade
    public void dropRedundantProperties(final ModuleContext context)
    {
        _log.info("Dropping redundant fields from the issues provisioned tables");

        User upgradeUser = new LimitedUser(UserManager.getGuestUser(), new int[0], Collections.singleton(RoleManager.getRole(SiteAdminRole.class)), false);
        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            for (IssueListDef issueListDef : IssueManager.getIssueListDefs(null))
            {
                Domain domain = issueListDef.getDomain(upgradeUser);
                if (domain != null)
                {
                    TableInfo table = issueListDef.createTable(upgradeUser);
                    if (table != null)
                    {
                        Collection<PropertyStorageSpec> propsDropped = new ArrayList<>();

                        if (table.getColumn(FieldKey.fromParts("Duplicate")) != null)
                            propsDropped.add(new PropertyStorageSpec("Duplicate", JdbcType.INTEGER));

                        if (table.getColumn(FieldKey.fromParts("LastIndexed")) != null)
                            propsDropped.add(new PropertyStorageSpec("LastIndexed", JdbcType.TIMESTAMP));

                        if (!propsDropped.isEmpty())
                            StorageProvisioner.dropStorageProperties(domain, propsDropped);
                    }
                }
            }
            transaction.commit();
        }
    }
}