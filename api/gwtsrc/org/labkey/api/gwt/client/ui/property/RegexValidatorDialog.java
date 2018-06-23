/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Window;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.WidgetUpdatable;

import java.util.List;
import java.util.ArrayList;

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

        prop.setType(PropertyValidatorType.RegEx);
        int row = 0;

        BoundTextBox name = new BoundTextBox("name", prop.getName(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.setName(((TextBox)widget).getText());
            }
        });
        HorizontalPanel namePanel = new HorizontalPanel();
        namePanel.add(new HTML("Name"));
        namePanel.add(new HTML("<span style=\"color:red;\">*</span>"));

        panel.setWidget(row, 0, namePanel);
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
        expression.setCharacterWidth(35);
        expression.setHeight("60px");

        HorizontalPanel expressionLabel = new HorizontalPanel();
        expressionLabel.add(new HTML("Regular Expression"));
        expressionLabel.add(new HTML("<span style=\"color:red;\">*</span>"));
        expressionLabel.add(new HelpPopup("Regular Expression", "The regular expression that this field's value will be evaluated against. " +
                "All regular expressions must be compatible with Java regular expressions as implemented in the <a target=\"_blank\" href=\"http://download.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html#sum\"><code>Pattern</code></a> class."));

        panel.setWidget(row, 0, expressionLabel);
        panel.setWidget(row++, 1, expression);

        BoundTextArea errorMessage = new BoundTextArea("errorMessage", prop.getErrorMessage(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.setErrorMessage(((TextArea)widget).getText());
            }
        });
        errorMessage.setCharacterWidth(35);
        errorMessage.setHeight("60px");

        HorizontalPanel failureMessageLabel = new HorizontalPanel();
        failureMessageLabel.add(new HTML("Error Message"));
        failureMessageLabel.add(new HelpPopup("Error Message", "The message that will be displayed to the user in the event that validation" +
                " fails for this field."));

        panel.setWidget(row, 0, failureMessageLabel);
        panel.setWidget(row++, 1, errorMessage);

        boolean checked = false;
        String failOnMatch = prop.getProperties().get(FAIL_ON_MATCH);
        if (failOnMatch != null)
            checked = Boolean.parseBoolean(failOnMatch);

        BoundCheckBox checkBox = new BoundCheckBox("", checked, new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.getProperties().put(FAIL_ON_MATCH, Boolean.toString(((CheckBox)widget).getValue()));
            }
        });

        HorizontalPanel failureOnMatchLabel = new HorizontalPanel();
        failureOnMatchLabel.add(new HTML("Fail when pattern matches"));
        failureOnMatchLabel.add(new HelpPopup("Fail when pattern matches", "By default, validation will fail if" +
                " the field value does not match the specified regular expression. Check this box if you want validation" +
                " to fail when the pattern matches the field value."));

        panel.setWidget(row, 0, failureOnMatchLabel);
        panel.setWidget(row++, 1, checkBox);

        ImageButton save = new ImageButton("OK");
        save.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                if (validate(prop))
                {
                    _oldProp.copy(prop);
                    getListener().propertyChanged(prop);
                    RegexValidatorDialog.this.hide();
                }
            }
        });

        ImageButton cancel = new ImageButton("Cancel");
        cancel.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent e)
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

    protected boolean validate(GWTPropertyValidator prop)
    {
        List errors = new ArrayList();
        prop.validate(errors);

        if (!errors.isEmpty())
        {
            String s = "";
            for (int i=0 ; i<errors.size() ; i++)
                s += errors.get(i) + "\n";
            Window.alert(s);
            return false;
        }
        return true;
    }
}