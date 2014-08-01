/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.api.pipeline.file;

import org.labkey.api.query.ValidationException;

import java.util.Map;

/**
 * Translates paths back and forth between the web server's representation and a remote computer's representation.
 * Strings are URIs and should begin with "file:/"
 * <code>PathMapper</code>
 */
public interface PathMapper
{
    Map<String, String> getPathMap();

    String remoteToLocal(String remoteURI);

    String localToRemote(String localURI);

    ValidationException getValidationErrors();
}
