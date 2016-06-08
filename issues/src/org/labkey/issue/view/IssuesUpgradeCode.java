package org.labkey.issue.view;

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainTemplate;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.issue.ColumnType;
import org.labkey.issue.ColumnTypeEnum;
import org.labkey.issue.CustomColumnConfiguration;
import org.labkey.issue.IssuesModule;
import org.labkey.issue.model.CustomColumn;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.KeywordManager;
import org.labkey.issue.query.IssueDefDomainKind;
import org.labkey.issue.query.IssuesListDefTable;

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
    final String DEFAULT_ISSUE_LIST_NAME = "issues";
    final String DEFAULT_SITE_ISSUE_LIST_NAME = "siteIssues";

    /**
     * Invoked by issues-16.13-16.14.sql
     *
     * Upgrade to migrate existing issues to provisioned tables and to migrate legacy issues
     * admin setting to the domain and new tables
     */
    // Invoked by issues-16.13-16.14.sql
    @DeferredUpgrade
    public void upgradeIssuesTables(final ModuleContext context)
    {
        _log.info("Analyzing site-wide configuration to generate the issue list migration plan");
        MultiMap<Container, IssueMigrationPlan> migrationPlans = generateMigrationPlan();
        User upgradeUser = new LimitedUser(UserManager.getGuestUser(), new int[0], Collections.singleton(RoleManager.getRole(SiteAdminRole.class)), false);

        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            // create the issue list definitions and new domains
            for (Map.Entry<Container, Collection<IssueMigrationPlan>> entry : migrationPlans.entrySet())
            {
                Container defContainer = entry.getKey();

                for (IssueMigrationPlan plan : entry.getValue())
                {
                    assert IssueManager.getIssueListDef(defContainer, plan.getIssueDefName()) == null : "An issue definition of name : " + plan.getIssueDefName() +
                            " already exists in folder : " + defContainer.getPath();

                    _log.info("Creating the issue def domain named : " + plan.getIssueDefName() + " in folder : " + defContainer.getPath());

                    IssueListDef def = createNewIssueListDef(upgradeUser, plan.getIssueDefName(), defContainer);

                    // initialize the domain with the saved settings
                    configureIssueDomain(upgradeUser, def, plan.getConfig());

                    // now create individual issue lists that share this configuration (if in a different container)
                    for (Container folder : plan.getFolders())
                    {
                        if (!defContainer.equals(folder))
                        {
                            createNewIssueListDef(upgradeUser, plan.getIssueDefName(), folder);
                        }
                    }
                }
            }
            transaction.commit();
        }
        catch (Exception e)
        {
            _log.error(e.getMessage());
        }
    }

    IssueListDef createNewIssueListDef(User user, String label, Container container)
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
        def.setName(IssuesListDefTable.nameFromLabel(label));
        def.setLabel(label);
        def.beforeInsert(user, container.getId());

        return def.save(user);
    }

    /**
     * Initializes the issue definition domain, adding any custom field configurations and legacy picklists
     */
    void configureIssueDomain(User user, IssueListDef def, IssueAdminConfig config) throws Exception
    {
        Domain domain = def.getDomain(user);
        if (domain != null)
        {
            if (!config.isEmpty())
            {
                Map<String, Collection<KeywordManager.Keyword>> keywordMap = config.getKeywordMap();
                Set<String> keywordSet = new CaseInsensitiveHashSet(keywordMap.keySet());
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

                    if (!col.getPermission().equals(ReadPermission.class))
                        prop.setProtected(true);

                    if (col.isPickList())
                    {
                        // need to create the lookup
                        Lookup lookup = prop.getLookup();
                        if (lookup == null)
                        {
                            // create the lookup as a list using the preexisting domain templates
                            DomainTemplateGroup templateGroup = DomainTemplateGroup.get(domain.getContainer(), IssueDefDomainKind.ISSUE_LOOKUP_TEMPLATE_GROUP);
                            String lookupTableName = IssueDefDomainKind.getLookupTableName(domain.getName(), colName);
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
                            populateLookupTable(domain.getContainer(), user, lookup, keywordMap.get(col.getName()));
                        }
                    }
                }

                // standard fields (type, area, priority, milestone...) may have keywords without having a custom column entry
                // deal with them in this pass
                for (String colName : keywordSet)
                {
                    DomainProperty prop = domain.getPropertyByName(colName);
                    if (prop != null && prop.getLookup() != null)
                    {
                        populateLookupTable(domain.getContainer(), user, prop.getLookup(), keywordMap.get(colName));
                    }
                }
                domain.save(user);
            }
        }
    }

    /**
     * Populates the lookup table with the contents of the legacy keywords
     */
    private void populateLookupTable(Container c, User user, Lookup lookup, Collection<KeywordManager.Keyword> keywords) throws Exception
    {
        UserSchema userSchema = QueryService.get().getUserSchema(user, lookup.getContainer(), lookup.getSchemaName());
        TableInfo table = userSchema.getTable(lookup.getQueryName());

        if (table != null)
        {
            QueryUpdateService qus = table.getUpdateService();
            if (qus != null)
            {
                // delete any existing rows
                final List<Map<String, Object>> rowsToDelete = new ArrayList<>();
                new TableSelector(table).forEachMap(row -> rowsToDelete.add(row));
                if (!rowsToDelete.isEmpty())
                {
                    qus.deleteRows(user, c, rowsToDelete, null, null);
                }

                List<Map<String, Object>> rows = new ArrayList();
                BatchValidationException errors = new BatchValidationException();
                String defaultValue = null;

                for (KeywordManager.Keyword keyword : keywords)
                {
                    rows.add(new CaseInsensitiveHashMap<>(Collections.singletonMap("value", keyword.getKeyword())));
                    if (keyword.isDefault())
                        defaultValue = keyword.getKeyword();
                }
                qus.insertRows(user, c, rows, errors, null, null);

                // todo handle default values
            }
        }
    }

    /**
     * Generate the migration plan for every container that needs to be upgraded.
     * @return
     */
    private MultiMap<Container, IssueMigrationPlan> generateMigrationPlan()
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
        Map<Container, MultiMap<IssueAdminConfig, Container>> settingsProjectMap = new HashMap<>();
        MultiMap<IssueAdminConfig, Container> sharedSettingsMap = new MultiHashMap<>();

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

                config.setKeywordMap(keywordMap);
            }
            Container inheritContainer = IssueManager.getInheritFromContainer(c);
            if (inheritContainer == null)
            {
                if (!settingsProjectMap.containsKey(project))
                {
                    settingsProjectMap.put(project, new MultiHashMap<>());
                }
                settingsProjectMap.get(project).put(config, c);
            }
            else
            {
                config.setIgnoreRequiredFields(true);
                sharedSettingsMap.put(config, c);
            }
        }

        // create the new issue definitions
        MultiMap<Container, IssueMigrationPlan> migrationPlans = new MultiHashMap<>();

        for (Map.Entry<Container, MultiMap<IssueAdminConfig, Container>> project : settingsProjectMap.entrySet())
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
                for (Map.Entry<IssueAdminConfig, Collection<Container>> entry : project.getValue().entrySet())
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
                        migrationPlans.put(folder, new IssueMigrationPlan(entry.getKey(), entry.getValue(), folder.getName()));
                    }
                }
            }
        }

        Set<String> siteNames = new HashSet<>();
        // now process the configurations that are configured to be inherited
        for (Map.Entry<IssueAdminConfig, Collection<Container>> entry : sharedSettingsMap.entrySet())
        {
            if (entry.getValue().size() == 1)
            {
                // for single container dependencies, create at the folder level
                Container folder = entry.getValue().iterator().next();
                migrationPlans.put(folder, new IssueMigrationPlan(entry.getKey(), entry.getValue(), folder.getName()));
            }
            else
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
        for (Map.Entry<Container, Collection<IssueMigrationPlan>> entry : migrationPlans.entrySet())
        {
            if (entry.getKey().isProject() && !ContainerManager.getSharedContainer().equals(entry.getKey()))
            {
                if (entry.getValue().size() == 1)
                {
                    entry.getValue().iterator().next().setIssueDefName(DEFAULT_ISSUE_LIST_NAME);
                }
                else
                {
                    Set<String> projectNames = new HashSet<>();

                    for (IssueMigrationPlan plan : entry.getValue())
                    {
                        if (plan.getIssueDefName() == null && !plan.getConfig().isEmpty())
                        {
                            plan.setIssueDefName(DEFAULT_ISSUE_LIST_NAME);
                            projectNames.add(DEFAULT_ISSUE_LIST_NAME);
                            break;
                        }
                    }

                    for (IssueMigrationPlan plan : entry.getValue())
                    {
                        if (plan.getIssueDefName() == null)
                        {
                            String name = createUniqueName(DEFAULT_ISSUE_LIST_NAME, projectNames);
                            plan.setIssueDefName(name);
                            projectNames.add(name);
                        }
                    }
                }
            }
        }
        return migrationPlans;
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
            _issueDefName = name;
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
            _issueDefName = issueDefName;
        }
    }
}