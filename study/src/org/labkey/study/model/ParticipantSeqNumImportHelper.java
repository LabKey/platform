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
