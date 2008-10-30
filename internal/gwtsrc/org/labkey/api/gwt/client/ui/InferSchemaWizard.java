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
package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * User: jgarms
 * Date: Oct 28, 2008
 */
public class InferSchemaWizard extends DialogBox
{
    private final PropertiesEditor propertiesEditor;

    private final ImageButton submitButton;

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

        FileUpload upload = new FileUpload();
        upload.setName("uploadFormElement");
        panel.add(upload);

        submitButton = new ImageButton("Submit", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                submitButton.setEnabled(false);
                form.submit();
                submitButton.setEnabled(true);
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
        }

        public void onSubmitComplete(FormSubmitCompleteEvent event)
        {
            GWTTabLoader loader = new GWTTabLoader(event.getResults());
            if (loader.processTsv(propertiesEditor))
            {
                hide();
            }
        }
    }

    
}
