package org.labkey.api.ehr.dataentry;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * User: bimber
 * Date: 12/3/13
 * Time: 12:47 PM
 */
public class DefaultDataEntryFormFactory implements DataEntryFormFactory
{
    private Logger _log = Logger.getLogger(DefaultDataEntryFormFactory.class);
    Class<? extends DataEntryForm> _clazz;
    Module _module;

    public DefaultDataEntryFormFactory(Class<? extends DataEntryForm> clazz, Module module)
    {
        _clazz = clazz;
        _module = module;
    }

    public DataEntryForm createForm(DataEntryFormContext ctx)
    {
        try
        {
            //TODO: how do I force forms to have this constructor??
            Constructor<?> cons = _clazz.getConstructor(DataEntryFormContext.class, Module.class);
            return (DataEntryForm)cons.newInstance(ctx, _module);
        }
        catch (InstantiationException e)
        {
            _log.error("Unable to create form: " + _clazz.getName(), e);
            return null;
        }
        catch (IllegalAccessException e)
        {
            _log.error("Unable to create form: " + _clazz.getName(), e);
            return null;
        }
        catch (InvocationTargetException e)
        {
            _log.error("Unable to create form: " + _clazz.getName(), e);
            return null;
        }
        catch (NoSuchMethodException e)
        {
            _log.error("Unable to create form: " + _clazz.getName(), e);
            return null;
        }
//        catch (NoSuchMethodException e)
//        {
//            _log.error("Unable to create form: " + _clazz.getName(), e);
//            return null;
//        }
    }

    public static class TaskFactory implements DataEntryFormFactory
    {
        private Module _owner;
        private String _category;
        private String _name;
        private String _label;
        private List<FormSection> _sections;

        public TaskFactory(Module owner, String category, String name, String label, List<FormSection> sections)
        {
            _owner = owner;
            _category = category;
            _name = name;
            _label = label;
            _sections = sections;
        }

        public DataEntryForm createForm(DataEntryFormContext ctx)
        {
            return TaskForm.create(ctx, _owner, _category, _name, _label, _sections);
        }
    }

    public static class RequestFactory implements DataEntryFormFactory
    {
        private Module _owner;
        private String _category;
        private String _name;
        private String _label;
        private List<FormSection> _sections;

        public RequestFactory(Module owner, String category, String name, String label, List<FormSection> sections)
        {
            _owner = owner;
            _category = category;
            _name = name;
            _label = label;
            _sections = sections;
        }

        public DataEntryForm createForm(DataEntryFormContext ctx)
        {
            return RequestForm.create(ctx, _owner, _category, _name, _label, _sections);
        }
    }
}
