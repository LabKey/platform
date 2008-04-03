/*
 * Copyright (c) 2008 LabKey Software Foundation
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

import java.util.Map;
import java.io.IOException;

/**
 * <code>FileAnalysisXarGeneratorSupport</code> can be implemented to support running
 * the <code>XarGeneratorTask</code> on a FileAnalysisJob.  Use Spring configuration
 * to set the implementation as a <code>xarGeneratorSupport</code> property bean
 * on <code>FileAnalysisTaskPipelineSettings</code>.
 */
public interface FileAnalysisXarGeneratorSupport
{
    /**
     * Returns a classpath-relative path to the template resource.
     */
    String getXarTemplateResource(AbstractFileAnalysisJob job);

    /**
     * Returns a map of string replacements to be made in the template.
     */
    Map<String, String> getXarTemplateReplacements(AbstractFileAnalysisJob job) throws IOException;
}
