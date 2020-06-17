package org.labkey.api.security;

import org.labkey.api.util.HttpUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface MethodsAllowed
{
    HttpUtil.Method[] value();
}
