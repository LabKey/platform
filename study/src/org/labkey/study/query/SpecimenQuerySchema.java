package org.labkey.study.query;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.study.model.StudyImpl;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: kevink
 * Date: 10/10/12
 *
 * TODO:
 * SpecimenQuerySchema extends StudyQuerySchema only to reduce the amount
 * of refactoring necessary to attach the AbstractSpecimenTable tables.
 * Eventually, the AbstractSpecimenTable should accept SpecimenQuerySchema
 * instead of StudyQuerySchema in the ctor.
 */
//public class SpecimenQuerySchema extends UserSchema
public class SpecimenQuerySchema extends StudyQuerySchema
{
    public static final String NAME = "specimen";
    public static final String DESCRIPTION = "Contains tables related to study specimens";
    public static final String SIMPLE_SPECIMEN_TABLE_NAME = "SimpleSpecimen";

    private enum TableName
    {
        Event,
        Detail,
        Summary,
        //Request,
        //RequestStatus,
        //Vial,
        //VialCount,
        //VialRequest,

        //Additive,
        //Derivative,
        //PrimaryType,
        //Comment,

        //Simple,
    }

    private static final Set<String> TABLE_NAMES;
    static
    {
        Set<String> tableNames = new LinkedHashSet<String>();
        for (TableName table : TableName.values())
            tableNames.add(table.name());

        TABLE_NAMES = Collections.unmodifiableSet(tableNames);
    }

    private StudyQuerySchema _parent;

    public SpecimenQuerySchema(StudyQuerySchema parent, StudyImpl study, User user, Container container)
    {
        super(study, NAME, DESCRIPTION, user, container);
        _parent = parent;
        _path = SchemaKey.fromParts(_parent.getName(), NAME);
    }

    // Ugh. Restore UserSchema.getSchemaNames() behavior.
    @Override
    public Set<String> getSchemaNames()
    {
        Set<String> ret = new HashSet<String>();
        ret.add("Folder");
        return ret;
    }

    // Ugh. Restore UserSchema.getSchema() behavior.
    @Override
    public QuerySchema getSchema(String name)
    {
        if (_restricted)
            return null;
        return DefaultSchema.get(_user, _container).getSchema(name);
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }

    @Override
    public Set<String> getVisibleTableNames()
    {
        if (_study != null)
            return TABLE_NAMES;

        return Collections.emptySet();
    }

    @Override
    public TableInfo createTable(String name)
    {
        TableName table;
        try
        {
            table = TableName.valueOf(name);
        }
        catch (IllegalArgumentException e)
        {
            // name not found
            return null;
        }

        AbstractTableInfo ret = null;
        switch (table)
        {
            case Event:
                ret = new SpecimenEventTable(this);
                break;

            case Detail:
                ret = new SpecimenDetailTable(this);
                break;

            case Summary:
                ret = new SpecimenSummaryTable(this);
                break;

        }

        if (ret != null)
        {
            ret.setName(table.name());
            return ret;
        }
        else
        {
            return null;
        }
    }

}
