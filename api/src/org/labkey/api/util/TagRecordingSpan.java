/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.util;

import datadog.trace.api.interceptor.MutableSpan;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;

/** For tests of span-tagging code: a Datadog span that only records its tags */
public class TagRecordingSpan
{
    /** Like dd-trace, setting a tag to null removes it */
    public static MutableSpan create(Map<String, Object> tags)
    {
        return (MutableSpan) Proxy.newProxyInstance(MutableSpan.class.getClassLoader(), new Class<?>[]{MutableSpan.class}, (proxy, method, args) -> {
            if (method.isDefault())
                return InvocationHandler.invokeDefault(proxy, method, args);

            return switch (method.getName())
            {
                case "getTags" -> tags;
                case "getLocalRootSpan" -> proxy;
                case "setTag" -> {
                    if (null == args[1])
                        tags.remove((String) args[0]);
                    else
                        tags.put((String) args[0], args[1]);
                    yield proxy;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
    }
}
