package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 6/12/12
 * Time: 12:12 AM
 */
public class ModuleProperty
{
    private Module _module;
    private String _name;
    private boolean _required = false;
    private boolean _canSetPerContainer = false;
    private boolean _canSetPerUser = false;
    private boolean _excludeFromClientContext = false;
    private String _defaultValue = null;
    private String _description = null;

    private List<Class<? extends Permission>> _editPermissions;

    public ModuleProperty(Module module, String name)
    {
        _module = module;
        _name = name;

        //default to requiring admin permission
        _editPermissions = new ArrayList<Class<? extends Permission>>();
        _editPermissions.add(AdminPermission.class);
    }

    public String getCategory()
    {
        return "moduleProperties" + "." + _module.getName();
    }

    public Module getModule()
    {
        return _module;
    }

    public String getName()
    {
        return _name;
    }

    public boolean isRequired()
    {
        return _required;
    }

    public void setRequired(boolean required)
    {
        _required = required;
    }

    public boolean isCanSetPerContainer()
    {
        return _canSetPerContainer;
    }

    public void setCanSetPerContainer(boolean canSetPerContainer)
    {
        _canSetPerContainer = canSetPerContainer;
    }

//    public boolean isCanSetPerUser()
//    {
//        return _canSetPerUser;
//    }
//
//    public void setCanSetPerUser(boolean canSetPerUser)
//    {
//        _canSetPerUser = canSetPerUser;
//    }

    public String getDefaultValue()
    {
        return _defaultValue;
    }

    public void setDefaultValue(String defaultValue)
    {
        _defaultValue = defaultValue;
    }

    @NotNull
    public List<Class<? extends Permission>> getEditPermissions()
    {
        return _editPermissions == null ? new ArrayList<Class<? extends Permission>>(): _editPermissions;
    }

    public void setEditPermissions(List<Class<? extends Permission>> editPermissions)
    {
        _editPermissions = editPermissions;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public boolean isExcludeFromClientContext()
    {
        return _excludeFromClientContext;
    }

    public void setExcludeFromClientContext(boolean excludeFromClientContext)
    {
        _excludeFromClientContext = excludeFromClientContext;
    }

    public JSONObject toJson()
    {
        JSONObject ret = new JSONObject();

        ret.put("name", getName());
        ret.put("module", getModule().getName());
        ret.put("required", isRequired());
        ret.put("canSetPerContainer", isCanSetPerContainer());
        //ret.put("canSetPerUser", isCanSetPerUser());
        ret.put("defaultValue", getDefaultValue());
        ret.put("editPermissions", getEditPermissions());
        ret.put("description", getDescription());
        return ret;
    }

    public void saveValue(User u, Container c, int userIdToSave, String value)
    {
        if (!isCanSetPerContainer() && !c.isRoot())
            throw new IllegalArgumentException("This property can not be set for this container.  It can only be set site-wide, which means it must be set on the root container.");

//        if (!isCanSetPerUser() && userIdToSave != 0)
//            throw new IllegalArgumentException("This property can not be set on a per user basis.  Use a userId of zero");

        //properties provide their edit permissions, so we only enforce read on the container
        if (!c.hasPermission(u, ReadPermission.class))
            throw new SecurityException("The user does not have read permission on this container");

        for (Class<? extends Permission> p : getEditPermissions())
        {
            if (!c.hasPermission(u, p))
                throw new SecurityException("The user does not have " + p.getName() + " permission on this container");
        }

        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(userIdToSave, c.getId(), getCategory(), true);
        if(!StringUtils.isEmpty(value))
            props.put(getName(), value);
        else
            props.remove(getName());
        PropertyManager.saveProperties(props);
    }

    /**
     * NOTE: does not test permissions
     */
    public String getEffectiveValue(User user, Container c, String name)
    {
        int userId = 0;

//        if(user == null || !isCanSetPerUser())
//            userId = 0;
//        else
//            userId = user.getUserId();

        String value;
        if(isCanSetPerContainer())
            value = PropertyManager.getCoalecedProperty(userId, c, getCategory(), name);
        else
            value = PropertyManager.getCoalecedProperty(userId, ContainerManager.getRoot(), getCategory(), name);

        if(value == null)
            value = getDefaultValue();

        return value;
    }
}
