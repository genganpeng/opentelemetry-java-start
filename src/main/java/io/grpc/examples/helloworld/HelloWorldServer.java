package io.grpc.examples.helloworld;/*
 * Copyright 2015 The gRPC Authors
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

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class HelloWorldServer {
  private static final Logger logger = LoggerFactory.getLogger(HelloWorldServer.class.getName());
  private static final OpenTelemetry openTelemetry = ExampleConfiguration.initOpenTelemetry();
  private Server server;

  private void start() throws IOException {
    /* The port on which the server should run */
    int port = 50051;
    server = ServerBuilder.forPort(port)
        .addService(new GreeterImpl())
        .build()
        .start();
    logger.info("Server started, listening on " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        try {
          HelloWorldServer.this.stop();
        } catch (InterruptedException e) {
          e.printStackTrace(System.err);
        }
        System.err.println("*** server shut down");
      }
    });
  }

  private void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  /**
   * Main launches the server from the command line.
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    final HelloWorldServer server = new HelloWorldServer();
    server.start();
    server.blockUntilShutdown();
  }

  static class GreeterImpl extends GreeterGrpc.GreeterImplBase {

    @Override
    public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
      HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
      Tracer tracer =
              openTelemetry.getTracer("instrumentation-library-name", "1.0.0");
      Span span = tracer.spanBuilder("my span").startSpan();

      // Make the span the current span
      try (Scope ss = span.makeCurrent()) {
        // In this scope, the span is the current/active span
        span.setAttribute(SemanticAttributes.HTTP_METHOD, "grpc");
      } finally {
        span.end();
      }

      // Gets or creates a named meter instance
      Meter meter = openTelemetry.meterBuilder("instrumentation-library-name")
              .setInstrumentationVersion("1.0.0")
              .build();
      // Build counter e.g. LongCounter
      LongCounter counter = meter
              .counterBuilder("processed_jobs")
              .setDescription("Processed jobs")
              .setUnit("1")
              .build();
      // It is recommended that the API user keep a reference to Attributes they will record against
      Attributes attributes = Attributes.of(AttributeKey.stringKey("key"), "value");
      // Record data
      counter.add(123, attributes);

      meter
              .gaugeBuilder("cpu_usage")
              .setDescription("CPU Usage")
              .setUnit("ms")
              .buildWithCallback(measurement -> {
                measurement.record(2.0, Attributes.of(AttributeKey.stringKey("cpuKey"), "value"));
              });

      logger.info("say Hello!");


      responseObserver.onNext(reply);
      responseObserver.onCompleted();
    }
  }
}
