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
package org.labkey.pipeline.mule.transformers;

import org.mule.umo.UMOEventContext;
import org.mule.umo.transformer.TransformerException;

public class XmlToObject extends org.mule.transformers.xml.XmlToObject
{
    private boolean _securityInitialized = false;

    @Override
    public Object transform(Object src, String encoding, UMOEventContext context) throws TransformerException
    {
        // Restrict XStream deserialization to the types reachable from a StatusRequest rather than granting
        // AnyTypePermission. The JMS endpoint is considered secure, but deny-by-default is defense-in-depth
        // against gadget-chain attacks. https://x-stream.github.io/security.html#framework
        if (!_securityInitialized)
        {
            PipelineXStreamSecurity.configure(getXStream());
            _securityInitialized = true;
        }
        return super.transform(src, encoding, context);
    }
}
