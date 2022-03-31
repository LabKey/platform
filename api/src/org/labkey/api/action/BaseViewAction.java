/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.action;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.security.User;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.PageConfig;
import org.springframework.beans.AbstractPropertyAccessor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.NotReadablePropertyException;
import org.springframework.beans.NotWritablePropertyException;
import org.springframework.beans.PropertyAccessException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingErrorProcessor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.Validator;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.ServletRequestParameterPropertyValues;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: matthewb
 * Date: May 16, 2007
 * Time: 1:48:01 PM
 */
public abstract class BaseViewAction<FORM> extends PermissionCheckableAction implements Validator, HasPageConfig
{
    protected static final Logger logger = LogManager.getLogger(BaseViewAction.class);

    private PageConfig _pageConfig = null;
    private PropertyValues _pvs;
    private boolean _robot = false;  // Is this request from GoogleBot or some other crawler?
    private boolean _debug = false;

    protected boolean _print = false;
    protected Class _commandClass;
    protected String _commandName = "form";

    protected BaseViewAction()
    {
        String methodName = getCommandClassMethodName();

        if (null == methodName)
            return;

        // inspect the action's *public* methods to determine form class
        Class typeBest = null;
        for (Method m : this.getClass().getMethods())
        {
            if (methodName.equals(m.getName()))
            {
                Class[] types = m.getParameterTypes();
                if (types.length < 1)
                    continue;
                Class typeCurrent = types[0];
                assert null == _commandClass || typeCurrent.equals(_commandClass);

                // Using templated classes to extend a base action can lead to multiple
                // versions of a method with acceptable types, so take the most extended
                // type we can find.
                if (typeBest == null || typeBest.isAssignableFrom(typeCurrent))
                    typeBest = typeCurrent;
            }
        }
        if (typeBest != null)
            setCommandClass(typeBest);
    }


    protected abstract String getCommandClassMethodName();


    protected BaseViewAction(@NotNull Class<? extends FORM> commandClass)
    {
        setCommandClass(commandClass);
    }


    public void setProperties(PropertyValues pvs)
    {
        _pvs = pvs;
    }


    public void setProperties(Map m)
    {
        _pvs = new MutablePropertyValues(m);
    }

    /* Doesn't guarantee non-null, non-empty */
    public Object getProperty(String key, String d)
    {
        PropertyValue pv = _pvs.getPropertyValue(key);
        return pv == null ? d : pv.getValue();
    }

    public Object getProperty(Enum key)
    {
        PropertyValue pv = _pvs.getPropertyValue(key.name());
        return pv == null ? null : pv.getValue();
    }

    public Object getProperty(String key)
    {
        PropertyValue pv = _pvs.getPropertyValue(key);
        return pv == null ? null : pv.getValue();
    }


    public PropertyValues getPropertyValues()
    {
        return _pvs;
    }


    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (null == getPropertyValues())
            setProperties(new ServletRequestParameterPropertyValues(request));
        getViewContext().setBindPropertyValues(getPropertyValues());
        handleSpecialProperties();

        return handleRequest();
    }


    private void handleSpecialProperties()
    {
        _robot = PageFlowUtil.isRobotUserAgent(getViewContext().getRequest().getHeader("User-Agent"));

        // Special flag puts actions in "debug" mode, during which they should log extra information that would be
        // helpful for testing or debugging problems
        if (!_robot && null != StringUtils.trimToNull((String) getProperty("_debug")))
        {
            _debug = true;
        }

        if (null != StringUtils.trimToNull((String) getProperty("_print")) ||
            null != StringUtils.trimToNull((String) getProperty("_print.x")))
        {
            _print = true;
        }
    }


    public abstract ModelAndView handleRequest() throws Exception;


    @Override
    public void setPageConfig(PageConfig page)
    {
        _pageConfig = page;
    }


    public Container getContainer()
    {
        return getViewContext().getContainer();
    }


    public User getUser()
    {
        return getViewContext().getUser();
    }


    @Override
    public PageConfig getPageConfig()
    {
        return _pageConfig;
    }


    public void setTitle(String title)
    {
        assert null != getPageConfig() : "action not initialized property";
        getPageConfig().setTitle(title);
    }


    public void setHelpTopic(String topicName)
    {
        setHelpTopic(new HelpTopic(topicName));
    }


    public void setHelpTopic(HelpTopic topic)
    {
        assert null != getPageConfig() : "action not initialized property";
        getPageConfig().setHelpTopic(topic);
    }


    protected Object newInstance(Class c)
    {
        try
        {
            return c == null ? null : c.newInstance();
        }
        catch (Exception x)
        {
            if (x instanceof RuntimeException)
                throw ((RuntimeException)x);
            else
                throw new RuntimeException(x);
        }
    }


    protected @NotNull FORM getCommand(HttpServletRequest request) throws Exception
    {
        FORM command = (FORM) createCommand();

        if (command instanceof HasViewContext)
            ((HasViewContext)command).setViewContext(getViewContext());

        return command;
    }


    protected @NotNull FORM getCommand() throws Exception
    {
        return getCommand(getViewContext().getRequest());
    }


    //
    // PARAMETER BINDING
    //
    // don't assume parameters always come from a request, use PropertyValues interface
    //

    public @NotNull BindException defaultBindParameters(FORM form, PropertyValues params)
    {
        return defaultBindParameters(form, getCommandName(), params);
    }


    public static @NotNull BindException defaultBindParameters(Object form, String commandName, PropertyValues params)
    {
        /* check for do-it-myself forms */
        if (form instanceof HasBindParameters)
        {
            return ((HasBindParameters)form).bindParameters(params);
        }
        
        /* 'regular' commandName handling */
        if (null != params && null != params.getPropertyValue(DataRegion.OLD_VALUES_NAME))
        {
            try
            {
                Map<String, ?> oldObject = PageFlowUtil.decodeObject(Map.class, (String)params.getPropertyValue(DataRegion.OLD_VALUES_NAME).getValue());
                ((ObjectFactory<Object>)ObjectFactory.Registry.getFactory(form.getClass())).fromMap(form, oldObject);
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
        }

        if (form instanceof DynaBean)
        {
            return simpleBindParameters(form, commandName, params);
        }
        else
        {
            return springBindParameters(form, commandName, params);
        }
    }


    public static @NotNull BindException springBindParameters(Object command, String commandName, PropertyValues params)
    {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(command, commandName);

        String[] fields = binder.getDisallowedFields();
        List<String> fieldList = new ArrayList<>(fields != null ? Arrays.asList(fields) : Collections.emptyList());
        fieldList.addAll(Arrays.asList("class.*", "Class.*", "*.class.*", "*.Class.*"));
        binder.setDisallowedFields(fieldList.toArray(new String[] {}));

        ConvertHelper.getPropertyEditorRegistrar().registerCustomEditors(binder);
        BindingErrorProcessor defaultBEP = binder.getBindingErrorProcessor();
        binder.setBindingErrorProcessor(getBindingErrorProcessor(defaultBEP));
        binder.setFieldMarkerPrefix(SpringActionController.FIELD_MARKER);
        try
        {
            binder.bind(params);
            BindException errors = new NullSafeBindException(binder.getBindingResult());
            return errors;
        }
        catch (InvalidPropertyException x)
        {
            // Maybe we should propagate exception and return SC_BAD_REQUEST (in ExceptionUtil.handleException())
            // most POST handlers check errors.hasErrors(), but not all GET handlers do
            BindException errors = new BindException(command, commandName);
            errors.reject(SpringActionController.ERROR_MSG, "Error binding property: " + x.getPropertyName());
            return errors;
        }
        catch (NumberFormatException x)
        {
            // Malformed array parameter throws this exception, unfortunately. Just reject the request. #21931
            BindException errors = new BindException(command, commandName);
            errors.reject(SpringActionController.ERROR_MSG, "Error binding array property; invalid array index (" + x.getMessage() + ")");
            return errors;
        }
        catch (NegativeArraySizeException x)
        {
            // Another malformed array parameter throws this exception. #23929
            BindException errors = new BindException(command, commandName);
            errors.reject(SpringActionController.ERROR_MSG, "Error binding array property; negative array size (" + x.getMessage() + ")");
            return errors;
        }
        catch (IllegalArgumentException x)
        {
            // General bean binding problem. #23929
            BindException errors = new BindException(command, commandName);
            errors.reject(SpringActionController.ERROR_MSG, "Error binding property; (" + x.getMessage() + ")");
            return errors;
        }
    }


    static BindingErrorProcessor getBindingErrorProcessor(final BindingErrorProcessor defaultBEP)
    {
        return new BindingErrorProcessor()
        {
            @Override
            public void processMissingFieldError(String missingField, BindingResult bindingResult)
            {
                defaultBEP.processMissingFieldError(missingField, bindingResult);
            }

            @Override
            public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult)
            {
                Object newValue = ex.getPropertyChangeEvent().getNewValue();
                if (newValue instanceof String)
                    newValue = StringUtils.trimToNull((String)newValue);

                // convert NULL conversion errors to required errors
                if (null == newValue)
                    defaultBEP.processMissingFieldError(ex.getPropertyChangeEvent().getPropertyName(), bindingResult);
                else
                    defaultBEP.processPropertyAccessException(ex, bindingResult);
            }
        };
    }


    /*
     * This binder doesn't have much to offer over the standard spring data binding except that it will
     * handle DynaBeans.
     */
    public static @NotNull BindException simpleBindParameters(Object command, String commandName, PropertyValues params)
    {
        //params = _fixupPropertyMap(params);

        BindException errors = new NullSafeBindException(command, "Form");

        // unfortunately ObjectFactory and BeanObjectFactory are not good about reporting errors
        // do this by hand
        for (PropertyValue pv : params.getPropertyValues())
        {
            String propertyName = pv.getName();
            Object value = pv.getValue();
            try
            {
                Object converted = value;
                Class propClass = PropertyUtils.getPropertyType(command, propertyName);
                if (null == propClass)
                    continue;
                if (value == null)
                {
                    /*  */
                }
                else if (propClass.isPrimitive())
                {
                    converted = ConvertUtils.convert(String.valueOf(value), propClass);
                }
                else if (propClass.isArray())
                {
                    if (value instanceof Collection)
                        value = ((Collection) value).toArray(new String[((Collection) value).size()]);
                    else if (!value.getClass().isArray())
                        value = new String[] {String.valueOf(value)};
                    converted = ConvertUtils.convert((String[])value, propClass);
                }
                PropertyUtils.setProperty(command, propertyName, converted);
            }
            catch (ConversionException x)
            {
                errors.addError(new FieldError(commandName, propertyName, value, true, new String[] {"ConversionError", "typeMismatch"}, null, "Could not convert to value: " + String.valueOf(value)));
            }
            catch (Exception x)
            {
                errors.addError(new ObjectError(commandName, new String[]{"Error"}, new Object[] {value}, x.getMessage()));
                LogManager.getLogger(BaseViewAction.class).error("unexpected error", x);
            }
        }
        return errors;
    }

    @Override
    public boolean supports(Class clazz)
    {
        return getCommandClass().isAssignableFrom(clazz);
    }


    /* for TableViewForm, uses BeanUtils to work with DynaBeans */
    static public class BeanUtilsPropertyBindingResult extends BeanPropertyBindingResult
    {
        public BeanUtilsPropertyBindingResult(Object target, String objectName)
        {
            super(target, objectName);
        }

        @Override
        protected BeanWrapper createBeanWrapper()
        {
            return new BeanUtilsWrapperImpl((DynaBean)getTarget());
        }
    }

    static public class BeanUtilsWrapperImpl extends AbstractPropertyAccessor implements BeanWrapper
    {
        private Object object;
        private boolean autoGrowNestedPaths = false;
        private int autoGrowCollectionLimit = 0;

        public BeanUtilsWrapperImpl()
        {
            // registerDefaultEditors();
        }
        
        public BeanUtilsWrapperImpl(DynaBean target)
        {
            this();
            object = target;
        }

        @Override
        public Object getPropertyValue(String propertyName) throws BeansException
        {
            try
            {
                return PropertyUtils.getProperty(object, propertyName);
            }
            catch (Exception e)
            {
                throw new NotReadablePropertyException(object.getClass(), propertyName);
            }
        }

        @Override
        public void setPropertyValue(String propertyName, Object value) throws BeansException
        {
            try
            {
                PropertyUtils.setProperty(object, propertyName, value);
            }
            catch (Exception e)
            {
                throw new NotWritablePropertyException(object.getClass(), propertyName);
            }
        }

        @Override
        public boolean isReadableProperty(String propertyName)
        {
            return true;
        }

        @Override
        public boolean isWritableProperty(String propertyName)
        {
            return true;
        }

        @Override
        public TypeDescriptor getPropertyTypeDescriptor(String s) throws BeansException
        {
            return null;
        }

        public void setWrappedInstance(Object obj)
        {
            object = obj;
        }

        @Override
        public Object getWrappedInstance()
        {
            return object;
        }

        @Override
        public Class getWrappedClass()
        {
            return object.getClass();
        }

        @Override
        public PropertyDescriptor[] getPropertyDescriptors()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public PropertyDescriptor getPropertyDescriptor(String propertyName) throws BeansException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAutoGrowNestedPaths(boolean b)
        {
            this.autoGrowNestedPaths = b;
        }

        @Override
        public boolean isAutoGrowNestedPaths()
        {
            return this.autoGrowNestedPaths;
        }

        @Override
        public void setAutoGrowCollectionLimit(int i)
        {
            this.autoGrowCollectionLimit = i;
        }

        @Override
        public int getAutoGrowCollectionLimit()
        {
            return this.autoGrowCollectionLimit;
        }

        @Override
        public <T> T convertIfNecessary(Object value, Class<T> requiredType) throws TypeMismatchException
        {
            if (value == null)
                return null;
            return (T)ConvertUtils.convert(String.valueOf(value), requiredType);
        }

        @Override
        public <T> T convertIfNecessary(Object value, Class<T> requiredType, MethodParameter methodParam) throws TypeMismatchException
        {
            return convertIfNecessary(value, requiredType);
        }
    }

    /**
     * @return a map from form element name to uploaded files
     */
    protected Map<String, MultipartFile> getFileMap()
    {
        if (getViewContext().getRequest() instanceof MultipartHttpServletRequest)
            return ((MultipartHttpServletRequest)getViewContext().getRequest()).getFileMap();
        return Collections.emptyMap();
    }

    protected List<AttachmentFile> getAttachmentFileList()
    {
        return SpringAttachmentFile.createList(getFileMap());
    }

    public boolean isRobot()
    {
        return _robot;
    }

    public boolean isPrint()
    {
        return _print;
    }

    public boolean isDebug()
    {
        return _debug;
    }

    public @NotNull Class getCommandClass()
    {
        if (null == _commandClass)
            throw new IllegalStateException("NULL _commandClass in " + getClass().getName());
        return _commandClass;
    }

    public void setCommandClass(@NotNull Class commandClass)
    {
        _commandClass = commandClass;
    }

    protected final @NotNull Object createCommand()
    {
        return BeanUtils.instantiateClass(getCommandClass());
    }

    public void setCommandName(String commandName)
    {
        _commandName = commandName;
    }

    public String getCommandName()
    {
        return _commandName;
    }

    /**
     * Cacheable resources can calculate a last modified timestamp to send to the browser.
     */
    protected long getLastModified(FORM form)
    {
        return Long.MIN_VALUE;
    }

    /**
     * Cacheable resources can calculate an ETag header to send to the browser.
     */
    protected String getETag(FORM form)
    {
        return null;
    }
}
