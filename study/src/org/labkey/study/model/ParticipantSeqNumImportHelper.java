/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.dataiterator.DataIterator;

import java.util.concurrent.Callable;

public class ParticipantSeqNumImportHelper
{
    public static Callable<Object> getCallable(
            @NotNull final DataIterator it,
            @Nullable final Integer indexPtidOutput,
            @Nullable final Integer indexSequenceNum
    )
    {
        return () -> {
            String participantId = indexPtidOutput == null ? "" : DatasetDataIteratorBuilder.getOutputString(it, indexPtidOutput);
            Double sequenceNum = DatasetDataIteratorBuilder.getOutputDouble(it, indexSequenceNum);

            return translateParticipantSeqNum(participantId, sequenceNum);
        };
    }

    public static String translateParticipantSeqNum(String participantId, Double sequenceNum)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(participantId.trim());
        sb.append("|");
        if (sequenceNum != null)
            sb.append(DatasetDataIteratorBuilder.SEQUENCE_NUM_FORMAT.format(sequenceNum));

        return sb.toString();
    }
}
