/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.gwt.client.ui.property;

import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.reports.AbstractChartPanel;

/*
* User: Karl Lum
* Date: Aug 8, 2008
* Time: 3:06:31 PM
*/
public class RegexValidatorDialog extends ValidatorDialog
{
    private static final String FAIL_ON_MATCH = "failOnMatch";

    public RegexValidatorDialog(GWTPropertyValidator prop)
    {
        _oldProp = prop;
        createPanel();
    }

    public RegexValidatorDialog()
    {
        createPanel();
    }

    private void createPanel()
    {
        FlexTable panel = new FlexTable();
        final GWTPropertyValidator prop = new GWTPropertyValidator();

        if (_oldProp != null)
            prop.copy(_oldProp);

        prop.setType(GWTPropertyValidator.TYPE_REGEX);
        int row = 0;

        BoundTextBox name = new BoundTextBox("name", prop.getName(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.setName(((TextBox)widget).getText());
            }
        });
        panel.setWidget(row, 0, new HTML("Name"));
        panel.setWidget(row++, 1, name);

        BoundTextBox description = new BoundTextBox("description", prop.getDescription(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.setDescription(((TextBox)widget).getText());
            }
        });
        panel.setWidget(row, 0, new HTML("Description"));
        panel.setWidget(row++, 1, description);

        BoundTextArea expression = new BoundTextArea("expression", prop.getExpression(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.setExpression(((TextArea)widget).getText());
            }
        });
        expression.setCharacterWidth(25);
        expression.setHeight("50px");
        panel.setWidget(row, 0, new HTML("Regular Expression"));
        panel.setWidget(row++, 1, expression);

        BoundTextArea errorMessage = new BoundTextArea("errorMessage", prop.getErrorMessage(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.setErrorMessage(((TextArea)widget).getText());
            }
        });
        errorMessage.setCharacterWidth(25);
        errorMessage.setHeight("50px");

        panel.setWidget(row, 0, new HTML("Failure Message"));
        panel.setWidget(row++, 1, errorMessage);

        boolean checked = false;
        String failOnMatch = (String)prop.getProperties().get(FAIL_ON_MATCH);
        if (failOnMatch != null)
            checked = new Boolean(failOnMatch).booleanValue();

        BoundCheckBox checkBox = new BoundCheckBox("", checked, new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.getProperties().put(FAIL_ON_MATCH, Boolean.toString(((CheckBox)widget).isChecked()));
            }
        });
        panel.setWidget(row, 0, new HTML("Fail when pattern matches"));
        panel.setWidget(row++, 1, checkBox);

        ImageButton save = new ImageButton("OK");
        save.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                _oldProp.copy(prop);
                getListener().propertyChanged(prop);
                RegexValidatorDialog.this.hide();
            }
        });

        ImageButton cancel = new ImageButton("Cancel");
        cancel.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                RegexValidatorDialog.this.hide();
            }
        });

        HorizontalPanel hp = new HorizontalPanel();
        hp.add(save);
        hp.add(new HTML("&nbsp;"));
        hp.add(cancel);
        panel.setWidget(row++, 1, hp);

        setText("Regular Expression Validator");
        setWidget(panel);
    }
}