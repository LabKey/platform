/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ACL;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartFactory;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.*;


/**
 * see ContainerManager for more info
 *
 * CONSIDER: extend org.labkey.api.data.Entity
 */
public class Container implements Serializable
{
    static Logger _log = Logger.getLogger(Container.class);

    String _id;
    String _path;
    String _pathParent;
    String _name;
    Date _created;

    int _rowId; //Unique for this installation
    private int _sortOrder;
    private transient Module _defaultModule;
    private transient Set<Module> _activeModules;

    boolean _inheritedAcl;
    transient WeakReference<Container> _parent;

    public static final String DEFAULT_SUPPORT_PROJECT_PATH = ContainerManager.HOME_PROJECT_PATH + "/support";
    private transient FolderType _folderType;


    // UNDONE: BeanFactory for Container

    protected Container(String path, Container dirParent, String id, int rowId, int sortOrder, Date created)
    {
        _path = path;
        _id = id;
        _parent = new WeakReference<Container>(dirParent);
        assert dirParent != null || path == null || path.equals("/");
        _pathParent = dirParent == null ? "" : dirParent.getPath();
        if (path != null)
            _name = _path.substring(_path.lastIndexOf('/') + 1);

        _rowId = rowId;
        _sortOrder = sortOrder;
        _created = created;
    }


    public String getName()
    {
        return _name;
    }


    public Date getCreated()
    {
        return _created;
    }


    synchronized ACL computeAcl()
    {
        ACL acl = SecurityManager.getACL(_id);
        _inheritedAcl = false;
        if (null == acl || acl.isEmpty())
        {
            _inheritedAcl = true;
            Container parent = getParent();
            acl = (null == parent || this == parent) ? ACL.EMPTY : parent.getAcl();
        }
        return acl;
    }

    public boolean isInheritedAcl()
    {
        return _inheritedAcl;
    }

    /** If someone holds onto a container too long (e.g. in a cache, or across requests), it may become invalidated */
    public boolean isValid(Container c)
    {
        return c == ContainerManager.getForId(getId());
    }


    public Container getParent()
    {
        Container parent = _parent == null ? null : _parent.get();
        if (null == parent && null != _pathParent)
        {
            parent = ContainerManager.getForPath(_pathParent);
            _parent = new WeakReference<Container>(parent);
        }
        return parent;
    }


    public String getPath()
    {
        return _path;
    }


    /**
     * returned string begins and ends with "/" so you can slap it between
     * getContextPath() and action.view
     */
    public String getEncodedPath()
    {
        String path = _path.startsWith("/") ? _path.substring(1) : _path;
        String[] parts = path.split("/");
        StringBuffer encoded = new StringBuffer(_path.length() + 10);
        encoded.append("/");
        for (String part : parts)
        {
            encoded.append(PageFlowUtil.encode(part));
            encoded.append("/");
        }
        return encoded.toString();
    }


    public String getId()
    {
        return _id;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getSortOrder()
    {
        return _sortOrder;
    }

    public void setSortOrder(int sortOrder)
    {
        _sortOrder = sortOrder;
    }

    public Container getProject()
    {
        // Root has no project
        if (isRoot())
            return null;

        Container project = this;
        while (!project.isProject())
            project = project.getParent();
        return project;
    }


    public ACL getAcl()
    {
        return computeAcl();
    }


    public boolean hasPermission(User user, int perm)
    {
        ACL acl = getAcl();
        return acl.hasPermission(user, perm);
    }


    public boolean isProject()
    {
        return "/".equals(_pathParent);
    }


    public boolean isRoot()
    {
        return "/".equals(_path);
    }


    public Container getChild(String folderName)
    {
        String path = _path + (isRoot() ? "" : "/") + folderName;

        return ContainerManager.getForPath(path);
    }

    public boolean hasChild(String folderName)
    {
        return getChild(folderName) != null;
    }

    public boolean hasChildren()
    {
        return getChildren().size() > 0;
    }

    public List<Container> getChildren()
    {
        return ContainerManager.getChildren(this);
    }

    public String toString()
    {
        return getClass().getName() + "@" + System.identityHashCode(this) + " " + _path + " " + _id;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Container container = (Container) o;

        if (_id != null ? !_id.equals(container._id) : container._id != null) return false;
        if (_name != null ? !_name.equals(container._name) : container._name != null) return false;
        if (_path != null ? !_path.equals(container._path) : container._path != null) return false;
        if (_pathParent != null ? !_pathParent.equals(container._pathParent) : container._pathParent != null)
            return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = (_id != null ? _id.hashCode() : 0);
        result = 29 * result + (_path != null ? _path.hashCode() : 0);
        result = 29 * result + (_pathParent != null ? _pathParent.hashCode() : 0);
        result = 29 * result + (_name != null ? _name.hashCode() : 0);
        return result;
    }


    public static boolean isLegalName(String name, StringBuffer error)
    {
        if (null == name || 0 == name.trim().length())
        {
            error.append("Blank names are not allowed.");
            return false;
        }

        if (-1 != name.indexOf('/') || -1 != name.indexOf(';') || -1 != name.indexOf('\\'))
        {
            error.append("Slashes, semicolons, and backslashes are not allowed in folder names.");
            return false;
        }

        //Don't allow ISOControl characters as they are not handled well by the databases
        for( int i = 0; i < name.length(); ++i)
        {
            if (Character.isISOControl(name.charAt(i)))
            {
                error.append("Non-printable characters are not allowed in folder names.");
                return false;
            }
        }

        return true;
    }

    public ActionURL getStartURL(ViewContext viewContext)
    {
        FolderType ft = getFolderType();
        if (!FolderType.NONE.equals(ft))
            return ft.getStartURL(this, viewContext.getUser());

        Module module = getDefaultModule();
        if (module != null)
        {
            ActionURL helper = module.getTabURL(this, viewContext.getUser());
            if (helper != null)
                return helper;
        }

        return new ActionURL("Project", "begin", getPath());
    }

    public Module getDefaultModule()
    {
        if (isRoot())
            return null;

        try
        {
            if (_defaultModule == null)
            {
                Map props = PropertyManager.getProperties(getId(), "defaultModules", false);
                String defaultModuleName = null;
                if (props != null)
                    defaultModuleName = (String) props.get("name");

                boolean initRequired = false;
                if (null == defaultModuleName || null == ModuleLoader.getInstance().getModule(defaultModuleName))
                {
                    defaultModuleName = "Portal";
                    initRequired = true;
                }
                Module defaultModule = ModuleLoader.getInstance().getModule(defaultModuleName);

                //set default module
                if (initRequired)
                    setDefaultModule(defaultModule);

                //ensure that default module is included in active module set
                //should be there already if it's not portal, but if it is portal, we have to add it for upgrade
                if (defaultModuleName.compareToIgnoreCase("Portal") == 0)
                {
                    Set<Module> modules = new HashSet<Module>(getActiveModules());
                    if(!modules.contains(defaultModule))
                    {
                        modules.add(defaultModule);
                        setActiveModules(modules);
                    }
                }

                _defaultModule = defaultModule;
            }
            return _defaultModule;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void setFolderType(FolderType folderType, Set<Module> ensureModules) throws SQLException
    {
        setFolderType(folderType);
        Set<Module> modules = new HashSet<Module>(folderType.getActiveModules());
        modules.addAll(ensureModules);
        setActiveModules(modules);
    }

    public void setFolderType(FolderType folderType) throws SQLException
    {
        FolderType oldType = getFolderType();
        if (folderType.equals(oldType))
            return;

        oldType.unconfigureContainer(this);
        folderType.configureContainer(this);
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(0, getId(), "folderType", true);
        props.put("name", folderType.getName());
        PropertyManager.saveProperties(props);

        _folderType = null;
    }

    public FolderType getFolderType()
    {
        if (null != _folderType)
            return _folderType;

        Map props = PropertyManager.getProperties(0, getId(), "folderType", true);
        String name = props == null ? null : (String) props.get("name");
        if (null != name)
        {
            _folderType = ModuleLoader.getInstance().getFolderType(name);
            if (null == _folderType)
            {
                _log.warn("No such folder type " + name + " for folder " + toString());
                _folderType = FolderType.NONE;
            }
        }
        else
            _folderType = FolderType.NONE;

        return _folderType;
    }

    /**
     * Sets the default module for a "mixed" type folder. We try not to create
     * these any more. Instead each folder is "owned" by a module
     * @param module
     */
    public void setDefaultModule(Module module)
    {
        if (module == null)
            return;
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(0, getId(), "defaultModules", true);
        props.put("name", module.getName());

        PropertyManager.saveProperties(props);
        ContainerManager.notifyContainerChange(getId());
        _defaultModule = null;
    }



    public void setActiveModules(Set<Module> modules) throws SQLException
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(0, getId(), "activeModules", true);
        props.clear();
        for (Module module : modules)
        {
            if(null != module)
                props.put(module.getName(), Boolean.TRUE.toString());
        }
        PropertyManager.saveProperties(props);
        ContainerManager.notifyContainerChange(getId());
        _activeModules = null;
    }


    private void removeForward(final Portal.WebPart[] activeWebparts)
    {
        // we no longer use forwards: this concept has been replaced by the default-tab setting.
        // So, remove the setting here:
        boolean hasForward = false;
        List<Portal.WebPart> partList = new ArrayList<Portal.WebPart>(activeWebparts.length);
        for (Portal.WebPart part : activeWebparts)
        {
            if (!"forward".equals(part.getLocation()))
                partList.add(part);
            else
                hasForward = true;
        }
        if (hasForward)
        {
            Portal.saveParts(getId(), partList.toArray(new Portal.WebPart[partList.size()]));
        }
    }

    @Deprecated
    public ActionURL urlFor(Enum action)
    {
        return PageFlowUtil.urlFor(action, this);
    }

    // UNDONE: (MAB) getActiveModules() and setActiveModules()
    // UNDONE: these don't feel like they belong on this class
    // UNDONE: move to ModuleLoader? 
    public Set<Module> getActiveModules()
    {
        return getActiveModules(true);
    } 
    public Set<Module> getActiveModules(boolean init)
    {
        if (_activeModules == null)
        {
            //Short-circuit for root module
            if (isRoot())
            {
                //get active modules from database
                Set<Module> modules = new HashSet<Module>();
               _activeModules = Collections.unmodifiableSet(modules);
               return _activeModules;
            }

            Map<String,String> props = PropertyManager.getProperties(getId(), "activeModules", false);
            //get set of all modules
            List<Module> allModules = ModuleLoader.getInstance().getModules();
            //get active web parts for this container
            Portal.WebPart[] activeWebparts = Portal.getParts(getId());
            //remove forward from active web parts
            removeForward(activeWebparts);

            //store active modules
            if (props == null && init)
            {
                //initialize properties cache
                PropertyManager.PropertyMap propsWritable = PropertyManager.getWritableProperties(0, getId(), "activeModules", true);
                props = propsWritable;

                if (isProject())
                {
                    // first time in this project: initalize active modules now, based on the active webparts
                    Map<String, Module> mapWebPartModule = new HashMap<String, Module>();
                    //get set of all web parts for all modules
                    for (Module module : allModules)
                    {
                        WebPartFactory[] webparts = module.getModuleWebParts();
                        if (webparts != null)
                        {
                            for (WebPartFactory desc : webparts)
                                mapWebPartModule.put(desc.getName(), module);
                        }
                    }

                    //get active modules based on which web parts are active
                    for (Portal.WebPart activeWebPart : activeWebparts)
                    {
                        if (!"forward".equals(activeWebPart.getLocation()))
                        {
                            //get module associated with this web part & add to props
                            Module activeModule = mapWebPartModule.get(activeWebPart.getName());
                            if (activeModule != null)
                                propsWritable.put(activeModule.getName(), Boolean.TRUE.toString());
                        }
                    }

                    // enable 'default' tabs:
                    for (Module module : allModules)
                    {
                        if (module.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT)
                            propsWritable.put(module.getName(), Boolean.TRUE.toString());
                    }
                }
                else
                {
                    //if this is a subfolder, set active modules to inherit from parent
                    Set<Module> parentModules = getParent().getActiveModules();
                    for (Module module : parentModules)
                    {
                        //set the default module for the subfolder to be the default module of the parent.
                        Module parentDefault = getParent().getDefaultModule();
                        if(module.equals(parentDefault))
                            setDefaultModule(module);

                        propsWritable.put(module.getName(), Boolean.TRUE.toString());
                    }
                }
                PropertyManager.saveProperties(propsWritable);
            }

            Set<Module> modules = new HashSet<Module>();
            // add all modules found in user preferences:
            if (null != props)
                for (String moduleName : props.keySet())
                {
                    Module module = ModuleLoader.getInstance().getModule(moduleName);
                    if (module != null)
                        modules.add(module);
                }

           // ensure all modules for folder type are added (may have been added after save
            if (!getFolderType().equals(FolderType.NONE))
                modules.addAll(getFolderType().getActiveModules());

           // add all 'always display' modules, remove all 'never display' modules:
           for (Module module : allModules)
           {
              if (module.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_NEVER)
                modules.remove(module);
           }
           _activeModules = Collections.unmodifiableSet(modules);
        }
        return _activeModules;
    }

    public static class ContainerException extends Exception
    {
        public ContainerException(String message)
        {
            super(message);
        }

        public ContainerException(String message, Throwable t)
        {
            super(message, t);
        }
    }
}
