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

package org.apache.flink.kubernetes.operator.autoscaler.metrics;

import org.apache.flink.runtime.rest.messages.job.metrics.AggregatedMetric;

import java.util.function.Function;

/** Enum specifying which aggregator to use when getting a metric value. */
public enum MetricAggregator {
    AVG(AggregatedMetric::getAvg),
    MAX(AggregatedMetric::getMax),
    MIN(AggregatedMetric::getMin);

    private final Function<AggregatedMetric, Double> getter;

    MetricAggregator(Function<AggregatedMetric, Double> getter) {
        this.getter = getter;
    }

    public double get(AggregatedMetric metric) {
        if (metric != null) {
            return getter.apply(metric);
        } else {
            return Double.NaN;
        }
    }
}
