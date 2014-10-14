/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.filecontent.designer.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.*;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.ServiceUtil;

import java.util.List;

/**
 * User: klum
 * Date: Mar 24, 2010
 * Time: 5:56:06 PM
 */
public class FilePropertiesDesigner implements EntryPoint, Saveable<GWTDomain>
{
    private RootPanel _root;
    private Label _loading;
    private PropertiesEditor _properties;
    private GWTDomain _domain;
    private String _returnURL;
    private String _cancelURL;
    private String _typeURI;
    private String _domainName;
    private SaveButtonBar _buttons;

    private class FilePropertiesSaveable implements Saveable<GWTDomain>
    {
        private Saveable<GWTDomain> _domainSavable;

        public FilePropertiesSaveable(Saveable<GWTDomain> domainsSaveable)
        {
            _domainSavable = domainsSaveable;
        }

        @Override
        public String getCurrentURL()
        {
            return PropertyUtil.getCurrentURL();
        }

        public void save()
        {
            _domainSavable.save();
        }

        public void save(final SaveListener<GWTDomain> gwtDomainSaveListener)
        {
            _domainSavable.save(new SaveListener<GWTDomain>()
            {
                public void saveSuccessful(GWTDomain domain, String designerUrl)
                {
                }
            });
        }

        public void cancel()
        {
            _domainSavable.cancel();
        }

        public void finish()
        {
            _domainSavable.finish();
        }

        public boolean isDirty()
        {
            return _domainSavable.isDirty();
        }
    }

    public void onModuleLoad()
    {
        _root = RootPanel.get("org.labkey.filecontent.designer.FilePropertiesDesigner-Root");

        _returnURL = PropertyUtil.getReturnURL();
        _cancelURL = PropertyUtil.getCancelURL();
        _typeURI = PropertyUtil.getServerProperty("typeURI");
        _domainName = PropertyUtil.getServerProperty("domainName");

        _loading = new Label("Loading...");

        _properties = new PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>(_root, new FilePropertiesSaveable(this), getService(), null);

        _root.add(_loading);
        asyncGetDomain(_typeURI, _domainName);
    }

    private void showUI()
    {
        VerticalPanel vPanel = new VerticalPanel();
        vPanel.add(_properties.getWidget());

        final WebPartPanel panel = new WebPartPanel("File Properties", vPanel);
        panel.setWidth("100%");

        _buttons = new SaveButtonBar(this);
        _properties.addChangeHandler(new ChangeHandler()
        {
            public void onChange(ChangeEvent e)
            {
                setDirty(true);
            }
        });

        _root.remove(_loading);
        _root.add(_buttons);
        _root.add(panel);

        setDirty(false);
    }

    private void setDirty(boolean dirty)
    {
        _buttons.setAllowSave(dirty);
    }

    private FilePropertiesServiceAsync _service = null;
    private FilePropertiesServiceAsync getService()
    {
        if (_service == null)
        {
            _service = GWT.create(FilePropertiesService.class);
            ServiceUtil.configureEndpoint(_service, "filePropertiesService");
        }
        return _service;
    }

    @Override
    public String getCurrentURL()
    {
        return PropertyUtil.getCurrentURL();
    }

    @Override
    public void save()
    {
        save(new SaveListener<GWTDomain>(){
            public void saveSuccessful(GWTDomain domain, String designerUrl)
            {
                _root.clear();
                _root.add(_loading);
                asyncGetDomain(_typeURI, _domainName);
            }
        });
    }

    @Override
    public void save(final SaveListener<GWTDomain> listener)
    {
        List errors = _properties.validate();
        if (null != errors && !errors.isEmpty())
        {
            String s = "";
            for (Object error : errors)
                s += error + "\n";
            Window.alert(s);
            //_submitButton.setEnabled(true);
            return;
        }

        GWTDomain edited = _properties.getUpdates();
        getService().updateDomainDescriptor(_domain, edited, new ErrorDialogAsyncCallback<List<String>>("Save failed")
        {
            public void onSuccess(List<String> errors)
            {
                if (errors.isEmpty())
                {
                    if (listener != null)
                        listener.saveSuccessful(_domain, PropertyUtil.getCurrentURL());
                }
                else
                {
                    String s = "";
                    for (String error : errors)
                        s += error + "\n";
                    Window.alert(s);
                }
            }
        });
    }

    @Override
    public void cancel()
    {
        if (_cancelURL != null)
            navigate(_cancelURL);
        else
            back();
    }

    @Override
    public void finish()
    {
        save(new SaveListener<GWTDomain>(){
            public void saveSuccessful(GWTDomain domain, String designerUrl)
            {
                if (null == _returnURL || _returnURL.length() == 0)
                    cancel();
                else
                    navigate(_returnURL);
            }
        });
    }

    @Override
    public boolean isDirty()
    {
        return _properties.isDirty();
    }

    void asyncGetDomain(final String domainURI, final String domainName)
    {
        getService().getDomainDescriptor(domainURI, new ErrorDialogAsyncCallback<GWTDomain>() {
            public void handleFailure(String message, Throwable caught)
            {
                _loading.setText("ERROR: " + message);
            }

            public void onSuccess(GWTDomain domain)
            {
                if (domain == null)
                {
                    domain = new GWTDomain();

                    domain.setDomainURI(domainURI);
                    domain.setName(domainName);

/*
                    String message = "Could not find " + domainURI;
                    Window.alert(message);
                    _loading.setText("ERROR: " + message);
                    return;
*/
                }
                _domain = domain;
                _properties.init(new GWTDomain(_domain));
                if (null == _domain.getFields() || _domain.getFields().size() == 0)
                    _properties.addField(new GWTPropertyDescriptor());
                showUI();
            }
        });
    }

    public static native void navigate(String url) /*-{
      $wnd.location.href = url;
    }-*/;


    public static native void back() /*-{
        $wnd.history.back();
    }-*/;
}
