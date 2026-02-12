/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.higress.console.service;

import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for registering MCP Server to Nacos.
 *
 * @author higress
 */
@Data
@NoArgsConstructor
public class McpRegisterRequest {

    /**
     * MCP server name to register.
     */
    private String serverName;

    /**
     * Server description.
     */
    private String description;

    /**
     * Nacos namespace ID where to register.
     */
    private String namespaceId;

    /**
     * Service name for endpoint reference.
     */
    private String serviceName;

    /**
     * Nacos group name.
     */
    private String group;

    /**
     * List of tools to register.
     */
    private List<ToolInfo> tools;

    @Data
    @NoArgsConstructor
    public static class ToolInfo {

        private String name;
        private String description;
        private List<ArgInfo> args;
        private RequestTemplateInfo requestTemplate;
    }

    @Data
    @NoArgsConstructor
    public static class ArgInfo {

        private String name;
        private String description;
        private String type;
        private boolean required;
        private String position;
    }

    @Data
    @NoArgsConstructor
    public static class RequestTemplateInfo {

        private String url;
        private String method;
        private List<Map<String, String>> headers;
    }
}
