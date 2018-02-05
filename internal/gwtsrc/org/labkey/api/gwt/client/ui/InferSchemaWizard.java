/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;
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
    private Label statusLabel;

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
        panel.setSpacing(6);
        form.setWidget(panel);

        Hidden csrfToken = new Hidden("X-LABKEY-CSRF", ServiceUtil.getCsrfToken());
        panel.add(csrfToken);

        fileButton = new RadioButton("source", "file");
        fileButton.setText("Upload Excel or text file (.xls, .xlsx, .tsv, .txt, .csv):");
        fileButton.setFormValue("file");
        fileButton.setValue(true);
        panel.add(fileButton);

        upload = new FileUploadWithListeners();
        upload.setName("uploadFormElement");
        upload.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                fileButton.setValue(true);
            }
        });
        panel.add(upload);

        tsvButton = new RadioButton("source", "tsv");
        tsvButton.setText("Paste tab-delimited data:");
        tsvButton.setFormValue("tsv");
        panel.add(tsvButton);
        tsvTextArea = new TextArea();
        tsvTextArea.setName("tsvText");
        tsvTextArea.setCharacterWidth(80);
        tsvTextArea.setVisibleLines(8);
        tsvTextArea.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                tsvButton.setValue(true);
            }
        });
        panel.add(tsvTextArea);

        submitButton = new ImageButton("Submit", new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                submitButton.setEnabled(false);
                form.submit();
            }
        });

        Label warning = new Label("WARNING: This will delete all existing fields and data!");
        warning.setStyleName("labkey-error", true);
        panel.add(warning);

        ImageButton cancelButton = new ImageButton("Cancel", new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                InferSchemaWizard.this.hide();
            }
        });

        HorizontalPanel buttonPanel = new HorizontalPanel();
        buttonPanel.add(submitButton);
        buttonPanel.add(cancelButton);
        statusLabel = new Label();
        buttonPanel.add(statusLabel);

        panel.add(buttonPanel);

        setWidget(form);
    }

    private class UploadFormHandler implements FormHandler
    {
        public void onSubmit(FormSubmitEvent event)
        {
            if (tsvButton.getValue())
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
                result = result.substring("Success:".length()); // trim the "Success:" prefix
                result = result.replaceAll("<br>|<BR>", "\n");
                result = result.replaceAll("<hr>|<HR>", "\t");
                GWTTabLoader loader = new GWTTabLoader(result);
                if (loader.processTsv(propertiesEditor))
                {
                    statusLabel.setText("");
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
