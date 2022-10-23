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
import org.apache.flink.kubernetes.operator.crd.spec.AbstractFlinkSpec;
import org.apache.flink.kubernetes.operator.crd.spec.ExceptionSpec;
import org.apache.flink.kubernetes.operator.exception.DeploymentFailedException;
import org.apache.flink.kubernetes.operator.exception.FlinkResourceException;
import org.apache.flink.kubernetes.operator.exception.ReconciliationException;
import org.apache.flink.runtime.rest.util.RestClientException;
import org.apache.flink.util.ExceptionUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Flink Resource Exception utilities. */
public final class FlinkResourceExceptionUtils {

    public static <R extends AbstractFlinkResource> void updateFlinkResourceException(
            Throwable cause, R resource) {

        boolean isStackTraceEnabled =
                Optional.ofNullable((AbstractFlinkSpec) resource.getSpec())
                        .map(AbstractFlinkSpec::getException)
                        .map(ExceptionSpec::isStackTraceEnabled)
                        .orElse(false);
        List<String> exceptionFilters =
                Optional.ofNullable((AbstractFlinkSpec) resource.getSpec())
                        .map(AbstractFlinkSpec::getException)
                        .map(ExceptionSpec::getExceptionFilters)
                        .orElse(Collections.emptyList());

        FlinkResourceException flinkResourceException =
                getFlinkResourceException(cause, exceptionFilters, isStackTraceEnabled);

        try {
            ((AbstractFlinkResource<?, ?>) resource)
                    .getStatus()
                    .setError(convertToJson(flinkResourceException));
        } catch (Exception e) {
            ((AbstractFlinkResource<?, ?>) resource)
                    .getStatus()
                    .setError(
                            (e instanceof ReconciliationException)
                                    ? e.getCause().toString()
                                    : e.toString());
        }
    }

    private static FlinkResourceException getFlinkResourceException(
            Throwable cause, List<String> exceptionFilters, boolean isStackTraceEnabled) {
        FlinkResourceException flinkResourceException =
                convertToFlinkResourceException(cause, isStackTraceEnabled);
        List<FlinkResourceException> filteredExceptionList =
                getFilteredException(cause, exceptionFilters, isStackTraceEnabled);

        if (!filteredExceptionList.isEmpty()) {
            flinkResourceException.setFilteredExceptions(filteredExceptionList);
        }

        return flinkResourceException;
    }

    private static List<FlinkResourceException> getFilteredException(
            Throwable cause, List<String> exceptionFilters, boolean isStackTraceEnabled) {
        return exceptionFilters.stream()
                .map(
                        (exceptionFilter) ->
                                ExceptionUtils.findThrowable(
                                        cause,
                                        (t) -> t.getClass().getName().contains(exceptionFilter)))
                .filter(Optional::isPresent)
                .map(
                        (exception) ->
                                convertToFlinkResourceException(
                                        exception.get(), isStackTraceEnabled))
                .collect(Collectors.toList());
    }

    private static FlinkResourceException convertToFlinkResourceException(
            Throwable cause, boolean isStackTraceEnabled) {
        FlinkResourceException flinkResourceException =
                FlinkResourceException.builder()
                        .type(cause.getClass().getName())
                        .message(cause.getMessage())
                        .build();

        if (isStackTraceEnabled) {
            flinkResourceException.setStackTraceElements(cause.getStackTrace());
        }

        if (cause instanceof RestClientException) {
            flinkResourceException.setAdditionalMetadata(
                    Map.of(
                            "httpResponseCode",
                            ((RestClientException) cause).getHttpResponseStatus().code()));
        }

        if (cause instanceof DeploymentFailedException) {
            flinkResourceException.setAdditionalMetadata(
                    Map.of("reason", ((DeploymentFailedException) cause).getReason()));
        }

        return flinkResourceException;
    }

    private static String convertToJson(FlinkResourceException flinkResourceException)
            throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(flinkResourceException);
    }
}
