package org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.TextArea;
import org.labkey.study.designer.client.ActivatingLabel;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Dec 21, 2006
 * Time: 12:10:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class DescriptionWidget extends ActivatingLabel
{
    public DescriptionWidget()
    {
        super(new TextArea(), "Click to edit description");
        getWidget().setWidth("60em");
    }
}
