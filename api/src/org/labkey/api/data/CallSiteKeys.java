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
package org.labkey.api.data;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Keys a captured stack by its call site, for throttling warnings once per call site */
final class CallSiteKeys
{
    // Caps key length; a stack this deep in plumbing collapses to a single key
    static final int MAX_KEY_FRAMES = 10;

    // Names, not classes: stack frames carry only the class name
    private final Set<String> _plumbingClassNames;

    /** @param plumbingClasses the classes whose frames can sit between the real call site and the point the stack is captured */
    CallSiteKeys(Class<?>... plumbingClasses)
    {
        _plumbingClassNames = Arrays.stream(plumbingClasses).map(Class::getName).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The stack through the first frame outside the plumbing classes: the code that actually made the call.
     * Don't extend this to the full stack: a shared helper is reached via many callers, each of which would then get its own key.
     */
    String getStackKey(Throwable t)
    {
        return Arrays.stream(t.getStackTrace()).limit(getCallSiteIndex(t) + 1L).map(StackTraceElement::toString).collect(Collectors.joining("\n"));
    }

    /** Index of the first frame outside the plumbing classes, capped at {@link #MAX_KEY_FRAMES} */
    int getCallSiteIndex(Throwable t)
    {
        StackTraceElement[] frames = t.getStackTrace();
        int index = 0;

        while (index < frames.length - 1 && index < MAX_KEY_FRAMES - 1 && isPlumbingStackFrame(frames[index]))
            index++;

        return index;
    }

    private boolean isPlumbingStackFrame(StackTraceElement frame)
    {
        String className = frame.getClassName();
        int nested = className.indexOf('$'); // Nested classes and lambdas belong to their enclosing class

        return _plumbingClassNames.contains(nested < 0 ? className : className.substring(0, nested));
    }
}
