package org.labkey.api.module;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlScriptRunner;

/**
* User: adam
* Date: 8/28/13
* Time: 4:19 PM
*/
enum SchemaUpdateType
{
    Before
    {
        @Nullable
        @Override
        SqlScriptRunner.SqlScript getScript(SqlScriptRunner.SqlScriptProvider provider, DbSchema schema) throws SqlScriptRunner.SqlScriptException
        {
            return provider.getDropScript(schema);
        }
    },

    After
    {
        @Nullable
        @Override
        SqlScriptRunner.SqlScript getScript(SqlScriptRunner.SqlScriptProvider provider, DbSchema schema) throws SqlScriptRunner.SqlScriptException
        {
            return provider.getCreateScript(schema);
        }
    };

    abstract @Nullable SqlScriptRunner.SqlScript getScript(SqlScriptRunner.SqlScriptProvider provider, DbSchema schema) throws SqlScriptRunner.SqlScriptException;
}
