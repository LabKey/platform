package org.labkey.api.view;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jun 20, 2007
 * Time: 9:25:42 PM
 */
public class PopupMenuView extends JspView<NavTree>
{
    public enum Align
    {
        LEFT,
        RIGHT
    }

    private NavTree navTree;
    private String elementId;
    private Align align = Align.LEFT;

    public PopupMenuView(String elementId, NavTree navTree)
    {
        super("/org/labkey/api/view/PopupMenu.jsp", navTree);
        this.elementId = elementId;
        this.navTree = navTree;
    }


    public NavTree getNavTree()
    {
        return navTree;
    }

    public void setNavTree(NavTree navTree)
    {
        this.navTree = navTree;
    }

    public String getElementId()
    {
        return elementId;
    }

    public void setElementId(String elementId)
    {
        this.elementId = elementId;
    }

    public Align getAlign()
    {
        return align;
    }

    public void setAlign(Align align)
    {
        this.align = align;
    }
}
