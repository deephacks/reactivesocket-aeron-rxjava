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
package io.reactivesocket.aeron.client;

import io.reactivesocket.ConnectionSetupHandler;
import io.reactivesocket.ConnectionSetupPayload;
import io.reactivesocket.Frame;
import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.RequestHandler;
import io.reactivesocket.aeron.TestUtil;
import io.reactivesocket.aeron.server.ReactiveSocketAeronServer;
import io.reactivesocket.exceptions.SetupException;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.reactivestreams.Publisher;
import rx.Observable;
import rx.RxReactiveStreams;
import rx.Subscriber;
import uk.co.real_logic.aeron.driver.MediaDriver;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aeron integration tests
 */
@Ignore
public class ReactiveSocketAeronTest {
    static {
        // Uncomment to enable tracing
        //System.setProperty("reactivesocket.aeron.tracingEnabled", "true");
    }

    @BeforeClass
    public static void init() {
        final MediaDriver.Context context = new MediaDriver.Context();
        context.dirsDeleteOnStart(true);

        final MediaDriver mediaDriver = MediaDriver.launch(context);
    }

    @Test(timeout = 3000)
    public void testRequestReponse1() throws Exception {
        requestResponseN(1);
    }

    @Test(timeout = 3000)
    public void testRequestReponse10() throws Exception {
        requestResponseN(10);
    }

    @Test(timeout = 30000)
    public void testRequestReponse10_000() throws Exception {
        requestResponseN(10_000);
    }

    public void requestResponseN(int count) throws Exception {
        AtomicLong server = new AtomicLong();
        ReactiveSocketAeronServer.create(new ConnectionSetupHandler() {
            @Override
            public RequestHandler apply(ConnectionSetupPayload setupPayload) throws SetupException {
                return new RequestHandler() {
                    Frame frame = Frame.from(ByteBuffer.allocate(1));

                    @Override
                    public Publisher<Payload> handleRequestResponse(Payload payload) {
                        String request = TestUtil.byteToString(payload.getData());
                        //System.out.println(Thread.currentThread() +  " Server got => " + request);
                        Observable<Payload> pong = Observable.just(TestUtil.utf8EncodedPayload("pong => " + server.incrementAndGet(), null));
                        return RxReactiveStreams.toPublisher(pong);
                    }

                    @Override
                    public Publisher<Payload> handleChannel(Publisher<Payload> payloads) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleRequestStream(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleSubscription(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleFireAndForget(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleMetadataPush(Payload payload) {
                        return null;
                    }
                };
            }
        });

        InetSocketAddress listenAddress = new InetSocketAddress("localhost", 39790);
        InetSocketAddress clientAddress = new InetSocketAddress("localhost", 39790);

        AeronClientDuplexConnectionFactory cf = AeronClientDuplexConnectionFactory.getInstance();
        cf.addSocketAddressToHandleResponses(listenAddress);
        Publisher<AeronClientDuplexConnection> udpConnection = cf.createUDPConnection(clientAddress);

        System.out.println("Creating new duplex connection");
        AeronClientDuplexConnection connection = RxReactiveStreams.toObservable(udpConnection).toBlocking().single();
        System.out.println("Created duplex connection");

        ReactiveSocket reactiveSocket = ReactiveSocket.fromClientConnection(connection, ConnectionSetupPayload.create("UTF-8", "UTF-8", ConnectionSetupPayload.NO_FLAGS));
        reactiveSocket.startAndWait();

        CountDownLatch latch = new CountDownLatch(count);

        Observable
            .range(1, count)
            .flatMap(i -> {
                System.out.println("pinging => " + i);
                Payload payload = TestUtil.utf8EncodedPayload("ping => " + i, null);
                Publisher<Payload> publisher = reactiveSocket.requestResponse(payload);
                return RxReactiveStreams
                    .toObservable(publisher)
                    .doOnNext(f -> {
                        System.out.println("Got => " + i);
                    })
                    .doOnNext(f -> latch.countDown());
            })
            .subscribe(new Subscriber<Payload>() {
                @Override
                public void onCompleted() {
                    System.out.println("I HAVE COMPLETED $$$$$$$$$$$$$$$$$$$$$$$$$$$$");
                }

                @Override
                public void onError(Throwable e) {
                    System.out.println(Thread.currentThread() +  " counted to => " + latch.getCount());
                    e.printStackTrace();
                }

                @Override
                public void onNext(Payload payload) {

                }
            });

        latch.await();
    }

/*


    @Test(timeout = 100000)
    public void testRequestReponse() throws Exception {
        AtomicLong server = new AtomicLong();
        ReactiveSocketAeronServer.create(new ConnectionSetupHandler() {
            @Override
            public RequestHandler apply(ConnectionSetupPayload setupPayload) throws SetupException {
                return new RequestHandler() {
                    Frame frame = Frame.from(ByteBuffer.allocate(1));

                    @Override
                    public Publisher<Payload> handleRequestResponse(Payload payload) {
                        String request = TestUtil.byteToString(payload.getData());
                        //System.out.println(Thread.currentThread() +  " Server got => " + request);
                        Observable<Payload> pong = Observable.just(TestUtil.utf8EncodedPayload("pong => " + server.incrementAndGet(), null));
                        return RxReactiveStreams.toPublisher(pong);
                    }

                    @Override
                    public Publisher<Payload> handleChannel(Publisher<Payload> payloads) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleRequestStream(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleSubscription(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleFireAndForget(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleMetadataPush(Payload payload) {
                        return null;
                    }
                };
            }
        });

        CountDownLatch latch = new CountDownLatch(1300000);


        ReactiveSocketAeronClient client = ReactiveSocketAeronClient.create("localhost", "localhost");

        Observable
            .range(1, 1300000)
            .flatMap(i -> {
                    //System.out.println("pinging => " + i);
                    Payload payload = TestUtil.utf8EncodedPayload("ping =>" + i, null);
                    return RxReactiveStreams
                        .toObservable(client.requestResponse(payload))
                        .doOnNext(f -> {
                            if (i % 1000 == 0) {
                                System.out.println("Got => " + i);
                            }
                        })
                        .doOnNext(f -> latch.countDown());
                }
            )
            .subscribe(new rx.Subscriber<Payload>() {
                @Override
                public void onCompleted() {
                    System.out.println("I HAVE COMPLETED $$$$$$$$$$$$$$$$$$$$$$$$$$$$");
                }

                @Override
                public void onError(Throwable e) {
                    System.out.println(Thread.currentThread() +  " counted to => " + latch.getCount());
                    e.printStackTrace();
                }

                @Override
                public void onNext(Payload s) {
                    //System.out.println(Thread.currentThread() +  " countdown => " + latch.getCount());
                    //latch.countDown();
                }
            });

        latch.await();
    }

    @Test(timeout = 100000)
    public void testRequestReponseMultiThreaded() throws Exception {
        AtomicLong server = new AtomicLong();
        ReactiveSocketAeronServer.create(new ConnectionSetupHandler() {
            @Override
            public RequestHandler apply(ConnectionSetupPayload setupPayload) throws SetupException {
                return new RequestHandler() {
                    Frame frame = Frame.from(ByteBuffer.allocate(1));

                    @Override
                    public Publisher<Payload> handleRequestResponse(Payload payload) {
                        String request = TestUtil.byteToString(payload.getData());
                        //System.out.println(Thread.currentThread() +  " Server got => " + request);
                        Observable<Payload> pong = Observable.just(TestUtil.utf8EncodedPayload("pong => " + server.incrementAndGet(), null));
                        return RxReactiveStreams.toPublisher(pong);
                    }

                    @Override
                    public Publisher<Payload> handleChannel(Payload initialPayload, Publisher<Payload> payloads) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleRequestStream(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleSubscription(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleFireAndForget(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleMetadataPush(Payload payload) {
                        return null;
                    }
                };
            }
        });

        CountDownLatch latch = new CountDownLatch(10_000);


        ReactiveSocketAeronClient client = ReactiveSocketAeronClient.create("localhost", "localhost");

        Observable
            .range(1, 10_000)
            .flatMap(i -> {
                    //System.out.println("pinging => " + i);
                    Payload payload = TestUtil.utf8EncodedPayload("ping =>" + i, null);
                    return RxReactiveStreams
                        .toObservable(client.requestResponse(payload))
                        .doOnNext(f -> {
                            if (i % 1000 == 0) {
                                System.out.println("Got => " + i);
                            }
                        })
                        .doOnNext(f -> latch.countDown())
                        .subscribeOn(Schedulers.newThread());
                }
            , 8)
            .subscribe(new rx.Subscriber<Payload>() {
                @Override
                public void onCompleted() {
                    System.out.println("I HAVE COMPLETED $$$$$$$$$$$$$$$$$$$$$$$$$$$$");
                }

                @Override
                public void onError(Throwable e) {
                    System.out.println(Thread.currentThread() +  " counted to => " + latch.getCount());
                    e.printStackTrace();
                }

                @Override
                public void onNext(Payload s) {
                    //System.out.println(Thread.currentThread() +  " countdown => " + latch.getCount());
                    //latch.countDown();
                }
            });

        latch.await();
    }

    @Test(timeout = 10000)
    public void sendLargeMessage() throws Exception {

        Random random = new Random();
        byte[] b = new byte[1_000_000];
        random.nextBytes(b);

        ReactiveSocketAeronServer.create(new ConnectionSetupHandler() {
            @Override
            public RequestHandler apply(ConnectionSetupPayload setupPayload) throws SetupException {
                return new RequestHandler() {
                    @Override
                    public Publisher<Payload> handleRequestResponse(Payload payload) {
                        System.out.println("Server got => " + b.length);
                        Observable<Payload> pong = Observable.just(TestUtil.utf8EncodedPayload("pong", null));
                        return RxReactiveStreams.toPublisher(pong);
                    }

                    @Override
                    public Publisher<Payload> handleChannel(Publisher<Payload> payloads) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleRequestStream(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleSubscription(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleFireAndForget(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleMetadataPush(Payload payload) {
                        return null;
                    }
                };
            }
        });

        CountDownLatch latch = new CountDownLatch(2);

        ReactiveSocketAeronClient client = ReactiveSocketAeronClient.create("localhost", "localhost");

        Observable
            .range(1, 2)
            .flatMap(i -> {
                    System.out.println("pinging => " + i);
                    Payload payload = new Payload() {
                        @Override
                        public ByteBuffer getData() {
                            return ByteBuffer.wrap(b);
                        }

                        @Override
                        public ByteBuffer getMetadata() {
                            return ByteBuffer.allocate(0);
                        }
                    };
                    return  RxReactiveStreams.toObservable(client.requestResponse(payload));
                }
            )
            .subscribe(new rx.Subscriber<Payload>() {
                @Override
                public void onCompleted() {
                }

                @Override
                public void onError(Throwable e) {
                    e.printStackTrace();
                }

                @Override
                public void onNext(Payload s) {
                    System.out.println(s + "countdown => " + latch.getCount());
                    latch.countDown();
                }
            });

        latch.await();
    }


    @Test(timeout = 10000)
    public void createTwoServersAndTwoClients()throws Exception {
        Random random = new Random();
        byte[] b = new byte[1];
        random.nextBytes(b);

        ReactiveSocketAeronServer.create(new ConnectionSetupHandler() {
            @Override
            public RequestHandler apply(ConnectionSetupPayload setupPayload) throws SetupException {
                return new RequestHandler() {
                    @Override
                    public Publisher<Payload> handleRequestResponse(Payload payload) {
                        System.out.println("pong 1 => " + payload.getData());
                        Observable<Payload> pong = Observable.just(TestUtil.utf8EncodedPayload("pong server 1", null));
                        return RxReactiveStreams.toPublisher(pong);
                    }

                    @Override
                    public Publisher<Payload> handleChannel(Publisher<Payload> payloads) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleRequestStream(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleSubscription(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleFireAndForget(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleMetadataPush(Payload payload) {
                        return null;
                    }
                };
            }
        });

        ReactiveSocketAeronServer.create(12345, new ConnectionSetupHandler() {
            @Override
            public RequestHandler apply(ConnectionSetupPayload setupPayload) throws SetupException {
                return new RequestHandler() {
                    @Override
                    public Publisher<Payload> handleRequestResponse(Payload payload) {
                        System.out.println("pong 2 => " + payload.getData());
                        Observable<Payload> pong = Observable.just(TestUtil.utf8EncodedPayload("pong server 2", null));
                        return RxReactiveStreams.toPublisher(pong);
                    }

                    @Override
                    public Publisher<Payload> handleChannel(Publisher<Payload> payloads) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleRequestStream(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleSubscription(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleFireAndForget(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleMetadataPush(Payload payload) {
                        return null;
                    }
                };
            }
        });

        int count = 64;

        CountDownLatch latch = new CountDownLatch(2 * count);

        ReactiveSocketAeronClient client = ReactiveSocketAeronClient.create("localhost", "localhost");

        ReactiveSocketAeronClient client2 = ReactiveSocketAeronClient.create("localhost", "localhost", 12345);

        Observable
            .range(1, count)
            .flatMap(i -> {
                    System.out.println("pinging server 1 => " + i);
                    Payload payload = new Payload() {
                        @Override
                        public ByteBuffer getData() {
                            return ByteBuffer.wrap(b);
                        }

                        @Override
                        public ByteBuffer getMetadata() {
                            return ByteBuffer.allocate(0);
                        }
                    };

                    return  RxReactiveStreams.toObservable(client.requestResponse(payload));
                }
            )
            .subscribe(new rx.Subscriber<Payload>() {
                @Override
                public void onCompleted() {
                }

                @Override
                public void onError(Throwable e) {
                    e.printStackTrace();
                }

                @Override
                public void onNext(Payload s) {
                    latch.countDown();
                    System.out.println(s + " countdown server 1 => " + latch.getCount());
                }
            });

        Observable
            .range(1, count)
            .flatMap(i -> {
                    System.out.println("pinging server 2 => " + i);
                    Payload payload = new Payload() {
                        @Override
                        public ByteBuffer getData() {
                            return ByteBuffer.wrap(b);
                        }

                        @Override
                        public ByteBuffer getMetadata() {
                            return ByteBuffer.allocate(0);
                        }
                    };

                    return RxReactiveStreams.toObservable(client2.requestResponse(payload));
                }
            )
            .subscribe(new rx.Subscriber<Payload>() {
                @Override
                public void onCompleted() {
                }

                @Override
                public void onError(Throwable e) {
                    e.printStackTrace();
                }

                @Override
                public void onNext(Payload s) {
                    latch.countDown();
                    System.out.println(s + " countdown server 2 => " + latch.getCount());
                }
            });

        latch.await();
    }

    @Test(timeout = 10000)
    public void testFireAndForget() throws Exception {
        CountDownLatch latch = new CountDownLatch(130);
        ReactiveSocketAeronServer.create(new ConnectionSetupHandler() {
            @Override
            public RequestHandler apply(ConnectionSetupPayload setupPayload) throws SetupException {
                return new RequestHandler() {

                    @Override
                    public Publisher<Payload> handleRequestResponse(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleChannel(Publisher<Payload> payloads) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleRequestStream(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleSubscription(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleFireAndForget(Payload payload) {
                        return new Publisher<Void>() {
                            @Override
                            public void subscribe(Subscriber<? super Void> s) {
                                latch.countDown();
                                s.onComplete();
                            }
                        };
                    }

                    @Override
                    public Publisher<Void> handleMetadataPush(Payload payload) {
                        return null;
                    }
                };
            }
        });

        ReactiveSocketAeronClient client = ReactiveSocketAeronClient.create("localhost", "localhost");

        Observable
            .range(1, 130)
            .flatMap(i -> {
                    System.out.println("pinging => " + i);
                    Payload payload = TestUtil.utf8EncodedPayload("ping =>" + i, null);
                    return RxReactiveStreams.toObservable(client.fireAndForget(payload));
                }
            )
            .subscribe();

        latch.await();
    }

    @Test(timeout = 10000)
    public void testRequestStream() throws Exception {
        CountDownLatch latch = new CountDownLatch(130);
        ReactiveSocketAeronServer.create(new ConnectionSetupHandler() {
            @Override
            public RequestHandler apply(ConnectionSetupPayload setupPayload) throws SetupException {
                return new RequestHandler() {

                    @Override
                    public Publisher<Payload> handleRequestResponse(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleChannel(Publisher<Payload> payloads) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleRequestStream(Payload payload) {
                        return new Publisher<Payload>() {
                            @Override
                            public void subscribe(Subscriber<? super Payload> s) {
                                for (int i = 0; i < 1_000_000; i++) {
                                    s.onNext(new Payload() {
                                        @Override
                                        public ByteBuffer getData() {
                                            return ByteBuffer.allocate(0);
                                        }

                                        @Override
                                        public ByteBuffer getMetadata() {
                                            return ByteBuffer.allocate(0);
                                        }
                                    });
                                }

                            }
                        };
                    }

                    @Override
                    public Publisher<Payload> handleSubscription(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleFireAndForget(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleMetadataPush(Payload payload) {
                        return null;
                    }
                };
            }
        });

        ReactiveSocketAeronClient client = ReactiveSocketAeronClient.create("localhost", "localhost");

        Observable
            .range(1, 1)
            .flatMap(i -> {
                    System.out.println("pinging => " + i);
                    Payload payload = TestUtil.utf8EncodedPayload("ping =>" + i, null);
                    return RxReactiveStreams.toObservable(client.requestStream(payload));
                }
            )
            .doOnNext(i -> latch.countDown())
            .subscribe();

        latch.await();
    }

    @Test(timeout = 75000)
    public void testReconnection() throws Exception {
        System.out.println("--------------------------------------------------------------------------------");

        ReactiveSocketAeronServer server = ReactiveSocketAeronServer.create(new ConnectionSetupHandler() {
            @Override
            public RequestHandler apply(ConnectionSetupPayload setupPayload) throws SetupException {
                return new RequestHandler() {
                    Frame frame = Frame.from(ByteBuffer.allocate(1));

                    @Override
                    public Publisher<Payload> handleRequestResponse(Payload payload) {
                        String request = TestUtil.byteToString(payload.getData());
                        System.out.println(Thread.currentThread() + " Server got => " + request);
                        Observable<Payload> pong = Observable.just(TestUtil.utf8EncodedPayload("pong", null));
                        return RxReactiveStreams.toPublisher(pong);
                    }

                    @Override
                    public Publisher<Payload> handleChannel(Publisher<Payload> payloads) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleRequestStream(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Payload> handleSubscription(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleFireAndForget(Payload payload) {
                        return null;
                    }

                    @Override
                    public Publisher<Void> handleMetadataPush(Payload payload) {
                        return null;
                    }
                };
            }
        });

        System.out.println("--------------------------------------------------------------------------------");

        for (int j = 0; j < 3; j++) {
            CountDownLatch latch = new CountDownLatch(1);

            ReactiveSocketAeronClient client = ReactiveSocketAeronClient.create("localhost", "localhost");

            Observable
                .range(1, 1)
                .flatMap(i -> {
                        Payload payload = TestUtil.utf8EncodedPayload("ping =>" + System.nanoTime(), null);
                        return RxReactiveStreams.toObservable(client.requestResponse(payload));
                    }
                )
                .subscribe(new rx.Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("I HAVE COMPLETED $$$$$$$$$$$$$$$$$$$$$$$$$$$$");
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(Thread.currentThread() + " counted to => " + latch.getCount());
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Payload s) {
                        System.out.println(Thread.currentThread() + " countdown => " + latch.getCount());
                        latch.countDown();
                    }
                });

            latch.await();

            client.close();

            while (server.hasConnections()) {
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
            }

            System.out.println("--------------------------------------------------------------------------------");
        }

    }*/

}
