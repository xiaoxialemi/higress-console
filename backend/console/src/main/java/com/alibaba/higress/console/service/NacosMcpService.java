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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
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
        String namespaceId = resolveNamespace(request.getNamespaceId());
        String serverName = request.getServerName();

        // 1. Check if MCP server already exists to enable incremental update (merge)
        McpServerDetailInfo existingServer = null;
        try {
            existingServer = aiMaintainerService.getMcpServerDetail(namespaceId, serverName, null, null);
        } catch (NacosException e) {
            // If not found, it's fine, we'll create it.
            // Nacos SDK might throw exception if server doesn't exist depending on version/impl.
            log.debug("MCP Server '{}' not found in Nacos, will create new.", serverName);
        }

        // 2. Prepare Tool Spec (Merged if existing)
        McpToolSpecification toolSpec = new McpToolSpecification();
        List<McpTool> mergedTools = new ArrayList<>();
        Map<String, McpToolMeta> mergedToolsMeta = new HashMap<>();

        // If exists, load existing tools into the merge pool
        if (existingServer != null && existingServer.getToolSpec() != null) {
            McpToolSpecification existingSpec = existingServer.getToolSpec();
            if (existingSpec.getTools() != null) {
                mergedTools.addAll(existingSpec.getTools());
            }
            if (existingSpec.getToolsMeta() != null) {
                mergedToolsMeta.putAll(existingSpec.getToolsMeta());
            }
        }

        // Add/Overwrite with tools from the current request
        for (McpRegisterRequest.ToolInfo toolReq : request.getTools()) {
            McpTool mcpTool = new McpTool();
            mcpTool.setName(toolReq.getName());
            mcpTool.setDescription(toolReq.getDescription());
            mcpTool.setInputSchema(buildInputSchema(toolReq));

            // Merge by name: remove existing one if present to replace with new definition
            mergedTools.removeIf(t -> t.getName().equals(toolReq.getName()));
            mergedTools.add(mcpTool);

            // Build/Overwrite tool meta
            McpToolMeta meta = new McpToolMeta();
            meta.setEnabled(true);
            meta.setTemplates(buildTemplates(toolReq));
            mergedToolsMeta.put(toolReq.getName(), meta);
        }

        toolSpec.setTools(mergedTools);
        toolSpec.setToolsMeta(mergedToolsMeta);

        // 3. Build Endpoint and Server Basic Info
        McpEndpointSpec endpointSpec = new McpEndpointSpec();
        endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
        endpointSpec.getData().put("namespaceId", namespaceId);
        endpointSpec.getData().put("serviceName", request.getServiceName());
        endpointSpec.getData().put("groupName",
            (request.getGroup() != null && !request.getGroup().isEmpty()) ? request.getGroup() : "DEFAULT_GROUP");
        endpointSpec.getData().put("transportProtocol", "http");

        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName(serverName);
        serverSpec.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_HTTP);
        serverSpec.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        serverSpec.setDescription(request.getDescription());

        McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
        remoteConfig.setExportPath("/mcp");
        serverSpec.setRemoteServerConfig(remoteConfig);

        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion("1.0.0");
        serverSpec.setVersionDetail(versionDetail);

        // 4. Upsert (Create or Update)
        if (existingServer != null) {
            log.info("Updating existing MCP Server '{}' with merged tools (count: {})", serverName, mergedTools.size());
            boolean updated =
                aiMaintainerService.updateMcpServer(namespaceId, serverName, true, serverSpec, toolSpec, endpointSpec);
            if (updated) {
                return "Updated MCP Server successfully with merged tools.";
            } else {
                throw new NacosException(NacosException.SERVER_ERROR, "Failed to update MCP Server: " + serverName);
            }
        } else {
            log.info("Creating new MCP Server '{}' with tools (count: {})", serverName, mergedTools.size());
            String mcpId =
                aiMaintainerService.createMcpServer(namespaceId, serverName, serverSpec, toolSpec, endpointSpec);
            return "Created MCP Server successfully. ID: " + mcpId;
        }
    }

    /**
     * Get list of registered tool names for a given MCP server in Nacos.
     *
     * @param namespaceId Nacos namespace ID
     * @param serverName MCP server name
     * @return list of tool names
     */
    public List<String> getRegisteredToolNames(String namespaceId, String serverName) throws NacosException {
        McpServerDetailInfo mcpServer =
            aiMaintainerService.getMcpServerDetail(resolveNamespace(namespaceId), serverName, null, null);
        if (mcpServer == null) {
            return Collections.emptyList();
        }

        McpToolSpecification toolSpec = mcpServer.getToolSpec();
        if (toolSpec != null && toolSpec.getTools() != null) {
            List<String> names = new ArrayList<>();
            for (McpTool tool : toolSpec.getTools()) {
                names.add(tool.getName());
            }
            return names;
        }
        return Collections.emptyList();
    }

    /**
     * Unregister a specific tool from MCP Server in Nacos.
     *
     * @param namespaceId Nacos namespace ID
     * @param serverName MCP server name
     * @param toolName name of the tool to remove
     * @return true if successfully unregistered
     */
    public boolean unregisterMcpTool(String namespaceId, String serverName, String toolName) throws NacosException {
        String nsId = resolveNamespace(namespaceId);
        McpServerDetailInfo mcpServer = aiMaintainerService.getMcpServerDetail(nsId, serverName, null, null);
        if (mcpServer == null || mcpServer.getToolSpec() == null) {
            log.warn("Cannot unregister tool {}: MCP Server {} not found in Nacos", toolName, serverName);
            return false;
        }

        McpToolSpecification toolSpec = mcpServer.getToolSpec();
        List<McpTool> tools = toolSpec.getTools();
        Map<String, McpToolMeta> toolsMeta = toolSpec.getToolsMeta();

        if (tools == null || tools.isEmpty()) {
            return true; // Already empty
        }

        boolean removed = tools.removeIf(t -> t.getName().equals(toolName));
        if (toolsMeta != null) {
            toolsMeta.remove(toolName);
        }

        if (removed) {
            log.info("Unregistering tool {} from MCP Server {} in Nacos", toolName, serverName);
            McpServerBasicInfo basicInfo = resolveBasicInfo(mcpServer, serverName);
            McpEndpointSpec endpointSpec = resolveEndpointSpec(mcpServer);
            return aiMaintainerService.updateMcpServer(nsId, serverName, true, basicInfo, toolSpec, endpointSpec);
        }

        return true; // Tool not found, consider success
    }

    private McpEndpointSpec resolveEndpointSpec(McpServerDetailInfo mcpServer) {
        McpEndpointSpec endpointSpec = new McpEndpointSpec();
        McpServerRemoteServiceConfig remoteConfig = mcpServer.getRemoteServerConfig();
        if (remoteConfig != null && remoteConfig.getServiceRef() != null) {
            McpServiceRef ref = remoteConfig.getServiceRef();
            endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
            endpointSpec.getData().put("namespaceId", ref.getNamespaceId());
            endpointSpec.getData().put("serviceName", ref.getServiceName());
            endpointSpec.getData().put("groupName", ref.getGroupName());
            endpointSpec.getData().put("transportProtocol", ref.getTransportProtocol());
        } else {
            // Fallback for direct endpoints if needed, but currently Higress uses REF
            endpointSpec.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
            if (mcpServer.getBackendEndpoints() != null && !mcpServer.getBackendEndpoints().isEmpty()) {
                McpEndpointInfo first = mcpServer.getBackendEndpoints().get(0);
                endpointSpec.getData().put("address", first.getAddress());
                endpointSpec.getData().put("port", String.valueOf(first.getPort()));
                endpointSpec.getData().put("protocol", first.getProtocol());
            }
        }
        return endpointSpec;
    }

    private McpServerBasicInfo resolveBasicInfo(McpServerDetailInfo detail, String serverName) {
        McpServerBasicInfo basicInfo = new McpServerBasicInfo();
        basicInfo.setName(serverName);
        basicInfo.setProtocol(detail.getProtocol());
        basicInfo.setFrontProtocol(detail.getFrontProtocol());
        basicInfo.setDescription(detail.getDescription());
        basicInfo.setRemoteServerConfig(detail.getRemoteServerConfig());
        basicInfo.setVersionDetail(detail.getVersionDetail());
        return basicInfo;
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
