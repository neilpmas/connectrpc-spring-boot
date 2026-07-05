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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("connect")
public class ConnectProperties {

	private boolean enabled = true;

	private String pathPrefix = "/connect";

	private DataSize maxMessageSize = DataSize.ofMegabytes(4);

	private boolean corsEnabled = true;

	private List<String> corsAllowedOrigins = List.of("*");

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getPathPrefix() {
		return this.pathPrefix;
	}

	public void setPathPrefix(String pathPrefix) {
		this.pathPrefix = pathPrefix;
	}

	public DataSize getMaxMessageSize() {
		return this.maxMessageSize;
	}

	public void setMaxMessageSize(DataSize maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}

	public boolean isCorsEnabled() {
		return this.corsEnabled;
	}

	public void setCorsEnabled(boolean corsEnabled) {
		this.corsEnabled = corsEnabled;
	}

	public List<String> getCorsAllowedOrigins() {
		return this.corsAllowedOrigins;
	}

	public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
		this.corsAllowedOrigins = corsAllowedOrigins;
	}

}
