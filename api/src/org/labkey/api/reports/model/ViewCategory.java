package org.labkey.api.reports.model;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.permissions.SharedParticipantGroupPermission;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Oct 12, 2011
 * Time: 7:23:43 PM
 */
public class ViewCategory extends Entity
{
    private int _rowId;
    private String _label;
    private int _displayOrder;

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public int getDisplayOrder()
    {
        return _displayOrder;
    }

    public void setDisplayOrder(int displayOrder)
    {
        _displayOrder = displayOrder;
    }

    public boolean canEdit(Container container, User user)
    {
        return user.isAdministrator();
    }

    public boolean canDelete(Container container, User user)
    {
        return canEdit(container, user);
    }

    public boolean canRead(Container container, User user)
    {
        return container.hasPermission(user, ReadPermission.class);
    }

    public JSONObject toJSON(User currentUser)
    {
        JSONObject o = new JSONObject();

        o.put("rowid", getRowId());
        o.put("label", getLabel());
        o.put("displayOrder", getDisplayOrder());


        o.put("created", getCreated());
        o.put("modified", getModified());

        User user = UserManager.getUser(getCreatedBy());
        o.put("createdBy", createDisplayValue(getCreatedBy(), user != null ? user.getDisplayName(currentUser) : getCreatedBy()));

        User modifiedBy = UserManager.getUser(getModifiedBy());
        o.put("modifiedBy", createDisplayValue(getModifiedBy(), modifiedBy != null ? modifiedBy.getDisplayName(currentUser) : getModifiedBy()));

        return o;
    }

    public static ViewCategory fromJSON(JSONObject info)
    {
        ViewCategory category = new ViewCategory();

        Object row = info.get("rowid");
        if (row instanceof Integer)
            category.setRowId((Integer)row);

        Object label = info.get("label");
        if (label instanceof String)
            category.setLabel((String)label);

        Object displayOrder = info.get("displayOrder");
        if (displayOrder instanceof Integer)
            category.setDisplayOrder((Integer)displayOrder);

        return category;
    }

    private JSONObject createDisplayValue(Object value, Object displayValue)
    {
        JSONObject json = new JSONObject();

        json.put("value", value);
        json.put("displayValue", displayValue);

        return json;
    }
}

