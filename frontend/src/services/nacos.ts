import request from './request';

export interface NacosConfig {
  dataId: string;
  group: string;
  groupName?: string;
  namespaceId: string;
  content: string;
  appName?: string;
  desc?: string;
  type?: string;
  md5?: string;
}

export interface NacosPage<T> {
  pageNumber: number;
  pagesAvailable: number;
  totalCount: number;
  pageItems: T[];
}

/**
 * Get Nacos Namespaces
 */
export const getNamespaces = (): Promise<any> => {
  return request.get('/nacos/namespaces');
};

/**
 * Get Nacos Configurations with Pagination
 */
export const getConfigs = (params: {
  dataId?: string;
  group?: string;
  namespaceId: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<NacosPage<NacosConfig>> => {
  return request.get('/nacos/configs', { params });
};

/**
 * Get Nacos Configuration Detail
 */
export const getConfigDetail = (params: {
  dataId: string;
  group?: string;
  namespaceId: string;
}): Promise<NacosConfig> => {
  return request.get('/nacos/config/detail', { params });
};

/**
 * Publish Nacos Configuration
 */
export const publishConfig = (payload: NacosConfig): Promise<any> => {
  return request.post('/nacos/config/publish', payload);
};
