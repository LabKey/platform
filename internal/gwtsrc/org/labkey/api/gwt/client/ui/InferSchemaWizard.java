/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;

/**
 * User: jgarms
 * Date: Oct 28, 2008
 */
public class InferSchemaWizard extends DialogBox
{
    private final PropertiesEditor propertiesEditor;

    private final ImageButton submitButton;
    private RadioButton fileButton;
    private RadioButton tsvButton;
    private TextArea tsvTextArea;
    private FileUploadWithListeners upload;
    private HTML statusLabel;

    public InferSchemaWizard(PropertiesEditor propertiesEditor)
    {
        this.propertiesEditor = propertiesEditor;

        final FormPanel form = new FormPanel();
        
        String url = PropertyUtil.getRelativeURL("inferProperties", "property");
        form.setAction(url);

        form.setEncoding(FormPanel.ENCODING_MULTIPART);
        form.setMethod(FormPanel.METHOD_POST);

        form.addFormHandler(new UploadFormHandler());

        VerticalPanel panel = new VerticalPanel();
        form.setWidget(panel);

        fileButton = new RadioButton("source", "file");
        fileButton.setText("File:");
        fileButton.setFormValue("file");
        fileButton.setChecked(true);
        panel.add(fileButton);

        upload = new FileUploadWithListeners();
        upload.setName("uploadFormElement");
        upload.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                fileButton.setChecked(true);
            }
        });
        panel.add(upload);

        tsvButton = new RadioButton("source", "tsv");
        tsvButton.setText("Tab-delimited data:");
        tsvButton.setFormValue("tsv");
        panel.add(tsvButton);
        tsvTextArea = new TextArea();
        tsvTextArea.setName("tsvText");
        tsvTextArea.setSize("400", "200");
        tsvTextArea.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                tsvButton.setChecked(true);
            }
        });
        panel.add(tsvTextArea);

        statusLabel = new HTML("&nbsp;");
        panel.add(statusLabel);

        submitButton = new ImageButton("Submit", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                submitButton.setEnabled(false);
                form.submit();
            }
        });

        ImageButton cancelButton = new ImageButton("Cancel", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                InferSchemaWizard.this.hide();
            }
        });

        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.add(submitButton);
        buttonPanel.add(cancelButton);

        panel.add(buttonPanel);

        setWidget(form);
    }

    private class UploadFormHandler implements FormHandler
    {
        public void onSubmit(FormSubmitEvent event)
        {
            if (tsvButton.isChecked())
            {
                String tsv = StringUtils.trimToNull(tsvTextArea.getText());
                if (tsv == null)
                {
                    Window.alert("Please enter some tab-delimited text to upload");
                    event.setCancelled(true);
                    submitButton.setEnabled(true);
                    return;
                }
            }
            else
            {
                if(upload.getFilename().length() == 0)
                {
                    Window.alert("Please select a file to upload");
                    event.setCancelled(true);
                    submitButton.setEnabled(true);
                    return;
                }
            }
            statusLabel.setText("Processing...");
        }

        public void onSubmitComplete(FormSubmitCompleteEvent event)
        {
            String result = event.getResults();
            if (result.startsWith("Success:"))
            {
                result = result.substring(8); // trim the "Success:" prefix
                GWTTabLoader loader = new GWTTabLoader(result);
                if (loader.processTsv(propertiesEditor))
                {
                    statusLabel.setText("&nbsp;");
                    hide();
                }
            }
            else
            {
                statusLabel.setText("");
                Window.alert(result);
                submitButton.setEnabled(true);
            }
        }
    }
    
}
