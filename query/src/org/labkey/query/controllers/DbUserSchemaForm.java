package org.labkey.query.controllers;

import org.labkey.api.data.BeanViewForm;

import org.labkey.query.persist.DbUserSchemaDef;
import org.labkey.query.persist.QueryManager;

public class DbUserSchemaForm extends BeanViewForm<DbUserSchemaDef>
{
    public DbUserSchemaForm()
    {
        super(DbUserSchemaDef.class, QueryManager.get().getTableInfoDbUserSchema());
    }
}
