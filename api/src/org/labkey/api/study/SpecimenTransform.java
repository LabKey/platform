/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.nio.file.Path;

/**
 * User: klum
 * Date: 11/12/13
 */
public interface SpecimenTransform
{
    /**
     * Returns the descriptive name
     */
    String getName();

    /**
     * Returns whether module containing transform is present for a container
     */
    boolean isValid(Container container);

    /**
     * Returns whether transform is active for a container
     */
    boolean isActive(Container container);

    /**
     * Returns the file type that this transform can accept
     */
    FileType getFileType();

    /**
     * Transform the input file into a specimen archive that a basic specimen import can
     * process.
     */
    @Deprecated
    void transform(@Nullable PipelineJob job, File input, File outputArchive) throws PipelineJobException;

    default void transform(@Nullable PipelineJob job, Path input, Path outputArchive) throws PipelineJobException
    {
        //TODO this should be implemented in the inheriting classes
        //  defaulting for now to prevent build issues
        transform(job, input.toFile(), outputArchive.toFile());
    }

    /**
     * An optional post transform step.
     */
    void postTransform(@Nullable PipelineJob job, File input, File outputArchive) throws PipelineJobException;

    @Nullable
    ActionURL getManageAction(Container c, User user);

    /**
     * Returns and saved configuration information
     * @param c
     * @param user
     * @return
     */
    ExternalImportConfig getExternalImportConfig(Container c, User user) throws ValidationException;

    /**
     * An optional capability to import from an external (API) source, data that can be transformed into
     * a LabKey compatible specimen archive
     *
     * @param importConfig configuration object
     * @param inputArchive the file to write the externally sourced data into
     */
    void importFromExternalSource(@Nullable PipelineJob job, ExternalImportConfig importConfig, File inputArchive) throws PipelineJobException;

    interface ExternalImportConfig
    {
        String getBaseServerUrl();
        String getUsername();
        String getPassword();
    }
}
