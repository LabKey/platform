package org.labkey.api.gwt.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.RadioButton;
import org.labkey.api.gwt.client.util.IPropertyWrapper;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 30, 2010
 * Time: 1:53:26 PM
 */
public class BoundRadioButton extends RadioButton
{
    final Object _boundValue;
    final IPropertyWrapper _prop;
    
    public BoundRadioButton(String name, String label, IPropertyWrapper prop)
    {
        this(name, label, prop, Boolean.TRUE);
    }

    public BoundRadioButton(String name, String label, IPropertyWrapper prop, Object value)
    {
        super(name, label);
        this._boundValue = value;
        this._prop = prop;

        if (value.equals(prop.get()))
            setValue(true);
        
        this.addClickHandler(new ClickHandler(){
            public void onClick(ClickEvent event)
            {
                _prop.set(_boundValue);
            }
        });
    }
}
