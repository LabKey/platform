package org.labkey.plate.designer.client;

/**
 * User: jeckels
 * Date: Apr 19, 2007
 */
public class PlatePropertyPanel extends PropertyPanel
{
    public PlatePropertyPanel(TemplateView templateView)
    {
        super(templateView);
        redraw(templateView.getPlate().getPlateProperties());
    }
}
