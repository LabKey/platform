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
package org.labkey.api.query;

import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

/**
 * Represents a particular validation error. Various validation error
 * classes will implement this interface for use in the
 * {@link ValidationException} class
 * User: Dave
 * Date: Jun 9, 2008
 */
public interface ValidationError
{
    String getMessage();

     ValidationException.SEVERITY getSeverity();

    void addToBindException(BindException be, String errorCode, boolean includeWarnings);
}