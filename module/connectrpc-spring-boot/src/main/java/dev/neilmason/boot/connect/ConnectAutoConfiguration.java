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

import java.util.List;

import dev.neilmason.connect.ConnectFilter;
import dev.neilmason.connect.ConnectServiceRegistry;
import io.grpc.BindableService;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} for serving the Connect protocol from every {@link BindableService}
 * bean in the application context, on a reactive (WebFlux) web application.
 *
 * @author Neil Mason
 */
@AutoConfiguration
@EnableConfigurationProperties(ConnectProperties.class)
@ConditionalOnClass(BindableService.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "connect", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConnectAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ConnectServiceRegistry connectServiceRegistry(List<BindableService> services) {
		return new ConnectServiceRegistry(services);
	}

	@Bean
	@ConditionalOnMissingBean
	public ConnectFilter connectFilter(ConnectServiceRegistry registry, ConnectProperties properties) {
		return new ConnectFilter(registry, properties.getPathPrefix(), properties.getMaxMessageSize().toBytes(),
				properties.isCorsEnabled(), properties.getCorsAllowedOrigins());
	}

}
