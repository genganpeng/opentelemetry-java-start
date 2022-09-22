/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.grpc.examples.helloworld;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

class ExampleConfiguration {

  static OpenTelemetry initOpenTelemetry() {
      OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
      if (openTelemetry != null) {
          return openTelemetry;
      }
      Resource resource = Resource.getDefault()
              .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "logical-service-name")));

      SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
          .registerMetricReader(PeriodicMetricReader.builder(OtlpGrpcMetricExporter.builder().build()).build())
          .setResource(resource)
          .build();

      SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
              .addSpanProcessor(BatchSpanProcessor.builder(OtlpGrpcSpanExporter.builder().build()).build())
              .setResource(resource)
              .build();


      openTelemetry = OpenTelemetrySdk.builder()
              .setTracerProvider(sdkTracerProvider)
              .setMeterProvider(sdkMeterProvider)
              .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
              .buildAndRegisterGlobal();

    // it's always a good idea to shutdown the SDK when your process exits.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.err.println(
                      "*** forcing the Span Exporter to shutdown and process the remaining spans");
                  sdkTracerProvider.shutdown();
                  sdkMeterProvider.shutdown();
                  System.err.println("*** Trace Exporter shut down");
                }));

    return openTelemetry;
  }
}
