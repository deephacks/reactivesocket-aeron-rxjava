/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivesocket.aeron.old.server;

import io.reactivesocket.DuplexConnection;
import io.reactivesocket.Frame;
import io.reactivesocket.aeron.internal.AeronUtil;
import io.reactivesocket.aeron.internal.Loggable;
import io.reactivesocket.aeron.internal.MessageType;
import io.reactivesocket.aeron.internal.NotConnectedException;
import io.reactivesocket.rx.Completable;
import io.reactivesocket.rx.Disposable;
import io.reactivesocket.rx.Observable;
import io.reactivesocket.rx.Observer;
import org.reactivestreams.Publisher;
import rx.RxReactiveStreams;
import uk.co.real_logic.aeron.Publication;
import uk.co.real_logic.agrona.BitUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class AeronServerDuplexConnection implements DuplexConnection, Loggable {
    private final Publication publication;
    private final CopyOnWriteArrayList<Observer<Frame>> subjects;

    public AeronServerDuplexConnection(
        Publication publication) {
        this.publication = publication;
        this.subjects = new CopyOnWriteArrayList<>();
    }

    public List<? extends Observer<Frame>> getSubscriber() {
        return subjects;
    }

    @Override
    public final Observable<Frame> getInput() {
        if (isTraceEnabled()) {
            trace("-------getting input for publication session id {} ", publication.sessionId());
        }

        return new Observable<Frame>() {
            public void subscribe(Observer<Frame> o) {
                o.onSubscribe(new Disposable() {
                    @Override
                    public void dispose() {
                        if (isTraceEnabled()) {
                            trace("removing Observer for publication with session id {} ", publication.sessionId());
                        }

                        subjects.removeIf(s -> s == o);
                    }
                });

                subjects.add(o);
            }
        };
    }

    private static volatile short count;


    private short getCount() {
        return count++;
    }

    @Override
    public void addOutput(Publisher<Frame> o, Completable callback) {
        RxReactiveStreams.toObservable(o).flatMap(frame ->
        {

            if (isTraceEnabled()) {
                trace("Server with publication session id {} sending frame => {}", publication.sessionId(), frame.toString());
            }

            final ByteBuffer byteBuffer = frame.getByteBuffer();
            final int length = frame.length() + BitUtil.SIZE_OF_INT;

            try {
                AeronUtil.tryClaimOrOffer(publication, (offset, buffer) -> {
                    buffer.putShort(offset, getCount());
                    buffer.putShort(offset + BitUtil.SIZE_OF_SHORT, (short) MessageType.FRAME.getEncodedType());
                    buffer.putBytes(offset + BitUtil.SIZE_OF_INT, byteBuffer, frame.offset(), frame.length());
                }, length);
            } catch (Throwable t) {
                return rx.Observable.error(t);
            }

            if (isTraceEnabled()) {
                trace("Server with publication session id {} sent frame  with ReactiveSocket stream id => {}", publication.sessionId(), frame.getStreamId());
            }


            return rx.Observable.empty();
        }
        )
            .doOnCompleted(()-> System.out.println("-----ehere-----")).
            subscribe(v -> {
            }, callback::error, callback::success);



        //o.subscribe(new ServerSubscription(publication, callback));
    }

    void ackEstablishConnection(int ackSessionId) {
        debug("Acking establish connection for session id => {}", ackSessionId);
        for (int i = 0; i < 10; i++) {
            try {
                AeronUtil.tryClaimOrOffer(publication, (offset, buffer) -> {
                    buffer.putShort(offset, (short) 0);
                    buffer.putShort(offset + BitUtil.SIZE_OF_SHORT, (short) MessageType.ESTABLISH_CONNECTION_RESPONSE.getEncodedType());
                    buffer.putInt(offset + BitUtil.SIZE_OF_INT, ackSessionId);
                }, 2 * BitUtil.SIZE_OF_INT, 30, TimeUnit.SECONDS);
                break;
            } catch (NotConnectedException ne) {
                if (i >= 10) {
                    throw ne;
                }
            }
        }
    }

    @Override
    public void close() {
        publication.close();
    }
}