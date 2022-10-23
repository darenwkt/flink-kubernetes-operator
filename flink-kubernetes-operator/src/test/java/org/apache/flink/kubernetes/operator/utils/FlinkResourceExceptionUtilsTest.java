/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.kubernetes.operator.utils;

import org.apache.flink.kubernetes.operator.crd.AbstractFlinkResource;
import org.apache.flink.kubernetes.operator.crd.FlinkDeployment;
import org.apache.flink.kubernetes.operator.crd.FlinkSessionJob;
import org.apache.flink.kubernetes.operator.crd.spec.AbstractFlinkSpec;
import org.apache.flink.kubernetes.operator.crd.spec.ExceptionSpec;
import org.apache.flink.kubernetes.operator.crd.spec.FlinkDeploymentSpec;
import org.apache.flink.kubernetes.operator.crd.spec.FlinkSessionJobSpec;
import org.apache.flink.kubernetes.operator.crd.status.CommonStatus;
import org.apache.flink.kubernetes.operator.exception.FlinkResourceException;
import org.apache.flink.kubernetes.operator.exception.ReconciliationException;
import org.apache.flink.runtime.rest.util.RestClientException;
import org.apache.flink.util.FlinkRuntimeException;

import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpResponseStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.apache.flink.kubernetes.operator.utils.FlinkResourceExceptionUtils.updateFlinkResourceException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Test class for {@link FlinkResourceExceptionUtils}. */
public class FlinkResourceExceptionUtilsTest {

    @Test
    public void testUpdateFlinkResourceException() throws JsonProcessingException {

        ReconciliationException reconciliationException = getTestException();

        List<AbstractFlinkResource> resources = getTestResources();

        for (AbstractFlinkResource resource : resources) {
            updateFlinkResourceException(reconciliationException, resource);

            boolean isStackTraceEnabled =
                    Optional.of(resource)
                            .map(r -> (AbstractFlinkSpec) r.getSpec())
                            .map(AbstractFlinkSpec::getException)
                            .map(ExceptionSpec::isStackTraceEnabled)
                            .orElseThrow(RuntimeException::new);

            String errorJson =
                    Optional.ofNullable(resource)
                            .map(r -> (CommonStatus) r.getStatus())
                            .map(CommonStatus::getError)
                            .orElseThrow(RuntimeException::new);

            FlinkResourceException flinkResourceException =
                    new ObjectMapper().readValue(errorJson, FlinkResourceException.class);

            assertNull(flinkResourceException.getAdditionalMetadata());
            assertEquals("reconciliation exception message", flinkResourceException.getMessage());
            assertEquals(
                    "org.apache.flink.kubernetes.operator.exception.ReconciliationException",
                    flinkResourceException.getType());

            assertEquals(2, flinkResourceException.getFilteredExceptions().size());
            flinkResourceException
                    .getFilteredExceptions()
                    .forEach(
                            exception -> {
                                if (exception.getType().contains("RestClientException")) {
                                    assertEquals(
                                            "rest client exception message",
                                            exception.getMessage());
                                    assertEquals(
                                            400,
                                            exception
                                                    .getAdditionalMetadata()
                                                    .get("httpResponseCode"));
                                } else if (exception.getType().contains("FlinkRuntimeException")) {
                                    assertEquals(
                                            "flink runtime exception message",
                                            exception.getMessage());
                                } else {
                                    throw new RuntimeException(
                                            "Exception not found in ExceptionFilters");
                                }
                            });

            if (!isStackTraceEnabled) {
                assertNull(flinkResourceException.getStackTraceElements());
            } else {
                assertEquals(
                        flinkResourceException.getStackTraceElements().length,
                        reconciliationException.getStackTrace().length);
            }
        }
    }

    private ReconciliationException getTestException() {
        return new ReconciliationException(
                "reconciliation exception message",
                new Exception(
                        "exception message",
                        new RestClientException(
                                "rest client exception message",
                                new FlinkRuntimeException("flink runtime exception message"),
                                new HttpResponseStatus(400, "http response status"))));
    }

    private List<AbstractFlinkResource> getTestResources() {
        return List.of(
                getTestFlinkSessionJob(false),
                getTestFlinkSessionJob(true),
                getTestFlinkDeployment(false),
                getTestFlinkDeployment(true));
    }

    private FlinkSessionJob getTestFlinkSessionJob(boolean stackTraceEnabled) {
        FlinkSessionJob flinkSessionJob = new FlinkSessionJob();
        flinkSessionJob.setSpec(
                FlinkSessionJobSpec.builder()
                        .exception(
                                ExceptionSpec.builder()
                                        .exceptionFilters(
                                                List.of(
                                                        "RestClientException",
                                                        "FlinkRuntimeException"))
                                        .stackTraceEnabled(stackTraceEnabled)
                                        .build())
                        .build());

        return flinkSessionJob;
    }

    private FlinkDeployment getTestFlinkDeployment(boolean stackTraceEnabled) {
        FlinkDeployment flinkDeployment = new FlinkDeployment();
        flinkDeployment.setSpec(
                FlinkDeploymentSpec.builder()
                        .exception(
                                ExceptionSpec.builder()
                                        .exceptionFilters(
                                                List.of(
                                                        "RestClientException",
                                                        "FlinkRuntimeException"))
                                        .stackTraceEnabled(stackTraceEnabled)
                                        .build())
                        .build());

        return flinkDeployment;
    }
}
