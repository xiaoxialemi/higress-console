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
        // Placeholder implementation
        return ControllerUtil.buildResponseEntity("Nacos Maintainer Service is active");
    }

    @GetMapping("/configs")
    @Operation(summary = "Get Nacos Configurations with Pagination")
    public ResponseEntity<?> getConfigs(
            @RequestParam(required = false, defaultValue = "") String dataId,
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
    public ResponseEntity<?> getConfigDetail(
            @RequestParam(required = true) String dataId,
            @RequestParam(required = false, defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(required = true) String namespaceId) {
        try {
            var detail = configMaintainerService.getConfig(dataId, group, namespaceId);
            return ControllerUtil.buildResponseEntity(detail);
        } catch (Exception e) {
            log.error("Failed to get Nacos config detail: ", e);
            return ControllerUtil.buildFalureResponseEntity("Failed to fetch Nacos configuration detail: " + e.getMessage());
        }
    }

    @PostMapping("/config/publish")
    @Operation(summary = "Publish Nacos Configuration (Create or Update)")
    public ResponseEntity<?> publishConfig(@RequestBody PublishRequest request) {
        try {
            boolean success = configMaintainerService.publishConfig(
                    request.getDataId(),
                    request.getGroup(),
                    request.getNamespaceId(),
                    request.getContent(),
                    request.getAppName(),
                    null, // srcUser
                    null, // configTags
                    request.getDesc(),
                    request.getType()
            );
            return success ? ControllerUtil.buildResponseEntity("Published successfully") : ControllerUtil.buildFalureResponseEntity("Publish failed");
        } catch (Exception e) {
            log.error("Failed to publish Nacos config: ", e);
            return ControllerUtil.buildFalureResponseEntity("Failed to publish Nacos configuration: " + e.getMessage());
        }
    }

    @Data
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
