package org.labkey.api.gwt.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.CheckBox;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.IPropertyWrapper;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 29, 2010
 * Time: 11:36:09 AM
 */
public class BoundCheckBox extends CheckBox
{
    public BoundCheckBox(String id, final BooleanProperty property, final DirtyCallback dirtyCallback)
    {
        super();
        DOM.setElementAttribute(getElement(), "id", id);

        setValue(property.booleanValue());
        
        addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                property.set(Boolean.TRUE == getValue());
                if (dirtyCallback != null)
                {
                    dirtyCallback.setDirty(true);
                }
            }
        });
    }
}