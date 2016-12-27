package org.labkey.experiment;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleContext;

import java.util.Collection;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/27/16
 */
public class ExperimentUpgradeCode implements UpgradeCode
{
    /** Called from exp-16.31-16.32.sql */
    public static void cleanupQuotedAliases(ModuleContext context) throws Exception
    {
        if (context.isNewInstall())
            return;

        DbSchema schema = ExperimentService.get().getSchema();
        try (DbScope.Transaction tx = schema.getScope().beginTransaction())
        {
            TableInfo aliasTable = ExperimentService.get().getTinfoAlias();
            TableInfo dataAliasMapTable = ExperimentService.get().getTinfoDataAliasMap();
            TableInfo materialAliasMapTable = ExperimentService.get().getTinfoMaterialAliasMap();

            // get map of alias name to rowId
            Map<String, Integer> aliases = new TableSelector(aliasTable, aliasTable.getColumns("name", "rowId"), null, null).getValueMap();

            // for each alias, if it is enclosed in single or double quotes
            // - remove the quotes
            // - if the unquoted alias already exists,
            //     - find any objects that have a reference to both the quoted and unquoted alias
            //         - remove the quoted alias reference
            //     - update all remaining references to the quoted alias to the unquoted alias
            //     - delete the quoted alias
            // - otherwise
            //     - change the quoted alias to the newly unquoted alias
            for (String quotedAliasName : aliases.keySet())
            {
                if ((quotedAliasName.startsWith("\"") && quotedAliasName.endsWith("\"")) ||
                    (quotedAliasName.startsWith("'") && quotedAliasName.endsWith("'")))
                {
                    Integer quotedAliasRowId = aliases.get(quotedAliasName);

                    // remove quotes
                    String unquotedAliasName = quotedAliasName.substring(1, quotedAliasName.length()-1);

                    // check if it already exists
                    Integer unquotedAliasRowId = aliases.get(unquotedAliasName);
                    if (unquotedAliasRowId != null)
                    {
                        fixupAliasReferences(dataAliasMapTable, quotedAliasRowId, unquotedAliasRowId);
                        fixupAliasReferences(materialAliasMapTable, quotedAliasRowId, unquotedAliasRowId);

                        // delete the quoted alias
                        Table.delete(aliasTable, new SimpleFilter("rowid", quotedAliasRowId));
                    }
                    else
                    {
                        // update the quoted alias name to the unquoted string
                        Table.update(null, aliasTable, CaseInsensitiveHashMap.of("name", unquotedAliasName), quotedAliasRowId);
                    }
                }
            }

            tx.commit();
        }
    }

    private static void fixupAliasReferences(TableInfo aliasMapTable, Integer quotedAliasRowId, Integer unquotedAliasRowId)
    {
        // find all references to the quoted alias
        Collection<Map<String, Object>> refs = new TableSelector(aliasMapTable, TableSelector.ALL_COLUMNS, new SimpleFilter("alias", quotedAliasRowId), null).getMapCollection();
        for (Map<String, Object> ref : refs)
        {
            String lsid = (String)ref.get("lsid");

            // check if the lsid has both quoted and unquoted aliases
            Collection<Map<String, Object>> aliasEntriesForLsid = new TableSelector(aliasMapTable, TableSelector.ALL_COLUMNS, new SimpleFilter("lsid", lsid), null).getMapCollection();
            for (Map<String, Object> aliasEntryForLsid : aliasEntriesForLsid)
            {
                if (quotedAliasRowId.equals(aliasEntryForLsid.get("alias")))
                    continue;

                if (unquotedAliasRowId.equals(aliasEntryForLsid.get("alias")))
                {
                    // lsid has both quoted and unquoted aliases.  remove the redundant quoted one.
                    Table.delete(aliasMapTable, new SimpleFilter("lsid", lsid).addCondition("alias", quotedAliasRowId));
                }
            }

            // changing remaining references of the quoted alias to the unquoted alias
            DbSchema schema = aliasMapTable.getSchema();
            SqlExecutor sqlex = new SqlExecutor(schema);
            SQLFragment sql = new SQLFragment("UPDATE ")
                    .append(aliasMapTable.toString())
                    .append(" SET alias=? WHERE alias=?")
                    .add(unquotedAliasRowId)
                    .add(quotedAliasRowId);
            sqlex.execute(sql);
        }
    }

}
