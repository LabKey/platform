/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.apache.commons.validator.routines;

import org.apache.log4j.Logger;

/**
 * Adds non-standard TLDs to allowable values for Apache Commons Validator. See issue 25041.
 * Needed because {@see DomainValidator.updateTLDOverride} is public, but its ArrayType argument is package-protected.
 * Created by: jeckels
 * Date: 1/16/16
 */
public class CustomTLDEnabler
{
    private static final Logger LOG = Logger.getLogger(CustomTLDEnabler.class);

    static
    {
        try
        {
            // We've received an exception report that indicates (but does not definitively prove) this call may fail
            // on some servers. Shouldn't be fatal if it doesn't work.
            DomainValidator.updateTLDOverride(DomainValidator.ArrayType.GENERIC_PLUS, new String[]{"local"});
        }
        catch (Throwable e)
        {
            LOG.error("Failed to enable .local domains in URL validation", e);
        }
    }

    public static void initialize()
    {
        // No-op, just used to get static initializer to run
    }
}
