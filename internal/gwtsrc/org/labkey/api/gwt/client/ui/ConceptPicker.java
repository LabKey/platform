/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import com.extjs.gxt.ui.client.Style;
import com.extjs.gxt.ui.client.event.*;
import com.extjs.gxt.ui.client.widget.ContentPanel;
import com.extjs.gxt.ui.client.widget.Dialog;
import com.extjs.gxt.ui.client.widget.button.Button;
import com.extjs.gxt.ui.client.widget.form.*;
import com.extjs.gxt.ui.client.widget.layout.AccordionLayout;
import com.extjs.gxt.ui.client.widget.layout.FitLayout;
import com.extjs.gxt.ui.client.widget.layout.VBoxLayout;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.shared.HandlerManager;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 23, 2007
 * Time: 5:16:30 PM
 *
 *
 * Concept picker is a UI for editing a subset of PropertyURI fiinitelds.
 *
 *    RangeURI      (roughly the storage type of the field, file/attachment are oddities)
 *    ConceptURI    Marker type e.g. Subject or VisitDate (may imply storage type)
 *    Lookup        Lookup (container,schmea,table), must match storage type of PK
 */
public class ConceptPicker extends TriggerField<ConceptPicker.ConceptType> //implements HasChangeHandlers
{
    private HandlerManager _handlers = new HandlerManager(this);
    final private LookupServiceAsync _lookupService;

    private boolean isRangeEditable = true;
    private boolean allowFileLinkProperties = true;
    private boolean allowAttachmentProperties = true;

    protected ConceptPicker(LookupServiceAsync lookupService)
    {
        super();
        setPropertyEditor(conceptTypePropertyEditor);
        _lookupService = lookupService;
        setEditable(true);
    }


    public ConceptPicker(LookupServiceAsync lookupService, GWTPropertyDescriptor initial)
    {
        this(lookupService);
        ConceptType type = fromPropertyDescriptor(initial);
        setValue(type);
    }

    
    public void setIsRangeEditable(boolean b)
    {
        this.isRangeEditable = b;
    }


    public void setAllowFileLinkProperties(boolean allowFileLinkProperties)
    {
        this.allowFileLinkProperties = allowFileLinkProperties;
    }


    public void setAllowAttachmentProperties(boolean allowAttachmentProperties)
    {
        this.allowAttachmentProperties = allowAttachmentProperties;
    }


    //
    // NOTE: raw value does not encode all the possible concept information
    // so we have to override getValue() which assumes that it does
    //

    @Override
    public ConceptType getValue()
    {
        ConceptType value = this.value;
        String rawValue = this.getRawValue();
        if (null != value && value.getDisplay().equals(rawValue))
            return value;
        return super.getValue();
    }

    @Override
    protected void beforeBlur()
    {
        ConceptType value = this.value;
        String rawValue = this.getRawValue();
        // if textbox is edited directly (and valid) we want to update the display
        if (null != value && rawValue.equals(value.getDisplay()))
            return;
        ConceptType type = parseRawValue(rawValue);
        if (null != type)
            setValue(type);
    }

    @Override
    protected boolean validateValue(String rawValue)
    {
        if (null != value && rawValue.equals(value.getDisplay()))
            return true;
        ConceptType type = parseRawValue(rawValue);
        return null != type;
    }


    //
    // Custom window on trigger
    //


    @Override
    protected void onTriggerClick(ComponentEvent ce)
    {
        showEditor(ConceptPicker.this);
    }
    

    private static void _copy(GWTPropertyDescriptor from, GWTPropertyDescriptor to)
    {
        to.setRangeURI(from.getRangeURI());
        to.setConceptURI(from.getConceptURI());
        to.setLookupContainer(from.getLookupContainer());
        to.setLookupSchema(from.getLookupSchema());
        to.setLookupQuery(from.getLookupQuery());
    }
    

    private static class ConceptTypeRadio extends Radio
    {
        ConceptType type;

        ConceptTypeRadio(String text, String rangeURI)
        {
            this(new BaseConceptType(rangeURI, text));
        }
        ConceptTypeRadio(ConceptType type)
        {
            this.type = type;
            setBoxLabel(type.getDisplay());
        }
    }



    private static class EditorWindow extends Dialog
    {
        ArrayList<Field> allradios = new ArrayList<Field>();
        ConceptType _type = null;
        ConceptPicker _current;
        LookupEditorPanel _lookupPanel;


        EditorWindow(LookupServiceAsync lookupService)
        {
            super();
            setSize(500, 300);
            setModal(true);
            setHeading("Choose field type:");

            setLayout(new AccordionLayout());
            okText = "Apply";
            setButtons(Dialog.OKCANCEL);
            
            // SIMPLE
            ContentPanel simplePanel = new ContentPanel(new FitLayout());
            simplePanel.setHeading("Simple data type");
            final RadioGroup group = new RadioGroup("rangeURI");
            group.setOrientation(Style.Orientation.VERTICAL);
            group.add(new ConceptTypeRadio(stringType));
            group.add(new ConceptTypeRadio(multilineType));
            group.add(new ConceptTypeRadio(booleanType));
            group.add(new ConceptTypeRadio(integerType));
            group.add(new ConceptTypeRadio(doubleType));
            group.add(new ConceptTypeRadio(datetimeType));
            simplePanel.add(group);
            add(simplePanel);

            // LOOKUP
            ContentPanel lookupPanel = new ContentPanel(new VBoxLayout());
            lookupPanel.setHeading("Lookup");
            final Radio lookupRadio = new ConceptTypeRadio(genericLookup);
            lookupPanel.add(lookupRadio);
            _lookupPanel = new LookupEditorPanel(lookupService, true);
            lookupPanel.add(_lookupPanel);
            add(lookupPanel);

            // CUSTOM
            ContentPanel customPanel = new ContentPanel(new FitLayout());
            customPanel.setHeading("Custom Types");
            final RadioGroup custom = new RadioGroup("customGroup");
            custom.setOrientation(Style.Orientation.VERTICAL);
            //if (allowFileLinkProperties)
            custom.add(new ConceptTypeRadio(fileType));
            custom.add(new ConceptTypeRadio(attachmentType));
            custom.add(new ConceptTypeRadio(userType));
            custom.add(new ConceptTypeRadio(subjectType));
            customPanel.add(custom);
            add(customPanel);

            // init all radios list
            allradios.addAll(custom.getAll());
            allradios.add(lookupRadio);
            allradios.addAll(group.getAll());

            // events : disable radios in other groups
            group.addListener(Events.Change, new Listener<FieldEvent>(){
                public void handleEvent(FieldEvent fe)
                {
                    if (Boolean.TRUE != fe.getValue())
                        return;
                    if (custom.getValue() != null) custom.getValue().setValue(false);
                    lookupRadio.setValue(false);
                    _type = ((ConceptTypeRadio)((RadioGroup)fe.getField()).getValue()).type;
                }
            });
            custom.addListener(Events.Change, new Listener<FieldEvent>(){
                public void handleEvent(FieldEvent fe)
                {
                    if (Boolean.TRUE != fe.getValue())
                        return;
                    if (group.getValue() != null) group.getValue().setValue(false);
                    lookupRadio.setValue(false);
                    _type = ((ConceptTypeRadio)((RadioGroup)fe.getField()).getValue()).type;
                }
            });
            lookupRadio.addListener(Events.Change, new Listener<FieldEvent>(){
                public void handleEvent(FieldEvent fe)
                {
                    _lookupPanel.setEnabled(Boolean.TRUE==fe.getValue());
                    if (Boolean.TRUE != fe.getValue())
                        return;
                    if (group.getValue() != null) group.getValue().setValue(false);
                    if (custom.getValue() != null) custom.getValue().setValue(false);
                    _type = ((ConceptTypeRadio)fe.getField()).type;
                }
            });
        }


        @Override
        protected void onButtonPressed(Button button)
        {
            if (button.getText().equalsIgnoreCase(Dialog.CANCEL))
            {
                hide();
                return;
            }

            apply();
            hide();
        }

        
        void init(ConceptPicker picker)
        {
            _current = picker;
            // TODO
            // set inital selection and combos
        }

        
        // push from the EditorPanel to the TriggerField
        private void apply()
        {
            if (_type != genericLookup)
            {
                _current.setValue(_type);
                return;
            }
            // TODO
            _current.setValue(_type);
        }
        

        ConceptType lookupType = new ConceptType()
        {
            @Override
            String getDisplay()
            {
                return _lookupPanel.getSchemaName() + "." + _lookupPanel.getTableName();
            }

            @Override
            String rangeURI()
            {
                // TODO _lookupPanel.getRangeURI();
                return PropertyType.xsdInt.toString();
            }

            @Override
            boolean matches(GWTPropertyDescriptor pd)
            {
                throw new UnsupportedOperationException("generic lookup");
            }

            @Override
            void apply(GWTPropertyDescriptor pd)
            {
                pd.setRangeURI(rangeURI());
                pd.setConceptURI(null);
                pd.setLookupContainer(_lookupPanel.getContainer());
                pd.setLookupSchema(_lookupPanel.getSchemaName());
                pd.setLookupQuery(_lookupPanel.getTableName());
            }
        };
    }


    EditorWindow _editorInstance = null;
    
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


    void fireChange()
    {
        ChangeEvent event = new ChangeEvent(){};
        _handlers.fireEvent(event);
    }


    ArrayList<ConceptType> customTypes = new ArrayList<ConceptType>();

    void addCustomType(ConceptType c)
    {
        customTypes.add(c);
    }

    

    /**
     * ConceptType is a wrapper for a PropetyDescriptor with a matches and apply method
     */

    public abstract static class ConceptType
    {
        abstract String getDisplay();
        abstract String rangeURI();
        abstract boolean matches(GWTPropertyDescriptor pd);
        abstract void apply(GWTPropertyDescriptor pd);
    }

    public static class BaseConceptType extends ConceptType
    {
        GWTPropertyDescriptor _pd = new GWTPropertyDescriptor();
        String _text;
        
        BaseConceptType(PropertyType type)
        {
            this._pd.setRangeURI(type.toString());
            this._text = type.getDisplay();
        }
        BaseConceptType(String rangeURI)
        {
            this._pd.setRangeURI(rangeURI);
            PropertyType t = PropertyType.fromName(rangeURI);
            this._text = _default(null==t?null:t.getDisplay(), rangeURI);
        }

        BaseConceptType(String rangeURI, String text)
        {
            this._pd.setRangeURI(rangeURI);
            this._text = text;
        }

        BaseConceptType(GWTPropertyDescriptor pd, String text)
        {
            _copy(pd, _pd);
            this._text = text;
        }

        @Override
        String rangeURI()
        {
            return _pd.getRangeURI();
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
            if (!_empty(pd.getConceptURI()) && !_pd.getConceptURI().equalsIgnoreCase(pd.getConceptURI()))
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
        LookupConceptType(String schema, String table)
        {
            super((String)null);
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
    public static final ConceptType subjectType = new BaseConceptType(PropertyType.xsdString.toString(), "Subject")
    {
        @Override
        boolean matches(GWTPropertyDescriptor pd)
        {
            return super.matches(pd) && "http://cpas.labkey.com/Study#ParticipantId".equals(pd.getConceptURI());
        }

        @Override
        void apply(GWTPropertyDescriptor pd)
        {
            super.apply(pd);
            pd.setConceptURI("http://cpas.labkey.com/Study#ParticipantId");
        }
    };
    public static final ConceptType userType = new BaseConceptType(PropertyType.xsdInt.toString(), "User")
    {
        @Override
        boolean matches(GWTPropertyDescriptor pd)
        {
            return super.matches(pd) &&
                    "core".equals(pd.getLookupSchema()) &&
                    "users".equals(pd.getLookupQuery());
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
    private static final ConceptType genericLookup = new BaseConceptType("", "Lookup")
    {
        @Override
        boolean matches(GWTPropertyDescriptor pd)
        {
            return !_empty(pd.getLookupSchema()) && !_empty(pd.getLookupQuery());
        }

        @Override
        void apply(GWTPropertyDescriptor pd)
        {
            throw new UnsupportedOperationException("generic lookup");
        }
    };



    static ConceptType fromPropertyDescriptor(GWTPropertyDescriptor pd)
    {
        if (userType.matches(pd))
            return userType;
        if (subjectType.matches(pd))
            return subjectType;
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

        int period = str.indexOf(".");
        if (0 < period && period < str.length()-1)
        {
            String table = str.substring(period+1);
            GWTPropertyDescriptor lookup = new GWTPropertyDescriptor();
            lookup.setLookupSchema(str.substring(0,period));
            lookup.setLookupQuery(str.substring(period+1));
            return new BaseConceptType(lookup, str);
        }
        
        return null;
    }


    public static PropertyEditor<ConceptType> conceptTypePropertyEditor = new PropertyEditor<ConceptType>()
    {
        public String getStringValue(ConceptType value)
        {
            return null==value ? "" : value.getDisplay();
        }

        public ConceptType convertStringValue(String value)
        {
            return parseRawValue(value);
        }
    };


    /*
     * this is a bound version of the ConceptPicker, it automatically
     * applies the value of the picker to the provided PropertyDescriptor
     */
    public static class Bound extends ConceptPicker
    {
        GWTPropertyDescriptor _target;

        public Bound(LookupServiceAsync lookupService, GWTPropertyDescriptor target)
        {
            super(lookupService, target);
            _target = target;
            this.addListener(Events.Change, new Listener(){
                public void handleEvent(BaseEvent be)
                {
                    onChange();
                }
            });
        }

        private void onChange()
        {
            ConceptType t = getValue();
            if (null != t)
                t.apply(_target);
        }
    }

    private static boolean _empty(String s) {return null==s || s.length()==0;}
    private static String _string(Object o) {return null==o ? "" : o.toString();}
    private static String _default(String a, String b) {return _empty(a) ? b : a;}
}