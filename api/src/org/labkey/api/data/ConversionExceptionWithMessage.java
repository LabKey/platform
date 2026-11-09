/*
 * Copyright (c) 2023-2026 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.labkey.api.util.SkipMothershipLogging;

/**
 * Use this class when you want your custom exception message to be displayed instead of the standard one we
 * construct using the value and target type for the conversion.
 */
public class ConversionExceptionWithMessage extends ConversionException implements SkipMothershipLogging
{
    public ConversionExceptionWithMessage(String message)
    {
        super(message);
    }
}
