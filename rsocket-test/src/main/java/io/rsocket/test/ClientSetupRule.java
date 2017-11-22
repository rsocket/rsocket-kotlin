/*
 * Copyright 2016 Netflix, Inc.
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
 *//*


   package io.rsocket.test;

   import io.rsocket.android.Closeable;
   import io.rsocket.android.RSocket;
   import io.rsocket.android.RSocketFactory;
   import io.rsocket.transport.ClientTransport;
   import io.rsocket.transport.ServerTransport;
   import java.util.function.BiFunction;
   import java.util.function.Function;
   import java.util.function.Supplier;
   import org.junit.rules.ExternalResource;
   import org.junit.runner.Description;
   import org.junit.runners.model.Statement;
   import reactor.core.publisher.Mono;

   public class ClientSetupRule<T, S extends Closeable> extends ExternalResource {

     private Supplier<T> addressSupplier;
     private BiFunction<T, S, RSocket> clientConnector;
     private Function<T, S> serverInit;

     private RSocket client;

     public ClientSetupRule(
         Supplier<T> addressSupplier,
         BiFunction<T, S, ClientTransport> clientTransportSupplier,
         Function<T, ServerTransport<S>> serverTransportSupplier) {
       this.addressSupplier = addressSupplier;

       this.serverInit =
           address ->
               RSocketFactory.INSTANCE
                   .receive()
                   .acceptor((setup, sendingSocket) -> Mono.just(new TestRSocket()))
                   .transport(serverTransportSupplier.apply(address))
                   .start()
                   .block();

       this.clientConnector =
           (address, server) ->
               RSocketFactory.INSTANCE
                   .connect()
                   .transport(clientTransportSupplier.apply(address, server))
                   .start()
                   .doOnError(Throwable::printStackTrace)
                   .block();
     }

     @Override
     public Statement apply(Statement base, Description description) {
       return new Statement() {
         @Override
         public void evaluate() throws Throwable {
           T address = addressSupplier.get();
           S server = serverInit.apply(address);
           client = clientConnector.apply(address, server);
           base.evaluate();
           server.close().block();
         }
       };
     }

     public RSocket getRSocket() {
       return client;
     }
   }
   */
