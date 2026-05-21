package org.labkey.api.dataiterator;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Extension point for DataClass-specific transformations in the pre-trigger DataIterator pipeline.
 * Registered per DataClass name via {@link org.labkey.api.exp.api.ExperimentService#registerDataClassDataIteratorTransformer}.
 * A fresh instance is created for each import operation since implementations may be stateful
 * between {@link #prepareTranslator} and {@link #wrapDataIterator}.
 */
public interface DataClassDataIteratorTransformer
{
    /**
     * Called during SimpleTranslator setup to inspect input columns and add placeholder columns
     * (e.g., via {@link SimpleTranslator#addNullColumn}) that will be populated by the wrapping DataIterator.
     *
     * @return true if the transformer is active and {@link #wrapDataIterator} should be called
     */
    boolean prepareTranslator(@NotNull SimpleTranslator step0,
                              @NotNull Map<String, Integer> inputColumnNameMap,
                              @NotNull DataIteratorContext context);

    /**
     * Wraps the DataIterator to apply DataClass-specific transformations.
     * Called after the pre-trigger pipeline is assembled, only if {@link #prepareTranslator} returned true.
     */
    @NotNull
    DataIterator wrapDataIterator(@NotNull DataIterator input, @NotNull DataIteratorContext context);
}
