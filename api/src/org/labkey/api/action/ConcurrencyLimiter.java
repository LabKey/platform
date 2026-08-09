/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.action;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.TooManyRequestsException;
import org.labkey.api.view.ViewContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enforces {@link ConcurrencyLimit} on behalf of {@link SpringActionController}. Holds one {@link Semaphore} per
 * action class that declares the annotation.
 */
public class ConcurrencyLimiter
{
    private static final Logger LOG = LogHelper.getLogger(ConcurrencyLimiter.class, "Rejects requests to actions that limit their concurrency when too many are already in flight");

    static final String GENERIC_MESSAGE = "The server is already handling as many simultaneous requests for this operation as it allows. Please retry in a few moments.";

    /**
     * One limiter (and therefore one set of permits) per action class. {@link ConcurrentHashMap} won't store a null
     * value, so unannotated actions map to {@link #UNLIMITED}.
     */
    private static final Map<Class<?>, ConcurrencyLimiter> LIMITERS = new ConcurrentHashMap<>();

    /**
     * Limiter for an action that declares no limit.
     */
    private static final ConcurrencyLimiter UNLIMITED = new ConcurrencyLimiter();

    /**
     * Handed out by {@link #UNLIMITED}; holds no permit, so closing it must do nothing.
     */
    private static final Permit NOOP_PERMIT = () -> {};

    /**
     * Returned by {@link #acquire}; release the permit by closing it, ideally via try-with-resources
     */
    public interface Permit extends AutoCloseable
    {
        @Override
        void close();
    }

    /**
     * Reserve one of the action's permits, if it declares a {@link ConcurrencyLimit}.
     *
     * @return a {@link Permit} that the caller must close once the action has finished executing
     * @throws TooManyRequestsException if no permit becomes available within the action's configured timeout
     */
    public static Permit acquire(@NotNull Class<?> actionClass, @Nullable ViewContext context)
    {
        return LIMITERS.computeIfAbsent(actionClass, ConcurrencyLimiter::resolve).acquirePermit(context);
    }

    private static ConcurrencyLimiter resolve(Class<?> actionClass)
    {
        ConcurrencyLimit limit = actionClass.getDeclaredAnnotation(ConcurrencyLimit.class);

        return null == limit ? UNLIMITED : new ConcurrencyLimiter(actionClass, limit);
    }

    /**
     * All three are null for the {@link #UNLIMITED} sentinel and non-null for every other instance.
     */
    private final Class<?> _actionClass;
    private final ConcurrencyLimit _limit;
    private final Semaphore _semaphore;

    private ConcurrencyLimiter()
    {
        _actionClass = null;
        _limit = null;
        _semaphore = null;
    }

    private ConcurrencyLimiter(@NotNull Class<?> actionClass, @NotNull ConcurrencyLimit limit)
    {
        if (limit.value() < 1)
            throw new IllegalStateException("@ConcurrencyLimit on " + actionClass.getName() + " must allow at least one concurrent request, but was " + limit.value());

        _actionClass = actionClass;
        _limit = limit;
        // Fair, so a steady stream of new requests can't starve one that's already waiting
        _semaphore = new Semaphore(limit.value(), true);
    }

    private Permit acquirePermit(@Nullable ViewContext context)
    {
        if (this == UNLIMITED)
            return NOOP_PERMIT;

        try
        {
            if (!_semaphore.tryAcquire(_limit.timeoutSeconds(), TimeUnit.SECONDS))
                throw reject(context);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw reject(context);
        }

        // Releasing twice would permanently inflate the pool and quietly defeat the limit, so make close() idempotent
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true))
                _semaphore.release();
        };
    }

    private TooManyRequestsException reject(@Nullable ViewContext context)
    {
        LOG.info("Rejecting request to {} for user {} in {}: {} requests already in progress", _actionClass.getName(),
                null == context ? "<unknown>" : context.getUser(),
                null == context || null == context.getContainer() ? "<unknown>" : context.getContainer().getPath(),
                _limit.value());

        String message = _limit.message().isEmpty() ? GENERIC_MESSAGE : _limit.message();
        return new TooManyRequestsException(message, _limit.retryAfterSeconds());
    }

    public static class TestCase extends Assert
    {
        @ConcurrencyLimit(value = 2, timeoutSeconds = 0, retryAfterSeconds = 7, message = "Slow down")
        private static class LimitedAction
        {
        }

        /**
         * Deliberately carries no annotation of its own - the limit is not inherited.
         */
        private static class SubclassAction extends LimitedAction
        {
        }

        @ConcurrencyLimit(1)
        private static class SingleRequestAction
        {
        }

        private static class UnlimitedAction
        {
        }

        @ConcurrencyLimit(0)
        private static class BadLimitAction
        {
        }

        @Test
        public void testUnlimitedActionIsNotThrottled()
        {
            // Many more than any limit would allow, all held at once
            for (int i = 0; i < 100; i++)
                //noinspection resource
                ConcurrencyLimiter.acquire(UnlimitedAction.class, null);
        }

        @Test
        public void testRejectsBeyondLimit()
        {
            try (Permit p1 = acquire(LimitedAction.class, null); Permit p2 = acquire(LimitedAction.class, null))
            {
                assertNotNull(p1);
                assertNotNull(p2);

                @SuppressWarnings("resource") TooManyRequestsException e = assertThrows(TooManyRequestsException.class, () -> acquire(LimitedAction.class, null));
                assertEquals(TooManyRequestsException.SC_TOO_MANY_REQUESTS, e.getStatus());
                assertEquals("Slow down", e.getMessage());
                assertEquals(7, e.getRetryAfterSeconds());
            }

            // Permits are back after the try-with-resources released them
            acquire(LimitedAction.class, null).close();
        }

        @Test
        public void testLimitIsNotInherited()
        {
            // More than the superclass's limit of two
            try (Permit p1 = acquire(SubclassAction.class, null); Permit p2 = acquire(SubclassAction.class, null); Permit p3 = acquire(SubclassAction.class, null))
            {
                assertNotNull(p1);
                assertNotNull(p2);
                assertNotNull(p3);

                // The superclass still has both of its own permits available
                try (Permit p4 = acquire(LimitedAction.class, null); Permit p5 = acquire(LimitedAction.class, null))
                {
                    assertNotNull(p4);
                    assertNotNull(p5);
                }
            }
        }

        @Test
        public void testGenericMessage()
        {
            try (Permit ignored = acquire(SingleRequestAction.class, null))
            {
                //noinspection resource
                assertEquals(GENERIC_MESSAGE, assertThrows(TooManyRequestsException.class, () -> acquire(SingleRequestAction.class, null)).getMessage());
            }
        }

        @Test
        public void testNonPositiveLimitIsRejected()
        {
            //noinspection resource
            assertThrows(IllegalStateException.class, () -> acquire(BadLimitAction.class, null));
        }
    }
}
