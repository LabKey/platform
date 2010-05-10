package org.labkey.api.gwt.client.ui;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 5, 2010
 * Time: 9:13:07 AM
 *
 * This is a marker interface for data bound widgets.
 * onBlur() can be simulated by
 *
 *  validate();
 *  pushValue();
 *
 * but without firing change and blur event
 *
 * NOTE: this was created because of some poor behavior with ext-gwt and selenium.
 * this is a way to make sure the bound controls push their changed data.
 */
public interface BoundWidget
{
    boolean validate(); // see (gxt) Field.validate()
    void pushValue();
    void pullValue();
}
