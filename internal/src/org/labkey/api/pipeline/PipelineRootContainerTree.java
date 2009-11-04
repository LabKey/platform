package org.labkey.api.pipeline;

import org.labkey.api.util.ContainerTree;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;

import java.util.Map;

/**
 * A ContainerTree subclass that lets its own subclasses know if a container has a pipeline root set.
 * It makes the currently correct assumption that subfolders inherit pipeline roots from their parents if not set.
 * User: jeckels
 * Date: Oct 27, 2009
 */
public abstract class PipelineRootContainerTree extends ContainerTree
{
    private Map<Container, PipeRoot> _roots = PipelineService.get().getAllPipelineRoots();

    public PipelineRootContainerTree(User user, ActionURL url)
    {
        super("/", user, ACL.PERM_INSERT, url);
    }
    
    /** Look up the chain until we find one or reach the root */
    private boolean hasPipelineRoot(Container c)
    {
        do
        {
            if (_roots.containsKey(c))
            {
                return true;
            }
            c = c.getParent();
        }
        while (c != null && !c.isRoot());

        return false;
    }

    protected final void renderCellContents(StringBuilder html, Container c, ActionURL url)
    {
        renderCellContents(html, c, url, hasPipelineRoot(c));
    }

    protected void defaultRenderCellContents(StringBuilder html, Container c, ActionURL url)
    {
        super.renderCellContents(html, c, url);
    }

    /** Subclasses must implement this overloaded version */
    protected abstract void renderCellContents(StringBuilder html, Container c, ActionURL url, boolean hasPipelineRoot);
}
