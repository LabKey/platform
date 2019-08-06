/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.RadioButton;
import com.sencha.gxt.core.client.util.ToggleGroup;
import com.sencha.gxt.widget.core.client.container.VerticalLayoutContainer;
import com.sencha.gxt.widget.core.client.form.PropertyEditor;
import com.sencha.gxt.widget.core.client.form.TriggerField;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.util.StringUtils;

import java.text.ParseException;
import java.util.ArrayList;

/**
 * User: matthewb
 * Date: Apr 23, 2007
 * Time: 5:16:30 PM
 *
 *
 * Concept picker is a UI for editing a subset of PropertyURI fields.
 *
 *    RangeURI      (roughly the storage type of the field, file/attachment are oddities)
 *    ConceptURI    Marker type e.g. Subject or VisitDate (may imply storage type)
 *    Lookup        Lookup (container,schema,table), must match storage type of PK
 */
public class ConceptPicker extends TriggerField<ConceptPicker.ConceptType>
{
    final private LookupServiceAsync _lookupService;

    private boolean isRangeEditable = true;
    private boolean allowFileLinkProperties = true;
    private boolean allowAttachmentProperties = true;
    private boolean allowFlagProperties = false;

    public ConceptPicker(LookupServiceAsync lookupService, String name, GWTPropertyDescriptor initial)
    {
        super(conceptTypePropertyEditor);
        sinkEvents(Event.ONCHANGE);
        _lookupService = lookupService;
        setEditable(false);
        setName(name);
        // adjustSize==true causes getComputesStyle(), SLOW ON FIREFOX
        this.adjustSize = false;
        setHeight(22);

        ConceptType type = fromPropertyDescriptor(initial);
        setValue(type);
        addTriggerClickHandler(event -> showEditor(ConceptPicker.this));
    }

    public void setValue(ConceptType t, boolean fireEvents)
    {
        assert t != genericLookup;
        if (t != null)
            setToolTip(t == null ? null : StringUtils.filter(t.getDisplay()));
        super.setValue(t, fireEvents);
    }

    public static String getDisplayString(GWTPropertyDescriptor pd)
    {
        ConceptType t = fromPropertyDescriptor(pd);
        return null==t ? "" : t.getDisplay();
    }


    public void setIsRangeEditable(boolean b)
    {
        this.isRangeEditable = b;
        setEditable(b);
    }


    public void setAllowFileLinkProperties(boolean allowFileLinkProperties)
    {
        this.allowFileLinkProperties = allowFileLinkProperties;
    }


    public void setAllowAttachmentProperties(boolean allowAttachmentProperties)
    {
        this.allowAttachmentProperties = allowAttachmentProperties;
    }


    public void setAllowFlagProperties(boolean allowFlagProperties)
    {
        this.allowFlagProperties = allowFlagProperties;
    }

    private static void _copy(GWTPropertyDescriptor from, GWTPropertyDescriptor to)
    {
        to.setRangeURI(from.getRangeURI());
        to.setConceptURI(from.getConceptURI());
        to.setLookupContainer(from.getLookupContainer());
        to.setLookupSchema(from.getLookupSchema());
        to.setLookupQuery(from.getLookupQuery());
    }
    

    private static class ConceptTypeRadio extends RadioButton
    {
        ConceptType _type;

        ConceptTypeRadio(String name, ConceptType type)
        {
            super(name, type.getDisplay());
            _type = type;
        }
    }



    private static class EditorWindow extends DialogBox
    {
        ArrayList<ConceptTypeRadio> allradios = new ArrayList<ConceptTypeRadio>();
        ConceptTypeRadio _lookupRadio = null;
        ConceptTypeRadio _selectedRadio = null;
        ConceptPicker _current;
        LookupServiceAsync _service;
        LookupEditorPanel _lookupEditorPanel;


        EditorWindow(LookupServiceAsync lookupService)
        {
            super();
            _service = lookupService;
            setModal(true);
            setWidth("400");
            setText("Choose Field Type");

            // SIMPLE
            VerticalLayoutContainer group = new VerticalLayoutContainer();
            group.add(new ConceptTypeRadio("rangeURI", stringType));
            group.add(new ConceptTypeRadio("rangeURI", multilineType));
            group.add(new ConceptTypeRadio("rangeURI", booleanType));
            group.add(new ConceptTypeRadio("rangeURI", integerType));
            group.add(new ConceptTypeRadio("rangeURI", doubleType));
            group.add(new ConceptTypeRadio("rangeURI", datetimeType));
            group.add(new ConceptTypeRadio("rangeURI", flagType));
            group.add(new ConceptTypeRadio("rangeURI", fileType));
            group.add(new ConceptTypeRadio("rangeURI", attachmentType));
            group.add(new ConceptTypeRadio("rangeURI", userType));
            group.add(new ConceptTypeRadio("rangeURI", subjectType));

            // LOOKUP
            _lookupRadio = new ConceptTypeRadio("rangeURI", genericLookup);
            _lookupEditorPanel = new LookupEditorPanel(lookupService, null, true);
            group.add(_lookupRadio);

            // need to add all the radio buttons to a toggle group else the lookup value change handler
            // won't fire on deselect
            ToggleGroup toggleGroup = new ToggleGroup();
            for (int i=0; i < group.getWidgetCount(); i++)
            {
                allradios.add((ConceptTypeRadio)group.getWidget(i));
                toggleGroup.add((ConceptTypeRadio)group.getWidget(i));
            }

            toggleGroup.addValueChangeHandler(event -> {
                if (event.getValue().getValue() == Boolean.TRUE)
                {
                    _selectedRadio = (ConceptTypeRadio)event.getValue();
                    _lookupEditorPanel.setEnabled(_selectedRadio == _lookupRadio);
                }
            });
            group.add(_lookupEditorPanel);

            Button btn = new Button("Apply");
            btn.addClickHandler(event -> apply(true));

            Button cancel = new Button("Cancel");
            cancel.addClickHandler(event -> hide());

            HorizontalPanel buttonPanel = new HorizontalPanel();
            buttonPanel.add(btn);
            buttonPanel.add(cancel);
            group.add(buttonPanel);

            add(group);
        }

        @Override
        public void hide()
        {
            super.hide();
        }

        void init(ConceptPicker picker)
        {
            _current = picker;
            ConceptType type = picker.getValue();

            // this is a funny swap back to GWTPopertyDescriptor...
            GWTPropertyDescriptor pd = new GWTPropertyDescriptor();
            if (null != type)
                type.apply(pd);

            // Be sure to set the new key type before calling setValue() so that it initializes correctly
            _lookupEditorPanel.setCurrentType(picker.isRangeEditable ? null : null == type ? null : type.getPropertyType());
            _lookupEditorPanel.setValue(pd);

            for (int i=allradios.size()-1 ; i>=0 ; i--)
            {
                ConceptTypeRadio r = allradios.get(i);
                if (null != pd && r._type.matches(pd))
                {
                    r.setValue(true, true);
                    _selectedRadio = r;
                    r.setFocus(true);
                    pd = null;
                }
                if (r._type == attachmentType)
                    r.setEnabled(picker.isRangeEditable && picker.allowAttachmentProperties);
                else if (r._type == fileType)
                    r.setEnabled(picker.isRangeEditable && picker.allowFileLinkProperties);
                else if (r._type instanceof LookupConceptType)
                    r.setEnabled(picker.isRangeEditable || null == type || type.getPropertyType().isLookupType());
                else if (r._type.getPropertyType() == PropertyType.expMultiLine || r._type.getPropertyType() == PropertyType.xsdString)
                    // Allow toggling between multi-line text and regular text types
                    r.setEnabled(picker.isRangeEditable || null == type || type.getPropertyType() == PropertyType.expMultiLine || type.getPropertyType() == PropertyType.xsdString);
                else if (r._type == flagType)
                    r.setEnabled(picker.isRangeEditable && picker.allowFlagProperties);
                else
                    r.setEnabled(picker.isRangeEditable || null == type || type.getPropertyType() == r._type.getPropertyType());
            }
        }


        // push from the EditorPanel to the TriggerField
        private void apply(final boolean hideOnSuccess)
        {
            if (_selectedRadio == _lookupRadio)
            {
                final String folder = _lookupEditorPanel.getContainer();
                final String schema = _lookupEditorPanel.getSchemaName();
                final String table = _lookupEditorPanel.getTableName();
                final String typeURI = _lookupEditorPanel.getTypeURI();
                PropertyType type = null == typeURI ? null : PropertyType.fromURI(typeURI);

//                _log("apply " + folder + " " + schema + " " + table + " " + typeURI);

                if (_empty(schema) || _empty(table))
                {
                    Window.alert("Schema name and table name must not be empty");
                    return;
                }

                if (!_current.isRangeEditable && null != _current.getValue())
                    type = _current.getValue().getPropertyType();

                if (null == type)
                {
                    Window.alert("Type was not set correctly");
                    return;
                }

                _current.setValue(new LookupConceptType(type, folder, schema, table), true);
                if (hideOnSuccess)
                    hide();
            }
            else // !lookup
            {
                _current.setValue(_selectedRadio._type, true);
                if (hideOnSuccess)
                    hide();
            }
        }
    }


    static EditorWindow _editorInstance = null;
    
    EditorWindow getEditor()
    {
        if (null == _editorInstance)
            _editorInstance =  new EditorWindow(_lookupService);
        return _editorInstance;
    }


    void showEditor(ConceptPicker picker)
    {
        EditorWindow w = getEditor();
        w.init(picker);
        w.show();
        w.center();
    }


    /**
     * ConceptType is a wrapper for a PropertyDescriptor with a matches and apply method
     */

    public abstract static class ConceptType
    {
        abstract String getDisplay();
        abstract PropertyType getPropertyType();
        abstract boolean matches(GWTPropertyDescriptor pd);
        abstract void apply(GWTPropertyDescriptor pd);
    }

    public static class BaseConceptType extends ConceptType
    {
        GWTPropertyDescriptor _pd = new GWTPropertyDescriptor();
        PropertyType _propertyType;
        String _text;
        
        BaseConceptType(PropertyType type)
        {
            _pd.setRangeURI(type.toString());
            _propertyType = type;
            _text = type.getDisplay();
        }
        BaseConceptType(String rangeURI)
        {
            _pd.setRangeURI(rangeURI);
            _propertyType = null==rangeURI ? null : PropertyType.fromName(rangeURI);
            _text = _default(null==_propertyType?null:_propertyType.getDisplay(), rangeURI);
        }

        BaseConceptType(String rangeURI, String text)
        {
            _pd.setRangeURI(rangeURI);
            _propertyType = PropertyType.fromName(rangeURI);
            _text = text;
        }

        BaseConceptType(String rangeURI, String text, String conceptURI)
        {
            _pd.setRangeURI(rangeURI);
            _pd.setConceptURI(conceptURI);
            _propertyType = PropertyType.fromName(rangeURI);
            _text = text;
        }

        BaseConceptType(GWTPropertyDescriptor pd, String text)
        {
            _copy(pd, _pd);
            _propertyType = PropertyType.fromName(_pd.getRangeURI());
            _text = text;
        }

        PropertyType getPropertyType()
        {
            return _propertyType;
        }

        @Override
        String getDisplay()
        {
            return _text;
        }

        @Override
        boolean matches(GWTPropertyDescriptor pd)
        {
            if (!_empty(_pd.getRangeURI()) && !_pd.getRangeURI().equalsIgnoreCase(pd.getRangeURI()))
                return false;
            if (!_empty(pd.getLookupSchema()) && null != _pd.getLookupSchema())
            {
                if (!_pd.getLookupSchema().equalsIgnoreCase(pd.getLookupSchema()) ||
                    !_pd.getLookupQuery().equalsIgnoreCase(pd.getLookupQuery()))
                    return false;
                // no custom types with lookupcontainer yet.
                if (!_empty(pd.getLookupContainer()))
                    return false;
            }
            if (!_empty(_pd.getConceptURI()) && !_pd.getConceptURI().equalsIgnoreCase(pd.getConceptURI()))
                return false;
            return true;
        }

        @Override
        void apply(GWTPropertyDescriptor pd)
        {
            _copy(_pd,pd);
        }
    }

    public static class LookupConceptType extends BaseConceptType
    {
        LookupConceptType(String rangeURI, String folder, String schema, String table)
        {
            super(rangeURI);
            _pd.setLookupContainer(folder);
            _pd.setLookupSchema(schema);
            _pd.setLookupQuery(table);
        }
        LookupConceptType(PropertyType pt, String folder, String schema, String table)
        {
            super(pt.getURI());
            _pd.setLookupContainer(folder);
            _pd.setLookupSchema(schema);
            _pd.setLookupQuery(table);
        }

        LookupConceptType(GWTPropertyDescriptor pd)
        {
            super(pd, null);
        }

        @Override
        String getDisplay()
        {
            PropertyType t = _empty(_pd.getRangeURI()) ? null : PropertyType.fromName(_pd.getRangeURI());
            if (null == t)
                return _pd.getLookupSchema() + "." + _pd.getLookupQuery();
            else
                return _pd.getLookupSchema() + "." + _pd.getLookupQuery() + " (" + t.getShortName() + ")";
        }
    }

    public static final ConceptType stringType = new BaseConceptType(PropertyType.xsdString);
    public static final ConceptType multilineType = new BaseConceptType(PropertyType.expMultiLine);
    public static final ConceptType booleanType = new BaseConceptType(PropertyType.xsdBoolean);
    public static final ConceptType integerType = new BaseConceptType(PropertyType.xsdInt);
    public static final ConceptType doubleType = new BaseConceptType(PropertyType.xsdDouble);
    public static final ConceptType datetimeType = new BaseConceptType(PropertyType.xsdDateTime);
    public static final ConceptType attachmentType = new BaseConceptType(PropertyType.expAttachment);
    public static final ConceptType fileType = new BaseConceptType(PropertyType.expFileLink);
    public static final ConceptType subjectType = new BaseConceptType(PropertyType.xsdString.toString(), "Subject/Participant (String)", PropertyType.PARTICIPANT_CONCEPT_URI);
    public static final ConceptType userType = new BaseConceptType(PropertyType.xsdInt.toString(), "User")
    {
        @Override
        boolean matches(GWTPropertyDescriptor pd)
        {
            String schema = pd.getLookupSchema();
            schema = null == schema ? "" : schema.toLowerCase();
            String query = pd.getLookupQuery();
            query = null == query ? "" : query.toLowerCase();
            return super.matches(pd) && "core".equals(schema) && ("siteusers".equals(query) || "users".equals(query));
        }

        @Override
        void apply(GWTPropertyDescriptor pd)
        {
            super.apply(pd);
            pd.setLookupContainer("");
            pd.setLookupSchema("core");
            pd.setLookupQuery("users");
        }
    };

    // generic lookup (used by init)
    private static final ConceptType genericLookup = new LookupConceptType((String)null, null, null, null)
    {
        @Override
        boolean matches(GWTPropertyDescriptor pd)
        {
            return !_empty(pd.getLookupSchema()) && !_empty(pd.getLookupQuery());
        }

        @Override
        String getDisplay()
        {
            return "Lookup";
        }
    };

    // this is really just a string with a special display column,
    // so I'm just set a special concept type, and using xsdString for base type
    public static final ConceptType flagType = new BaseConceptType(PropertyType.xsdString.toString(), "Flag (String)", PropertyType.expFlag.getURI());


    static ConceptType fromPropertyDescriptor(GWTPropertyDescriptor pd)
    {
        if (userType.matches(pd))
            return userType;
        if (subjectType.matches(pd))
            return subjectType;
        if (flagType.matches(pd))
            return flagType;
        if (!_empty(pd.getLookupQuery()) && !_empty(pd.getLookupSchema()))
            return new LookupConceptType(pd);
        return parseRawValue(pd.getRangeURI());
    }
    

    static ConceptType parseRawValue(String str)
    {
        str = str.toLowerCase();

        PropertyType type = PropertyType.fromName(str);
        if (null != type)
            return new BaseConceptType(type);

        if ("subject".equals(str) || "participant".equals(str))
            return subjectType;
        if ("user".equals(str))
            return userType;
        if ("flag".equals(str))
            return flagType;

        int period = str.indexOf(".");
        if (0 < period && period < str.length()-1)
        {
            GWTPropertyDescriptor lookup = new GWTPropertyDescriptor();
            lookup.setLookupSchema(str.substring(0,period));
            lookup.setLookupQuery(str.substring(period+1));
            return new BaseConceptType(lookup, str);
        }
        
        return null;
    }



    public static boolean validateLookup(PropertyType fkType, String schema, String table, PropertyType pkType)
    {
        boolean ret = _validateLookup(fkType, pkType);
//        _log("" + ret + "=validateLookup(" + (null==fkType ? "null" : fkType.getShortName())
//                + "," + schema + "," + table + "," + pkType.getShortName() + ")");
        return ret;
    }


    private static boolean _validateLookup(PropertyType fkType, PropertyType pkType)
    {
        if (null == fkType)
            return true;
        fkType = fkType == PropertyType.expMultiLine ? PropertyType.xsdString : fkType;
        pkType = pkType == PropertyType.expMultiLine ? PropertyType.xsdString : pkType;
        return fkType == pkType;
    }


    public static PropertyEditor<ConceptType> conceptTypePropertyEditor = new PropertyEditor<ConceptType>()
    {
        @Override
        public ConceptType parse(CharSequence text)
        {
            return parseRawValue(text.toString());
        }

        @Override
        public String render(ConceptType value)
        {
            return null==value ? "" : value.getDisplay();
        }
    };


    /*
     * this is a bound version of the ConceptPicker, it automatically
     * applies the value of the picker to the provided PropertyDescriptor
     */
    public static class Bound extends ConceptPicker implements BoundWidget
    {
        GWTPropertyDescriptor _target;

        public Bound(LookupServiceAsync lookupService, String name, GWTPropertyDescriptor target)
        {
            super(lookupService, name, target);
            _target = target;
            addValueChangeHandler(event -> pushValue());
        }

        public void pushValue()
        {
            ConceptType t = getValue();
            if (null != t)
                t.apply(_target);
        }

        public void pullValue()
        {
            throw new UnsupportedOperationException();
        }
    }

    private static boolean _empty(String s) {return null==s || s.length()==0;}
    private static String _default(String a, String b) {return _empty(a) ? b : a;}
    private static void _log(String s)
    {
        PropertiesEditor._log(s);
    }
}
