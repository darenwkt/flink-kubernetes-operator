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

import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.core.plugin.PluginUtils;
import org.apache.flink.kubernetes.operator.config.FlinkConfigManager;
import org.apache.flink.kubernetes.operator.observer.FlinkResourceObserverPlugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/** Validator utilities. */
public final class ObserverUtils {

    private static final Logger LOG = LoggerFactory.getLogger(FlinkUtils.class);

    public static Set<FlinkResourceObserverPlugin> discoverObserverPlugin(
            FlinkConfigManager configManager,
            FlinkResourceObserverPlugin.ObserverType observerType) {
        Set<FlinkResourceObserverPlugin> observers = new HashSet<>();

        PluginUtils.createPluginManagerFromRootFolder(configManager.getDefaultConfig())
                .load(FlinkResourceObserverPlugin.class)
                .forEachRemaining(
                        observer -> {
                            if (observer.getObserverType().equals(observerType)) {
                                LOG.info(
                                        "Discovered custom observer from plugin directory[{}]: {}. for Observer Type: {}",
                                        System.getenv()
                                                .getOrDefault(
                                                        ConfigConstants.ENV_FLINK_PLUGINS_DIR,
                                                        ConfigConstants.DEFAULT_FLINK_PLUGINS_DIRS),
                                        observer.getClass().getName(),
                                        observerType);
                                observer.configure(configManager.getDefaultConfig());
                                observers.add(observer);
                            }
                        });
        return observers;
    }
}
