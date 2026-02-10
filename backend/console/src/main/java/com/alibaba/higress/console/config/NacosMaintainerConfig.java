package com.alibaba.higress.console.config;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerFactory;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerFactory;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerService;

@Configuration
public class NacosMaintainerConfig {

    @Value("${spring.cloud.nacos.discovery.server-addr}")
    private String serverAddr;

    @Value("${spring.cloud.nacos.discovery.username}")
    private String username;

    @Value("${spring.cloud.nacos.discovery.password}")
    private String password;

    @Bean
    public ConfigMaintainerService configMaintainerService() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("username", username);
        properties.setProperty("password", password);
        return ConfigMaintainerFactory.createConfigMaintainerService(properties);
    }

    @Bean
    public NamingMaintainerService namingMaintainerService() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("username", username);
        properties.setProperty("password", password);
        return NamingMaintainerFactory.createNamingMaintainerService(properties);
    }
}
