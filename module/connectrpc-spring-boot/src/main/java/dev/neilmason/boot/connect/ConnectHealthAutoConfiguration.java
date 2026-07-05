/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.neilmason.boot.connect;

import io.grpc.BindableService;
import io.grpc.protobuf.services.HealthStatusManager;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({ BindableService.class, HealthStatusManager.class })
@ConditionalOnProperty(prefix = "connect", name = "health.enabled", havingValue = "true", matchIfMissing = true)
public class ConnectHealthAutoConfiguration {

	@Bean(destroyMethod = "enterTerminalState")
	@ConditionalOnMissingBean
	public HealthStatusManager connectHealthStatusManager() {
		return new HealthStatusManager();
	}

	@Bean
	@ConditionalOnMissingBean(name = "connectHealthService")
	public BindableService connectHealthService(HealthStatusManager healthStatusManager) {
		return healthStatusManager.getHealthService();
	}

}
