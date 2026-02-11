/*
 * Copyright (c) 2022-2024 Alibaba Group Holding Ltd.
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
package com.alibaba.higress.sdk.service.mcp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.alibaba.higress.sdk.constant.McpConstants;
import com.alibaba.higress.sdk.exception.BusinessException;
import com.google.common.io.ByteStreams;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for converting OpenAPI/Swagger to MCP configuration.
 * This class directly calls the Go binary for better reliability and performance.
 */
@Slf4j
public class McpConverter {

    public static String swaggerToMcpConfig(String swaggerContent) {
        try {
            long timeMillis = System.currentTimeMillis();

            // Write temporary OpenAPI JSON file
            String openaiFileName = "openapi-" + timeMillis + ".json";
            Path openapiFilePath = Paths.get(McpConstants.getMcpTempDir(), openaiFileName);
            Files.write(openapiFilePath, swaggerContent.getBytes(StandardCharsets.UTF_8));

            // Prepare output file path
            String mcpConfigFileName = "mcp-config-" + timeMillis + ".yaml";
            File tempMcpConfigFile = Paths.get(McpConstants.getMcpTempDir(), mcpConfigFileName).toFile();
            tempMcpConfigFile.createNewFile();

            // Construct direct command to the main binary
            String workDir = McpConstants.getMcpWorkDir();
            String mainBinaryPath = Paths.get(workDir, "main").toString();

            String[] args = new String[5];
            args[0] = mainBinaryPath;
            args[1] = "--input";
            args[2] = openapiFilePath.toString();
            args[3] = "--output";
            args[4] = tempMcpConfigFile.getPath();

            log.info("Executing direct OpenAPI to MCP conversion. Command: {} {} {} {} {}",
                args[0], args[1], args[2], args[3], args[4]);

            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(new File(workDir)); // Set working directory
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read process output to prevent blocking and capture errors
            String processOutput = new String(ByteStreams.toByteArray(process.getInputStream()), StandardCharsets.UTF_8);

            int exitCode = process.waitFor();


            if (exitCode != 0) {
                log.error("OpenAPI to MCP config conversion failed with exit code: {}, output: {}",
                    exitCode, processOutput);
                throw new BusinessException(
                    "Conversion failed (exit code " + exitCode + "): " + processOutput);
            }

            if (!processOutput.isEmpty()) {
                log.info("Process output: {}", processOutput);
            }

            // Read the generated mcp-config YAML file
            Path mcpConfigFilePath = Paths.get(McpConstants.getMcpTempDir(), mcpConfigFileName);
            String mcpConfigContent = new String(Files.readAllBytes(mcpConfigFilePath), StandardCharsets.UTF_8);

            if (mcpConfigContent.isEmpty()) {
                log.warn("MCP config output file is empty. Process output: {}", processOutput);
            }

            // Clean up temporary files
            Files.deleteIfExists(openapiFilePath);
            Files.deleteIfExists(mcpConfigFilePath);

            return mcpConfigContent;

        } catch (Throwable e) {
            log.error("Error occurs when converting swagger to mcp config", e);
            throw new BusinessException(e.getMessage(), e);
        }
    }
}
