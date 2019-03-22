/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.*;

/**
 * User: jeckels
 * Date: Apr 24, 2007
 */
public abstract class TextBoxDialogBox extends DialogBox
{
    private TextBox _textBox;

    public TextBoxDialogBox(String title, String label)
    {
        super(false);

        setText(title);

        _textBox = new TextBox();
        _textBox.addKeyboardListener(new KeyboardListenerAdapter()
        {
            public void onKeyDown(Widget sender, char keyCode, int modifiers)
            {
                if (keyCode == KeyCodes.KEY_ENTER)
                {
                    commit();
                }
                else if (keyCode == KeyCodes.KEY_ESCAPE)
                {
                    hide();
                }
            }
        });

        VerticalPanel contentPanel = new VerticalPanel();
        contentPanel.setHorizontalAlignment(VerticalPanel.ALIGN_CENTER);

        HorizontalPanel inputPanel = new HorizontalPanel();
        inputPanel.setSpacing(5);

        inputPanel.add(new Label(label + ": "));
        inputPanel.add(_textBox);

        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.setSpacing(5);

        ImageButton okButton = new ImageButton("OK");
        buttonPanel.add(okButton);
        okButton.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                commit();
            }
        });

        ImageButton cancelButton = new ImageButton("Cancel");
        cancelButton.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                hide();
            }
        });
        buttonPanel.add(cancelButton);

        contentPanel.add(inputPanel);
        contentPanel.add(buttonPanel);

        setWidget(contentPanel);
        _textBox.setFocus(true);
    }

    private void commit()
    {
        String propName = _textBox.getText().trim();

        if (commit(propName))
        {
            hide();
        }
    }

    public void show()
    {
        WindowUtil.centerDialog(this);
        super.show();
        _textBox.selectAll();
        _textBox.setFocus(true);
        WindowUtil.centerDialog(this);
    }

    public void show(String defaultValue)
    {
        _textBox.setText(defaultValue);
        show();
    }

    /** Do something useful with the value. If it's not valid, return false and the dialog won't go away.
     */
    protected abstract boolean commit(String value);

}
