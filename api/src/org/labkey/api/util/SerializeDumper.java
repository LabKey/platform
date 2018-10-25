package org.labkey.api.util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class SerializeDumper
{
    private static final Logger _log = Logger.getLogger(SerializeDumper.class);

    private static final Map<String, Class<?>> _seenClasses = new HashMap<>();
    private static final Queue<Class<?>> _queueClasses = new ArrayDeque<>();

    public static void dumpPipelineJobClasses()
    {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AssignableTypeFilter(PipelineJob.class));
        for (BeanDefinition beanDef : provider.findCandidateComponents("org/labkey"))
        {
            try
            {
                Class<?> cls = Class.forName(beanDef.getBeanClassName());
                SerializeDumper.dumpFieldHierarchy(cls);
            }
            catch (ClassNotFoundException e)
            {
                _log.debug("Class not found: " + beanDef.getBeanClassName());
            }
        }
    }

    public static void dumpFieldHierarchy(Class<?> cls)
    {
        _seenClasses.clear();
        _log.debug(";" + cls.getName());
        Class<?> hier = cls;
        while (isLabKey(hier))
        {
            _seenClasses.put(hier.getName(), hier);
            _queueClasses.add(hier);
            hier = hier.getSuperclass();
        }
        while (!_queueClasses.isEmpty())
        {
            Class<?> cl = _queueClasses.poll();
            if (null != cl)
                dumpFields(cl);
        }

        _log.debug(";" + cls.getName());
        _seenClasses.clear();
    }

    private static void dumpFields(Class<?> cls)
    {
        _log.debug(";;" + cls.getCanonicalName() +
                (null != cls.getSuperclass() && isLabKey(cls.getSuperclass()) ?
                        ";" + cls.getSuperclass().getCanonicalName() : ""));

        for (Field field : cls.getDeclaredFields())
        {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()))         // ignore statics and transients
                continue;

            Class<?> type = field.getType();
            _log.debug(";;;;" + field.getName() + ";" + type.getCanonicalName() + ";;" + Modifier.toString(field.getModifiers()));
            if (field.getGenericType() instanceof ParameterizedType)
            {
                ParameterizedType paramType = (ParameterizedType)field.getGenericType();
                for (Type typeArg : paramType.getActualTypeArguments())
                {
                    _log.debug(";;;;;;" + typeArg.getTypeName());
                    if (typeArg instanceof Class && !_seenClasses.containsKey(((Class)typeArg).getCanonicalName()))
                        queueClasses((Class)typeArg);
                }
            }
            else
            {
                if (type.isArray())
                    type = type.getComponentType();
                if (!type.isPrimitive() &&
                        !type.isEnum() &&
                        !_seenClasses.containsKey(type.getCanonicalName()) &&
                        null != type.getPackage() &&
                        type.getPackage().getName().startsWith("org.labkey"))
                {
                    queueClasses(type);
                }
            }
        }
    }

    private static void queueClasses(Class<?> hier)
    {
        while (isLabKey(hier))
        {
            _seenClasses.put(hier.getCanonicalName(), hier);
            _queueClasses.add(hier);
            hier = hier.getSuperclass();
        }
    }

    private static boolean isLabKey(Class<?> hier)
    {
        return (null != hier && null != hier.getPackage() && hier.getPackage().getName().startsWith("org.labkey"));
    }
}
