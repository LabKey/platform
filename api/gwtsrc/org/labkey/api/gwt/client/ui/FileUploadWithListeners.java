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

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.*;

/**
 * User: jgarms
 * Date: Oct 30, 2008
 */
public class FileUploadWithListeners extends FileUpload implements SourcesClickEvents, SourcesChangeEvents
{
    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
    private ClickListenerCollection clickListeners;

    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
    private ChangeListenerCollection changeListeners;

    public FileUploadWithListeners()
    {
        super();
        sinkEvents(Event.ONCLICK | Event.ONCHANGE);
    }

    protected FileUploadWithListeners(Element element)
    {
        super(element);
    }

    public void addClickListener(ClickListener listener)
    {
        if (clickListeners == null)
        {
            clickListeners = new ClickListenerCollection();
            sinkEvents(Event.ONCLICK);
        }
        clickListeners.add(listener);
    }

    public void removeClickListener(ClickListener listener)
    {
        if (clickListeners != null)
        {
            clickListeners.remove(listener);
        }
    }

    public void addChangeListener(ChangeListener listener)
    {
        if (changeListeners == null)
        {
            changeListeners = new ChangeListenerCollection();
            sinkEvents(Event.ONCHANGE);
        }
        changeListeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener)
    {
        if (changeListeners != null)
                changeListeners.remove(listener);
    }

    public void onBrowserEvent(Event event)
    {
        if (DOM.eventGetType(event) == Event.ONCLICK)
        {
            if (clickListeners != null)
                clickListeners.fireClick(this);
        }
        else if (DOM.eventGetType(event) == Event.ONCHANGE)
        {
            if (changeListeners != null)
                    changeListeners.fireChange(this);
        }
    }

}
