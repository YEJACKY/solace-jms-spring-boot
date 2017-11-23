/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.solace.labs.spring.boot.autoconfigure;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.jms.ConnectionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jms.JmsAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.ServiceInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.solace.labs.spring.cloud.core.SolaceMessagingInfo;
import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolConnectionFactoryImpl;
import com.solacesystems.jms.SpringSolConnectionFactoryCloudFactory;
import com.solacesystems.jms.property.JMSProperties;

@Configuration
@AutoConfigureBefore(JmsAutoConfiguration.class)
@ConditionalOnClass({ ConnectionFactory.class, SolConnectionFactory.class, CloudFactory.class })
@ConditionalOnMissingBean(ConnectionFactory.class)
@Conditional(CloudCondition.class)
@EnableConfigurationProperties(SolaceJmsProperties.class)
public class SolaceJmsAutoCloudConfiguration implements SpringSolConnectionFactoryCloudFactory {

	private static final Logger logger = LoggerFactory.getLogger(SolaceJmsAutoCloudConfiguration.class);

	private CloudFactory cloudFactory = new CloudFactory();

	@Autowired
	private SolaceJmsProperties properties;

	@Bean
	public List<SolaceMessagingInfo> getSolaceMessagingInfos() {
		List<SolaceMessagingInfo> solaceMessagingInfoList = new ArrayList<>();
		Cloud cloud = cloudFactory.getCloud();

		List<ServiceInfo> serviceInfos = cloud.getServiceInfos();
		for (ServiceInfo serviceInfo : serviceInfos) {
			if (serviceInfo instanceof SolaceMessagingInfo) {
				solaceMessagingInfoList.add((SolaceMessagingInfo) serviceInfo);
			}
		}
		return solaceMessagingInfoList;
	}

	@Bean
	public SolaceMessagingInfo findFirstSolaceMessagingInfo() {
		SolaceMessagingInfo solacemessaging = null;
		Cloud cloud = cloudFactory.getCloud();
		List<ServiceInfo> serviceInfos = cloud.getServiceInfos();
		for (ServiceInfo serviceInfo : serviceInfos) {
			// Stop when we find the first one...
			// TODO: Consider annotation driven selection, or sorted plan based
			// selection
			if (serviceInfo instanceof SolaceMessagingInfo) {
				solacemessaging = (SolaceMessagingInfo) serviceInfo;
				logger.info("Found Cloud Solace Messaging Service Instance Id: " + solacemessaging.getId());
				break;
			}
		}

		if (solacemessaging == null) {
			// The CloudCondition should shield from this happening, should not
			// arrive to this state.
			logger.error("Cloud Solace Messaging Info was not found, cannot auto-configure");
			throw new IllegalStateException(
					"Unable to create SolConnectionFactory did not find SolaceMessagingInfo in the current cloud environment");
		}

		return solacemessaging;
	}

	public SolConnectionFactory getSolConnectionFactory(SolaceMessagingInfo solacemessaging) {
		try {
			// TODO: Consider injecting other environment provided properties
			JMSProperties props = new JMSProperties((Hashtable<?, ?>) null);
			props.initialize();
			SolConnectionFactoryImpl cf = new SolConnectionFactoryImpl(props);

			// Use provided cloud information
			cf.setHost(solacemessaging.getSmfHost());
			cf.setVPN(solacemessaging.getMsgVpnName());

			if (solacemessaging.getClientUsername() != null)
				cf.setUsername(solacemessaging.getClientUsername());
			else
				cf.setUsername(properties.getClientUsername());

			if (solacemessaging.getClientPassword() != null)
				cf.setPassword(solacemessaging.getClientPassword());
			else
				cf.setPassword(properties.getClientPassword());

			// TODO: Should probably drive this from new Solace Annotations to
			// guide configuration preferences
			cf.setDirectTransport(false);

			return cf;
		} catch (Exception ex) {
			logger.error("Exception found during Solace Connection Factory creation.", ex);
			throw new IllegalStateException("Unable to create Solace connection factory", ex);
		}
	}

	@Bean
	public SolConnectionFactory getSolConnectionFactory() {
		return getSolConnectionFactory(findFirstSolaceMessagingInfo());
	}

}