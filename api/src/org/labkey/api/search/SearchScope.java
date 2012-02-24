package org.labkey.api.search;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

/**
  * User: adam
  * Date: 2/18/12
  * Time: 2:06 PM
  */
public enum SearchScope
{
    All() {
        @Override
        public Container getRoot(Container c)
        {
            return ContainerManager.getRoot();
        }
    },
    Project() {
        @Override
        public Container getRoot(Container c)
        {
            return c.getProject();
        }
    },
    FolderAndSubfolders() {
        @Override
        public Container getRoot(Container c)
        {
            return c;
        }
    },
    Folder() {
        @Override
        public Container getRoot(Container c)
        {
            return c;
        }

        @Override
        public boolean isRecursive()
        {
            return false;
        }
    };

    public abstract Container getRoot(Container c);

    public boolean isRecursive()
    {
        return true;
    }
}
