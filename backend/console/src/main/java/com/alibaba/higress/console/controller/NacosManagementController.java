package com.alibaba.higress.console.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.alibaba.higress.console.controller.util.ControllerUtil;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.var;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/nacos")
@RequiredArgsConstructor
@Tag(name = "Nacos Management", description = "Nacos Management APIs via Maintainer SDK")
public class NacosManagementController {

    private final ConfigMaintainerService configMaintainerService;
    private final NamingMaintainerService namingMaintainerService;

    @GetMapping("/namespaces")
    @Operation(summary = "Get Nacos Namespaces")
    public ResponseEntity<?> getNamespaces() {
        try {
            // Maintainer SDK might not have a direct listNamespaces in some versions,
            // but we can try to fetch from naming service or fall back to a reasonable list.
            var namespaces = namingMaintainerService.getNamespaceList();
            return ControllerUtil.buildResponseEntity(namespaces);
        } catch (Exception e) {
            log.error("Failed to fetch Nacos namespaces: ", e);
            // Fallback to minimal list if fails
            return ControllerUtil
                .buildResponseEntity(java.util.Collections.singletonList(new Namespace("public", "public")));
        }
    }

    @Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class Namespace {
        private String namespace;
        private String namespaceShowName;
    }

    @GetMapping("/configs")
    @Operation(summary = "Get Nacos Configurations with Pagination")
    public ResponseEntity<?> getConfigs(@RequestParam(required = false, defaultValue = "") String dataId,
        @RequestParam(required = false, defaultValue = "") String group,
        @RequestParam(required = true) String namespaceId,
        @RequestParam(required = false, defaultValue = "1") int pageNo,
        @RequestParam(required = false, defaultValue = "10") int pageSize) {
        try {
            // listConfigs(dataId, group, namespaceId, type, configTags, appName, pageNo, pageSize)
            var page = configMaintainerService.listConfigs(dataId, group, namespaceId, "", "", "", pageNo, pageSize);
            return ControllerUtil.buildResponseEntity(page);
        } catch (Exception e) {
            log.error("Failed to list Nacos configs: ", e);
            return ControllerUtil.buildFalureResponseEntity("Failed to fetch Nacos configurations: " + e.getMessage());
        }
    }

    @GetMapping("/config/detail")
    @Operation(summary = "Get Nacos Configuration Detail")
    public ResponseEntity<?> getConfigDetail(@RequestParam(required = true) String dataId,
        @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
        @RequestParam(required = true) String namespaceId) {
        try {
            var detail = configMaintainerService.getConfig(dataId, group, namespaceId);
            return ControllerUtil.buildResponseEntity(detail);
        } catch (Exception e) {
            log.error("Failed to get Nacos config detail: ", e);
            return ControllerUtil
                .buildFalureResponseEntity("Failed to fetch Nacos configuration detail: " + e.getMessage());
        }
    }

    @PostMapping("/config/publish")
    @Operation(summary = "Publish Nacos Configuration (Create or Update)")
    public ResponseEntity<?> publishConfig(@RequestBody PublishRequest request) {
        log.info("Request to publish Nacos config: dataId={}, group={}, namespaceId={}, type={}", request.getDataId(),
            request.getGroup(), request.getNamespaceId(), request.getType());

        if (request.getDataId() == null || request.getNamespaceId() == null || request.getContent() == null) {
            return ControllerUtil.buildFalureResponseEntity("Data ID, Namespace ID and Content are required");
        }

        try {
            boolean success = configMaintainerService.publishConfig(request.getDataId(), request.getGroup(),
                request.getNamespaceId(), request.getContent(), request.getAppName(), null, // srcUser
                null, // configTags
                request.getDesc(), request.getType());

            if (success) {
                log.info("Published Nacos config successfully: dataId={}", request.getDataId());
                return ControllerUtil.buildResponseEntity("Published successfully");
            } else {
                log.error("Failed to publish Nacos config via Maintainer Service: dataId={}", request.getDataId());
                return ControllerUtil.buildFalureResponseEntity("Publish failed");
            }
        } catch (Exception e) {
            log.error("Exception during Nacos config publish: ", e);
            return ControllerUtil.buildFalureResponseEntity("Failed to publish Nacos configuration: " + e.getMessage());
        }
    }

    @Data
    @lombok.NoArgsConstructor
    public static class PublishRequest {
        private String dataId;
        private String group;
        private String namespaceId;
        private String content;
        private String appName;
        private String desc;
        private String type;
    }
}
