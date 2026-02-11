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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolMeta;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for registering MCP Server to Nacos via AiMaintainerService. Ported from nacos-mcp-spring-boot-starter's
 * NacosMcpAutoRegistrar logic.
 *
 * @author higress
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NacosMcpService {

    private final AiMaintainerService aiMaintainerService;

    /**
     * Register MCP Server to Nacos.
     *
     * @param request registration request containing tools and server metadata
     * @return result message
     */
    public String registerMcpServer(McpRegisterRequest request) throws NacosException {
        // 1. Build McpToolSpecification from tool list
        McpToolSpecification toolSpec = new McpToolSpecification();
        List<McpTool> mcpTools = new ArrayList<>();
        Map<String, McpToolMeta> toolsMeta = new HashMap<>();

        for (McpRegisterRequest.ToolInfo tool : request.getTools()) {
            McpTool mcpTool = new McpTool();
            mcpTool.setName(tool.getName());
            mcpTool.setDescription(tool.getDescription());
            mcpTool.setInputSchema(buildInputSchema(tool));
            mcpTools.add(mcpTool);

            // Build tool meta with templates
            McpToolMeta meta = new McpToolMeta();
            meta.setEnabled(true);
            meta.setTemplates(buildTemplates(tool));
            toolsMeta.put(tool.getName(), meta);
        }

        toolSpec.setTools(mcpTools);
        toolSpec.setToolsMeta(toolsMeta);

        // 2. Build McpEndpointSpec (REF type - reference to Nacos registered service)
        McpEndpointSpec endpointSpec = new McpEndpointSpec();
        endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
        endpointSpec.getData().put("namespaceId", resolveNamespace(request.getNamespaceId()));
        endpointSpec.getData().put("serviceName", request.getServiceName());
        endpointSpec.getData().put("groupName", "DEFAULT_GROUP");
        endpointSpec.getData().put("transportProtocol", "http");

        // 3. Build McpServerBasicInfo
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName(request.getServerName());
        serverSpec.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        serverSpec.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        serverSpec.setDescription(request.getDescription());

        McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
        remoteConfig.setExportPath("/mcp");
        serverSpec.setRemoteServerConfig(remoteConfig);

        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion("1.0.0");
        serverSpec.setVersionDetail(versionDetail);

        // 4. Resolve namespace
        String namespaceId = resolveNamespace(request.getNamespaceId());

        log.info("Registering MCP Server '{}' to Nacos namespace '{}'", request.getServerName(), namespaceId);

        // 5. Try create, if exists then update
        try {
            String mcpId = aiMaintainerService.createMcpServer(namespaceId, request.getServerName(), serverSpec,
                toolSpec, endpointSpec);
            log.info("Successfully created MCP Server '{}', MCP ID: {}", request.getServerName(), mcpId);
            return "Created MCP Server successfully. ID: " + mcpId;
        } catch (NacosException e) {
            if (e.getErrCode() == 20005 || (e.getMessage() != null && e.getMessage().contains("existed"))) {
                log.info("MCP Server '{}' already exists, updating...", request.getServerName());
                boolean updated = aiMaintainerService.updateMcpServer(namespaceId, request.getServerName(), true,
                    serverSpec, toolSpec, endpointSpec);
                if (updated) {
                    log.info("Successfully updated MCP Server '{}'", request.getServerName());
                    return "Updated MCP Server successfully.";
                } else {
                    throw new NacosException(NacosException.SERVER_ERROR,
                        "Failed to update MCP Server: " + request.getServerName());
                }
            }
            throw e;
        }
    }

    private String resolveNamespace(String namespaceId) {
        if (namespaceId == null || namespaceId.isEmpty() || "public".equalsIgnoreCase(namespaceId)) {
            return Constants.DEFAULT_NAMESPACE_ID;
        }
        return namespaceId;
    }

    private Map<String, Object> buildInputSchema(McpRegisterRequest.ToolInfo tool) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        if (tool.getArgs() != null) {
            for (McpRegisterRequest.ArgInfo arg : tool.getArgs()) {
                Map<String, Object> propSchema = new HashMap<>();
                propSchema.put("type", arg.getType() != null ? arg.getType() : "string");
                if (arg.getDescription() != null && !arg.getDescription().isEmpty()) {
                    propSchema.put("description", arg.getDescription());
                }
                properties.put(arg.getName(), propSchema);
                if (arg.isRequired()) {
                    required.add(arg.getName());
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private Map<String, Object> buildTemplates(McpRegisterRequest.ToolInfo tool) {
        Map<String, Object> templates = new HashMap<>();
        Map<String, Object> jsonGoTemplate = new HashMap<>();

        // Request template
        Map<String, Object> requestTemplate = new HashMap<>();
        if (tool.getRequestTemplate() != null) {
            requestTemplate.put("url", tool.getRequestTemplate().getUrl());
            requestTemplate.put("method", tool.getRequestTemplate().getMethod());
            if (tool.getRequestTemplate().getHeaders() != null) {
                requestTemplate.put("headers", tool.getRequestTemplate().getHeaders());
            }
        }

        // Determine args position and body handling
        Map<String, String> argsPosition = new HashMap<>();
        boolean hasBodyParam = false;
        if (tool.getArgs() != null) {
            for (McpRegisterRequest.ArgInfo arg : tool.getArgs()) {
                if (arg.getPosition() != null) {
                    argsPosition.put(arg.getName(), arg.getPosition());
                    if ("body".equals(arg.getPosition())) {
                        hasBodyParam = true;
                    }
                }
            }
        }

        if (!hasBodyParam) {
            String method = tool.getRequestTemplate() != null ? tool.getRequestTemplate().getMethod() : "GET";
            if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                requestTemplate.put("argsToUrlParam", true);
            } else {
                requestTemplate.put("argsToJsonBody", true);
            }
        }

        jsonGoTemplate.put("requestTemplate", requestTemplate);
        if (!argsPosition.isEmpty()) {
            jsonGoTemplate.put("argsPosition", argsPosition);
        }

        // Response template
        Map<String, Object> responseTemplate = new HashMap<>();
        responseTemplate.put("prependBody", "");
        jsonGoTemplate.put("responseTemplate", responseTemplate);

        templates.put("json-go-template", jsonGoTemplate);
        return templates;
    }
}
