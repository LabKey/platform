/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;

/**
 * <code>AbstractTaskFactorySettings</code> is used for Spring configuration of a
 * <code>TaskFactory</code> in the <code>TaskRegistry</code>.  Extend this
 * class, and override <code>TaskFactory.configure()</code> to create
 * specific types of <code>TaskFactory</code> objects that can be configured
 * with Spring beans.
 *
 * @author brendanx
 */
abstract public class AbstractTaskFactorySettings implements TaskFactorySettings
{
    private TaskId _id;
    private TaskId _dependencyId;
    private Boolean _join;
    private Boolean _largeWork;
    private String _location;
    private Integer _autoRetry;
    private String _groupParameterName;
    private Module _declaringModule;

    public AbstractTaskFactorySettings(TaskId id)
    {
        _id = id;
    }

    /**
     * Convenience constructor for Spring XML configuration.
     *
     * @param namespaceClass namespace class for TaskId
     */
    public AbstractTaskFactorySettings(Class namespaceClass)
    {
        this(namespaceClass, null);
    }

    /**
     * Convenience constructor for Spring XML configuration.
     *
     * @param namespaceClass namespace class for TaskId
     * @param name name for TaskId
     */
    public AbstractTaskFactorySettings(Class namespaceClass, String name)
    {
        this(new TaskId(namespaceClass, name));
    }

    public TaskId getId()
    {
        return _id;
    }

    public TaskId getDependencyId()
    {
        return _dependencyId;
    }

    public void setDependencyId(TaskId dependencyId)
    {
        _dependencyId = dependencyId;
    }

    public boolean isJoinSet()
    {
        return _join != null;
    }

    public boolean isJoin()
    {
        return isJoinSet() && _join.booleanValue();
    }

    public void setJoin(boolean join)
    {
        _join = join;
    }

    public boolean isLargeWorkSet()
    {
        return _largeWork != null;
    }

    /**
     * Indicates that the inputs are too large to want to copy to the local file system if they're
     * coming from a remote file system. This prevents us from filling up the local disk.
     */
    public boolean isLargeWork()
    {
        return isLargeWorkSet() && _largeWork.booleanValue();
    }

    public void setLargeWork(Boolean largeWork)
    {
        _largeWork = largeWork;
    }

    public void setGroupParameterName(String name)
    {
        _groupParameterName = name;
    }

    public String getGroupParameterName()
    {
        return _groupParameterName;
    }

    public String getLocation()
    {
        return _location;
    }

    public void setLocation(String location)
    {
        _location = location;
    }

    public boolean isAutoRetrySet()
    {
        return _autoRetry != null;
    }

    public int getAutoRetry()
    {
        return _autoRetry.intValue();
    }

    public void setAutoRetry(int autoRetry)
    {
        _autoRetry = autoRetry;
    }

    @Override
    public void setDeclaringModule(@NotNull Module declaringModule)
    {
        if (declaringModule == null)
            throw new IllegalArgumentException("Declaring module must not be null");

        if (_declaringModule != null && _declaringModule != declaringModule)
            throw new IllegalStateException("Declaring module already set to " + _declaringModule + ", it cannot be reset to " + declaringModule);

        _declaringModule = declaringModule;
    }

    @Override
    public Module getDeclaringModule()
    {
        return _declaringModule;
    }
}
