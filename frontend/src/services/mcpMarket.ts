import request from '@/services/request';
import {
  McpServer,
  McpServerPageQuery,
  McpServerConsumers,
  McpServerConsumerDetail,
} from '@/interfaces/mcp';

const BASE_URL = '/v1/mcpMarket';

export const listMcpServers = (query: McpServerPageQuery): Promise<McpServer[]> => {
  return request.get<any, McpServer[]>(BASE_URL, { params: query });
};

export const getMcpServer = (name: string): Promise<McpServer> => {
  return request.get<any, McpServer>(`${BASE_URL}/${name}`);
};

export const createOrUpdateMcpServer = (payload: McpServer): Promise<McpServer> => {
  return payload.name ?
    request.put<any, McpServer>(BASE_URL, payload) :
    request.post<any, McpServer>(BASE_URL, payload);
};

export const deleteMcpServer = (name: string): Promise<any> => {
  return request.delete<any, any>(`${BASE_URL}/${name}`);
};

export const addMcpConsumers = (payload: { consumers: undefined[]; mcpServerName: string }): Promise<any> => {
  return request.put<any, any>(`${BASE_URL}/consumers`, payload);
};

export const removeMcpConsumers = (payload: McpServerConsumers): Promise<any> => {
  return request.delete<any, any>(`${BASE_URL}/consumers`, { data: payload });
};

export const listMcpConsumers = (
  query: any,
): Promise<McpServerConsumerDetail[]> => {
  return request.get<any, McpServerConsumerDetail[]>(`${BASE_URL}/consumers`, {
    params: query,
  });
};

export const swaggerToMcpConfig = (payload: { content: string }): Promise<any> => {
  return request.post<any, any>(`${BASE_URL}/swaggerToMcpConfig`, payload);
};

export const registerToNacos = (payload: any): Promise<any> => {
  return request.post<any, any>(`${BASE_URL}/registerToNacos`, payload);
};

export const getNacosRegisteredTools = (namespaceId: string, serverName: string): Promise<string[]> => {
  return request.get<any, string[]>(`${BASE_URL}/nacos/tools`, { params: { namespaceId, serverName } });
};

export const unregisterNacosTool = (namespaceId: string, serverName: string, toolName: string): Promise<any> => {
  return request.delete<any, any>(`${BASE_URL}/nacos/tools`, { params: { namespaceId, serverName, toolName } });
};
