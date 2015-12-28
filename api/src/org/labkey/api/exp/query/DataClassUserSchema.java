package org.labkey.api.exp.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 9/24/15
 */
public class DataClassUserSchema extends AbstractExpSchema
{
    public static final String NAME = ExpSchema.NestedSchemas.data.name();
    private static final String DESCR = "Contains data about the registered datas";

    private Map<String, ExpDataClass> _map;

    static private Map<String, ExpDataClass> getDataClassMap(Container container, User user)
    {
        Map<String, ExpDataClass> map = new CaseInsensitiveTreeMap<>();
        // User can be null if we're running in a background thread, such as doing a study export.
        for (ExpDataClass dataClass : ExperimentService.get().getDataClasses(container, user, true))
        {
            map.put(dataClass.getName(), dataClass);
        }
        return map;
    }

    public DataClassUserSchema(Container container, User user)
    {
        this(container, user, null);
    }

    private DataClassUserSchema(Container container, User user, Map<String, ExpDataClass> map)
    {
        super(SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, NAME), DESCR, user, container, ExperimentService.get().getSchema());
        _map = map;
    }

    private Map<String, ExpDataClass> getDataClasses()
    {
        if (_map == null)
            _map = getDataClassMap(getContainer(), getUser());
        return _map;
    }

    @Override
    public Set<String> getTableNames()
    {
        return getDataClasses().keySet();
    }

    @Nullable
    @Override
    protected TableInfo createTable(String name)
    {
        ExpDataClass dataClass = getDataClasses().get(name);
        if (dataClass == null)
            return null;

        return createTable(dataClass);
    }

    private ExpDataClassDataTable createTable(ExpDataClass dataClass)
    {
        ExpDataClassDataTable ret = ExperimentService.get().createDataClassDataTable(dataClass.getName(), this, dataClass);
        if (_containerFilter != null)
            ret.setContainerFilter(_containerFilter);
        ret.populate();
        ret.overlayMetadata(ret.getPublicName(), DataClassUserSchema.this, new ArrayList<>());
        return ret;
    }

    @Override
    public String getDomainURI(String queryName)
    {
        Container container = getContainer();
        ExpDataClass mts = getDataClasses().get(queryName);
        if (mts == null)
            throw new NotFoundException("DataClass '" + queryName + "' not found in this container '" + container.getPath() + "'.");

        return mts.getDomain().getTypeURI();
    }

}
