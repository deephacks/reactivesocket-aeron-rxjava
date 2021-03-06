/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivesocket.aeron.server;

import io.reactivesocket.aeron.internal.Constants;
import io.reactivesocket.aeron.internal.Loggable;
import uk.co.real_logic.aeron.*;
import uk.co.real_logic.agrona.TimerWheel;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static io.reactivesocket.aeron.internal.Constants.SERVER_IDLE_STRATEGY;

/**
 * Class that manages the Aeron instance and the server's polling thread. Lets you register more
 * than one NewImageHandler to Aeron after the it's the Aeron instance has started
 */
public class ServerAeronManager implements Loggable {
    private static final ServerAeronManager INSTANCE = new ServerAeronManager();

    private final Aeron aeron;

    private CopyOnWriteArrayList<AvailableImageHandler> availableImageHandlers = new CopyOnWriteArrayList<>();

    private CopyOnWriteArrayList<UnavailableImageHandler> unavailableImageHandlers = new CopyOnWriteArrayList<>();

    private CopyOnWriteArrayList<FragmentAssemblerHolder> fragmentAssemblerHolders = new CopyOnWriteArrayList<>();

    private TimerWheel timerWheel;

    public ServerAeronManager() {
        final Aeron.Context ctx = new Aeron.Context();
        ctx.availableImageHandler(this::availableImageHandler);
        ctx.unavailableImageHandler(this::unavailableImage);
        ctx.errorHandler(t -> error("an exception occurred", t));

        aeron = Aeron.connect(ctx);

        this.timerWheel = new TimerWheel(Constants.SERVER_TIMER_WHEEL_TICK_DURATION_MS, TimeUnit.MILLISECONDS, Constants.SERVER_TIMER_WHEEL_BUCKETS);

        poll();
    }

    public static ServerAeronManager getInstance() {
        return INSTANCE;
    }

    public void addAvailableImageHander(AvailableImageHandler handler) {
        availableImageHandlers.add(handler);
    }

    public void addUnavailableImageHandler(UnavailableImageHandler handler) {
        unavailableImageHandlers.add(handler);
    }

    public void addSubscription(Subscription subscription, FragmentAssembler fragmentAssembler) {
        debug("Adding subscription with session id {}", subscription.streamId());
        fragmentAssemblerHolders.add(new FragmentAssemblerHolder(subscription, fragmentAssembler));
    }

    public void removeSubscription(Subscription subscription) {
        debug("Removing subscription with session id {}", subscription.streamId());
        fragmentAssemblerHolders.removeIf(s -> s.subscription == subscription);
    }

    private void availableImageHandler(Image image, Subscription subscription, long joiningPosition, String sourceIdentity) {
        availableImageHandlers
                .forEach(handler -> handler.onAvailableImage(image, subscription, joiningPosition, sourceIdentity));
    }

    private void unavailableImage(Image image, Subscription subscription, long position) {
        unavailableImageHandlers
                .forEach(handler -> handler.onUnavailableImage(image, subscription, position));
    }

    public Aeron getAeron() {
        return aeron;
    }

    public TimerWheel getTimerWheel() {
        return timerWheel;
    }

    void poll() {
        Thread dutyThread = new Thread(() -> {
            for (; ; ) {
                try {
                    int poll = 0;
                    for (FragmentAssemblerHolder sh : fragmentAssemblerHolders) {
                        try {
                            if (sh.subscription.isClosed()) {
                                continue;
                            }

                            poll += sh.subscription.poll(sh.fragmentAssembler, Integer.MAX_VALUE);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }

                    SERVER_IDLE_STRATEGY.idle(poll);

                    try {
                        if (timerWheel.computeDelayInMs() < 0) {
                            timerWheel.expireTimers();
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                } catch (Throwable t) {
                    t.printStackTrace();
                }

            }


        });
        dutyThread.setName("reactive-socket-aeron-server");
        dutyThread.setDaemon(true);
        dutyThread.start();
    }

    private class FragmentAssemblerHolder {
        private Subscription subscription;
        private FragmentAssembler fragmentAssembler;

        public FragmentAssemblerHolder(Subscription subscription, FragmentAssembler fragmentAssembler) {
            this.subscription = subscription;
            this.fragmentAssembler = fragmentAssembler;
        }
    }
}
