package org.labkey.audit.query;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.audit.AuditSchema;

import java.util.List;

public class AuditUpgradeCode implements UpgradeCode
{
    public static final Logger LOG = LogHelper.getLogger(AuditUpgradeCode.class, "Audit upgrade activities");

    // called from audit-22.000-22.001.sql
    public static void updateRowIdToBigInt(ModuleContext context)
    {
        boolean isPostgres = DbScope.getLabKeyScope().getSqlDialect().isPostgreSQL();
        DbScope scope = AuditSchema.getInstance().getSchema().getScope();
        List<String> tableNames = new SqlSelector(scope, "SELECT StorageTableName FROM exp.domainDescriptor WHERE StorageSchemaName='audit'").getArrayList(String.class);
        LOG.info("Found " + tableNames.size() + " audit tables to update.");

        tableNames.forEach(tableName -> {
            LOG.info("Updating audit table: " + tableName);
            long start = System.currentTimeMillis();

            SQLFragment sql = new SQLFragment();

            if (isPostgres)
                sql.append("ALTER TABLE audit.").append(tableName).append(" ALTER COLUMN RowId TYPE BIGINT;\n");
            else
            {
                String pkName = tableName + "_pk";
                sql.append("ALTER TABLE audit.").append(tableName).append(" DROP CONSTRAINT ").append(pkName).append(";\n");
                sql.append("ALTER TABLE audit.").append(tableName).append(" ALTER COLUMN RowId BIGINT NOT NULL;\n");
                sql.append("ALTER TABLE audit.").append(tableName).append(" ADD CONSTRAINT ").append(pkName).append(" PRIMARY KEY (RowId);\n");
            }

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                SqlExecutor se = new SqlExecutor(scope);
                se.execute(sql);
                transaction.commit();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
            finally
            {
                long elapsed = System.currentTimeMillis() - start;
                LOG.info(tableName + " update time: " + elapsed / (1000 * 60) + " min");
            }
        });
    }
}
