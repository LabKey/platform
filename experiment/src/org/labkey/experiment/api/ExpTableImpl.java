package org.labkey.experiment.api;

import org.labkey.api.query.*;
import org.labkey.api.exp.api.ExpTable;
import org.labkey.api.exp.api.TableEditHelper;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.sql.Types;
import java.util.Map;
import java.util.TreeMap;

import org.labkey.experiment.api.flag.FlagForeignKey;
import org.labkey.experiment.api.flag.FlagColumnRenderer;

abstract public class ExpTableImpl<C extends Enum> extends FilteredTable implements ExpTable<C>
{
    protected Container _container;
    protected TableEditHelper _editHelper;
    public ExpTableImpl(String alias, TableInfo rootTable)
    {
        super(rootTable);
        setName(alias);
        setAlias(alias);
    }

    public void setContainer(Container container)
    {
        if (_container != null)
        {
            throw new IllegalStateException("Container already set");
        }
        if (container != null)
        {
            _container = container;
            addCondition(_rootTable.getColumn("container"), container.getId());
        }
    }

    public Container getContainer()
    {
        return _container;
    }

    final public ColumnInfo addColumn(C column)
    {
        return addColumn(column.toString(), column);
    }

    final public ColumnInfo addColumn(String alias, C column)
    {
        ColumnInfo ret = createColumn(alias, column);
        addColumn(ret);
        return ret;
    }

    public ColumnInfo getColumn(C column)
    {
        for (ColumnInfo info : getColumns())
        {
            if (info instanceof ExprColumn && info.getAlias().equals(column.toString()))
            {
                return info;
            }
        }
        return null;
    }

    protected ColumnInfo doAdd(ColumnInfo column)
    {
        addColumn(column);
        return column;
    }

    public ColumnInfo createPropertyColumn(String name)
    {
        String sql = "( SELECT objectid FROM exp.object WHERE exp.object.objecturi = " + ExprColumn.STR_TABLE_ALIAS + ".lsid)";
        ColumnInfo ret = new ExprColumn(this, name, new SQLFragment(sql), Types.INTEGER);
        ret.setIsUnselectable(true);
        return ret;
    }

    public ColumnInfo createPropertyValueColumn(String name, PropertyDescriptor pd)
    {
        SQLFragment sqlLSID = wrapColumn("~~createPropertyValueColumn~~", getLSIDColumn()).getValueSql(ExprColumn.STR_TABLE_ALIAS);
        SQLFragment sqlValue = PropertyForeignKey.getValueSql(pd.getPropertyType());
        SQLFragment sql = PropertyForeignKey.getValueSql(sqlLSID, sqlValue, pd.getPropertyId(), true);
        return new ExprColumn(this, name, sql, pd.getPropertyType().getSqlType());
    }

    public ColumnInfo createUserColumn(String name, ColumnInfo userIdColumn)
    {
        ColumnInfo ret = wrapColumn(name, userIdColumn);
        ret.setFk(new UserIdForeignKey());
        return ret;
    }

    public String urlFlag(boolean flagged)
    {
        return flagged ? ExpObjectImpl.s_urlFlagged : ExpObjectImpl.s_urlUnflagged;
    }

    protected ColumnInfo getLSIDColumn()
    {
        return _rootTable.getColumn("LSID");
    }

    protected ColumnInfo createFlagColumn(String alias)
    {
        ColumnInfo ret = wrapColumn(alias, getLSIDColumn());
        ret.setFk(new FlagForeignKey(urlFlag(true), urlFlag(false)));
        ret.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new FlagColumnRenderer(colInfo);
            }
        });
        return ret;
    }

    public void addRowIdCondition(SQLFragment condition)
    {
        SQLFragment sqlCondition = new SQLFragment("RowId ");
        sqlCondition.append(condition);
        addCondition(sqlCondition);
    }

    public void addLSIDCondition(SQLFragment condition)
    {
        SQLFragment sqlCondition = new SQLFragment("LSID ");
        sqlCondition.append(condition);
        addCondition(sqlCondition);
    }

    public void setEditHelper(TableEditHelper helper)
    {
        _editHelper = helper;
    }

    public boolean hasPermission(User user, int perm)
    {
        if (_editHelper != null)
            return _editHelper.hasPermission(user, perm);
        return false;
    }

    public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception
    {
        if (_editHelper != null)
        {
            return _editHelper.delete(user, srcURL, form);
        }
        throw new UnsupportedOperationException();
    }

    public ColumnInfo addPropertyColumns(String categoryDescription, PropertyDescriptor[] pds, QuerySchema schema)
    {
        String sqlObjectId = "(SELECT objectid FROM " + OntologyManager.getTinfoObject() + " o WHERE o.objecturi = " +
                ExprColumn.STR_TABLE_ALIAS + ".lsid)";

        ColumnInfo colProperty = new ExprColumn(this, categoryDescription, new SQLFragment(sqlObjectId), Types.INTEGER);
        Map<String, PropertyDescriptor> map = new TreeMap<String, PropertyDescriptor>();
        for(PropertyDescriptor pd : pds)
        {
            map.put(pd.getName(), pd);
        }
        colProperty.setFk(new PropertyForeignKey(map, schema));
        colProperty.setIsUnselectable(true);
        addColumn(colProperty);

        return colProperty;
    }
}
