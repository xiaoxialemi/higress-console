/* eslint-disable max-lines */
import { ClusterOutlined, RedoOutlined } from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-layout';
import { useRequest } from 'ahooks';
import { Button, Card, Col, Form, Input, message, Modal, Row, Select, Space, Table, Tag, Tabs } from 'antd';
import React, { useEffect, useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { getConfigs, getNamespaces, publishConfig, getConfigDetail, NacosConfig } from '@/services/nacos';
import CodeEditor from '@/components/CodeEditor';

const { Option } = Select;
const { TabPane } = Tabs;

interface ApiField {
  name: string;
  type: string;
  required: boolean;
  description?: string;
}

interface ApiParameter {
  name: string;
  parameterType: string;
  type: string;
  required: boolean;
  description?: string;
  fields?: ApiField[];
}

interface ApiRegistryItem {
  httpMethod: string;
  path: string;
  summary?: string;
  description?: string;
  parameters?: ApiParameter[];
  [key: string]: any;
}

interface ConfigMetadata {
  apiCount?: number;
  serviceName?: string;
  sourceNamespace?: string;
  collectedAt?: number;
}

const NacosList: React.FC = () => {
  const { t } = useTranslation();
  const [form] = Form.useForm();
  const [dataSource, setDataSource] = useState<NacosConfig[]>([]);
  const [namespaces, setNamespaces] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [current, setCurrent] = useState(1);
  const [selectedNamespace, setSelectedNamespace] = useState<string>('platform-hub');

  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editingConfig, setEditingConfig] = useState<NacosConfig | null>(null);
  const [editForm] = Form.useForm();
  const [activeTab, setActiveTab] = useState('raw');
  const [apiData, setApiData] = useState<ApiRegistryItem[]>([]);
  const [filterText, setFilterText] = useState<string>('');
  const [publishLoading, setPublishLoading] = useState(false);
  const [configMetadata, setConfigMetadata] = useState<ConfigMetadata>({});
  const watchedContent = Form.useWatch('content', editForm);
  const watchedType = Form.useWatch('type', editForm);


  const fetchNamespaces = async () => {
    try {
      const res = await getNamespaces();
      if (res && res.data && res.data.length > 0) {
        setNamespaces(res.data);
      } else {
        // Fallback to default if API returns empty
        setNamespaces([{ namespace: 'platform-hub', namespaceShowName: 'platform-hub' }, { namespace: 'public', namespaceShowName: 'public' }]);
      }
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Failed to fetch namespaces', error);
      setNamespaces([{ namespace: 'platform-hub', namespaceShowName: 'platform-hub' }, { namespace: 'public', namespaceShowName: 'public' }]);
    }
  };

  const fetchData = async (page = 1, size = 10, ns = selectedNamespace) => {
    setLoading(true);
    try {
      const values = form.getFieldsValue();
      const res = await getConfigs({
        namespaceId: ns,
        pageNo: page,
        pageSize: size,
        dataId: values.dataId,
        group: values.group,
      });
      if (res) {
        setDataSource(res.pageItems || []);
        setTotal(res.totalCount || 0);
      }
    } catch (error) {
      // message.error(t('request.error.500'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNamespaces();
    fetchData();
  }, []);

  const onSearch = () => {
    setCurrent(1);
    fetchData(1, pageSize);
  };

  const onReset = () => {
    form.resetFields();
    onSearch();
  };

  const handlePageChange = (page: number, size: number) => {
    setCurrent(page);
    setPageSize(size);
    fetchData(page, size);
  };

  const handleNamespaceChange = (value: string) => {
    setSelectedNamespace(value);
    setCurrent(1);
    fetchData(1, pageSize, value);
  };

  const parseApiData = (content: string): { apis: ApiRegistryItem[]; metadata: ConfigMetadata } => {
    try {
      const parsed = JSON.parse(content);
      const metadata: ConfigMetadata = {
        apiCount: parsed.apiCount,
        serviceName: parsed.serviceName,
        sourceNamespace: parsed.sourceNamespace,
        collectedAt: parsed.collectedAt,
      };

      if (Array.isArray(parsed)) {
        return { apis: parsed, metadata: {} };
      }
      if (parsed.apis && Array.isArray(parsed.apis)) {
        return { apis: parsed.apis, metadata };
      }
      return { apis: [], metadata: {} };
    } catch (e) {
      return { apis: [], metadata: {} };
    }
  };

  const onEdit = async (record: NacosConfig) => {
    setLoading(true);
    try {
      const detail = await getConfigDetail({
        dataId: record.dataId,
        group: (record.group || record.groupName || '') as string,
        namespaceId: selectedNamespace,
      });

      const editRecord: NacosConfig = {
        ...record,
        ...detail,
        group: (detail.group || detail.groupName || record.group || record.groupName || '') as string,
      };

      setEditingConfig(editRecord);
      editForm.resetFields();
      editForm.setFieldsValue(editRecord);

      const { apis, metadata } = parseApiData(detail.content || record.content || '');
      setApiData(apis);
      setConfigMetadata(metadata);
      setFilterText('');
      setActiveTab(apis.length > 0 ? 'api' : 'raw');

      setEditModalVisible(true);
    } catch (error) {
      message.error(t('request.error.500'));
    } finally {
      setLoading(false);
    }
  };

  const handlePublish = async () => {
    try {
      setPublishLoading(true);
      const values = await editForm.validateFields();

      if (!editingConfig) return;

      const allFields = editForm.getFieldsValue();
      const payload: NacosConfig = {
        dataId: editingConfig.dataId,
        group: editingConfig.group || editingConfig.groupName || 'DEFAULT_GROUP',
        namespaceId: selectedNamespace,
        content: values.content || allFields.content,
        appName: configMetadata.serviceName || editingConfig.appName || '',
        desc: editingConfig.desc || '',
        type: editingConfig.type || 'json',
      };

      await publishConfig(payload);

      message.success(t('nacos.publishSuccess'));
      setEditModalVisible(false);
      fetchData(current, pageSize);
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('Publish failed', error);
    } finally {
      setPublishLoading(false);
    }
  };

  const updateContentFromApiData = (newData: ApiRegistryItem[]) => {
    try {
      const currentContent = editForm.getFieldValue('content');
      if (!currentContent) {
        editForm.setFieldsValue({ content: JSON.stringify(newData, null, 2) });
        return;
      }
      const parsed = JSON.parse(currentContent);
      if (Array.isArray(parsed)) {
        editForm.setFieldsValue({ content: JSON.stringify(newData, null, 2) });
      } else if (parsed.apis && Array.isArray(parsed.apis)) {
        parsed.apis = newData;
        editForm.setFieldsValue({ content: JSON.stringify(parsed, null, 2) });
      }
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error('Failed to update content from API data', e);
    }
  };

  const handleApiDescriptionChange = (index: number, field: string, value: string) => {
    const newData = [...apiData];
    newData[index] = { ...newData[index], [field]: value };
    setApiData(newData);
    updateContentFromApiData(newData);
  };

  const handleParameterDescriptionChange = (apiIndex: number, paramIndex: number, value: string) => {
    const newData = [...apiData];
    const api = { ...newData[apiIndex] };
    const params = [...(api.parameters || [])];
    params[paramIndex] = { ...params[paramIndex], description: value };
    api.parameters = params;
    newData[apiIndex] = api;
    setApiData(newData);
    updateContentFromApiData(newData);
  };

  const handleEntityFieldDescriptionChange = (apiIndex: number, paramIndex: number, fieldIndex: number, value: string) => {
    const newData = [...apiData];
    const api = { ...newData[apiIndex] };
    const params = [...(api.parameters || [])];
    const param = { ...params[paramIndex] };
    const fieldsList = [...(param.fields || [])];
    fieldsList[fieldIndex] = { ...fieldsList[fieldIndex], description: value };
    param.fields = fieldsList;
    params[paramIndex] = param;
    api.parameters = params;
    newData[apiIndex] = api;
    setApiData(newData);
    updateContentFromApiData(newData);
  };

  const apiColumns = [
    {
      title: t('nacos.apiRegistry.columns.method'),
      dataIndex: 'httpMethod',
      key: 'httpMethod',
      width: 100,
      render: (val: string) => {
        const method = (val || '').toUpperCase();
        let color = 'blue';
        if (method === 'GET') color = 'green';
        if (method === 'POST') color = 'orange';
        if (method === 'DELETE') color = 'red';
        if (method === 'PUT') color = 'cyan';
        return <Tag color={color}>{method || 'UNKNOWN'}</Tag>;
      },
    },
    {
      title: t('nacos.apiRegistry.columns.path'),
      dataIndex: 'path',
      key: 'path',
      ellipsis: true,
    },
    {
      title: t('nacos.apiRegistry.columns.summary'),
      dataIndex: 'summary',
      key: 'summary',
      render: (val: string, record: ApiRegistryItem, index: number) => (
        <Input
          value={val}
          onChange={(e) => handleApiDescriptionChange(index, 'summary', e.target.value)}
          placeholder={t('nacos.apiRegistry.editDescription') as string}
        />
      ),
    },
    {
      title: t('nacos.apiRegistry.columns.description') as string,
      dataIndex: 'description',
      key: 'description',
      render: (val: string, record: ApiRegistryItem, index: number) => (
        <Input
          value={val}
          onChange={(e) => handleApiDescriptionChange(index, 'description', e.target.value)}
          placeholder={t('nacos.apiRegistry.editDescription') as string}
        />
      ),
    },
  ];

  const columns = [
    {
      title: t('nacos.columns.dataId') as string,
      dataIndex: 'dataId',
      key: 'dataId',
      ellipsis: true,
    },
    {
      title: t('nacos.columns.group') as string,
      dataIndex: 'group',
      key: 'group',
      render: (val: string, record: NacosConfig) => val || record.groupName || '-',
    },
    //     {
    //       title: t('nacos.columns.appName') as string,
    //       dataIndex: 'appName',
    //       key: 'appName',
    //       render: (val: string) => val || '-',
    //     },
    {
      title: t('nacos.columns.type') as string,
      dataIndex: 'type',
      key: 'type',
      render: (val: string) => <Tag color="blue">{val?.toUpperCase() || 'TEXT'}</Tag>,
    },
    {
      title: t('nacos.columns.action') as string,
      key: 'action',
      width: 120,
      render: (_: any, record: NacosConfig) => (
        <a onClick={() => onEdit(record)}>{t('misc.detail')}</a>
      ),
    },
  ];

  const filteredApiData = useMemo(() => {
    if (!filterText) return apiData;
    const lowerFilter = filterText.toLowerCase();
    return apiData.filter(item => {
      return (
        (item.path && item.path.toLowerCase().includes(lowerFilter)) ||
        (item.summary && item.summary.toLowerCase().includes(lowerFilter)) ||
        (item.description && item.description.toLowerCase().includes(lowerFilter)) ||
        (item.httpMethod && item.httpMethod.toLowerCase().includes(lowerFilter))
      );
    });
  }, [apiData, filterText]);

  return (
    <PageContainer title={t('nacos.title') as string}>
      <Card style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" onFinish={onSearch} initialValues={{ namespace: 'platform-hub' }}>
          <Form.Item label={t('nacos.columns.namespace') as string}>
            <Select
              value={selectedNamespace}
              onChange={handleNamespaceChange}
              style={{ width: 220 }}
              placeholder={t('nacos.namespacePlaceholder') as string}
            >
              {namespaces.map((ns) => (
                <Option key={ns.namespace} value={ns.namespace}>
                  {ns.namespaceShowName === ns.namespace ? ns.namespace : `${ns.namespaceShowName} (${ns.namespace})`}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item label={t('nacos.columns.dataId') as string} name="dataId">
            <Input placeholder={t('nacos.dataIdPlaceholder') as string} allowClear style={{ width: 220 }} />
          </Form.Item>
          <Form.Item label={t('nacos.columns.group') as string} name="group">
            <Input placeholder={t('nacos.groupPlaceholder') as string} allowClear style={{ width: 220 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                {t('misc.search')}
              </Button>
              <Button onClick={onReset}>{t('misc.reset')}</Button>
              <Button icon={<RedoOutlined />} onClick={() => fetchData(current, pageSize)} />
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Table
        columns={columns}
        dataSource={dataSource}
        loading={loading}
        rowKey={(record) => `${record.group}@@${record.dataId}`}
        pagination={{
          total,
          current,
          pageSize,
          onChange: handlePageChange,
          showSizeChanger: true,
          showTotal: (totalCount) => `Total ${totalCount} items`,
        }}
      />

      <Modal
        title={`${t('misc.detail')}: ${editingConfig?.dataId || ''}`}
        open={editModalVisible}
        onOk={handlePublish}
        onCancel={() => setEditModalVisible(false)}
        width={1000}
        destroyOnClose
        confirmLoading={publishLoading}
        okText={t('misc.confirm')}
        cancelText={t('misc.cancel')}
      >
        <div style={{ marginBottom: 16 }}>
          <Card size="small" bordered={false} style={{ background: '#f5f5f5' }}>
            <Row gutter={24}>
              <Col span={10}>
                <strong>Data ID: </strong> {editingConfig?.dataId}
              </Col>
              <Col span={8}>
                <strong>Group: </strong> {editingConfig?.group || editingConfig?.groupName}
              </Col>
              <Col span={6}>
                <strong>Type: </strong> {editingConfig?.type}
              </Col>
            </Row>
          </Card>
        </div>
        {configMetadata && Object.keys(configMetadata).length > 0 && (
          <Card size="small" bordered={false} style={{ background: '#fafafa', marginBottom: 16, border: '1px solid #f0f0f0' }}>
            <Row gutter={[24, 8]}>
              <Col span={12}>
                <span style={{ color: '#888' }}>Service Name: </span>
                <span style={{ fontWeight: 500 }}>{configMetadata.serviceName || '-'}</span>
              </Col>
              <Col span={12}>
                <span style={{ color: '#888' }}>Namespace: </span>
                <span style={{ fontWeight: 500 }}>{configMetadata.sourceNamespace || '-'}</span>
              </Col>
              <Col span={12}>
                <span style={{ color: '#888' }}>API Count: </span>
                <span style={{ fontWeight: 500 }}>{configMetadata.apiCount || 0}</span>
              </Col>
              <Col span={12}>
                <span style={{ color: '#888' }}>Collected At: </span>
                <span style={{ fontWeight: 500 }}>
                  {configMetadata.collectedAt ? new Date(configMetadata.collectedAt).toLocaleString() : '-'}
                </span>
              </Col>
            </Row>
          </Card>
        )}

        <Form form={editForm} layout="vertical">
          <Tabs activeKey={activeTab} onChange={setActiveTab}>
            {apiData.length > 0 && (
              <TabPane tab={t('nacos.apiRegistry.tab') as string} key="api">
                <div style={{ marginBottom: 16 }}>
                  <Input
                    placeholder="搜索 API (Path / Summary / Method)"
                    value={filterText}
                    onChange={e => setFilterText(e.target.value)}
                    style={{ width: 300 }}
                    allowClear
                  />
                </div>
                <Table
                  columns={apiColumns}
                  dataSource={filteredApiData}
                  pagination={false}
                  rowKey={(record, index) => `${record.path}@@${index}`}
                  scroll={{ y: 500 }}
                  expandable={{
                    expandedRowRender: (apiRecord, apiIndex) => {
                      const parameterColumns = [
                        { title: 'Name', dataIndex: 'name', key: 'name', width: 150 },
                        { title: 'Loc', dataIndex: 'parameterType', key: 'parameterType', width: 150 },
                        { title: 'Type', dataIndex: 'type', key: 'type', width: 150 },
                        {
                          title: 'Req.',
                          dataIndex: 'required',
                          key: 'required',
                          width: 80,
                          render: (val: boolean) => (
                            <Tag color={val ? 'red' : 'default'}>{val ? 'true' : 'false'}</Tag>
                          ),
                        },
                        {
                          title: 'Description',
                          dataIndex: 'description',
                          key: 'description',
                          render: (val: string, paramRecord: ApiParameter, paramIndex: number) => (
                            <Input
                              value={val}
                              onChange={(e) => handleParameterDescriptionChange(apiIndex, paramIndex, e.target.value)}
                              placeholder="Edit description"
                            />
                          ),
                        },
                      ];

                      return (
                        <div style={{ padding: '16px', background: '#fff', border: '1px solid #f0f0f0', borderRadius: '4px' }}>
                          <h4 style={{ marginBottom: 16 }}>Parameters:</h4>
                          <Table
                            columns={parameterColumns}
                            dataSource={apiRecord.parameters || []}
                            pagination={false}
                            size="small"
                            rowKey={(p) => p.name}
                            expandable={{
                              expandedRowRender: (paramRecord, paramIndex) => {
                                if (!paramRecord.fields || paramRecord.fields.length === 0) return null;

                                const fieldColumns = [
                                  { title: 'Field Name', dataIndex: 'name', key: 'name', width: 150 },
                                  { title: 'Type', dataIndex: 'type', key: 'type', width: 150 },
                                  {
                                    title: 'Required',
                                    dataIndex: 'required',
                                    key: 'required',
                                    width: 100,
                                    render: (val: boolean) => (
                                      <Tag color={val ? 'red' : 'default'}>{val ? 'true' : 'false'}</Tag>
                                    ),
                                  },
                                  {
                                    title: 'Description',
                                    dataIndex: 'description',
                                    key: 'description',
                                    render: (val: string, fieldRecord: ApiField, fieldIndex: number) => (
                                      <Input
                                        value={val}
                                        onChange={(e) => handleEntityFieldDescriptionChange(apiIndex, paramIndex, fieldIndex, e.target.value)}
                                        placeholder="Edit description"
                                      />
                                    ),
                                  },
                                ];

                                return (
                                  <div style={{ padding: '12px', background: '#fdfaff', borderLeft: '3px solid #722ed1' }}>
                                    <div style={{ color: '#722ed1', fontWeight: 'bold', marginBottom: 8 }}>
                                      Entity Fields ({paramRecord.type}):
                                    </div>
                                    <Table
                                      columns={fieldColumns}
                                      dataSource={paramRecord.fields}
                                      pagination={false}
                                      size="small"
                                      rowKey={(f) => f.name}
                                    />
                                  </div>
                                );
                              },
                              defaultExpandAllRows: true,
                              rowExpandable: (paramRecord) => (paramRecord.fields || []).length > 0,
                            }}
                          />
                        </div>
                      );
                    },
                    rowExpandable: (apiRecord) => (apiRecord.parameters || []).length > 0,
                  }}
                />
              </TabPane>
            )}
            <TabPane tab={t('nacos.apiRegistry.raw') as string} key="raw" forceRender>
              <Form.Item
                name="content"
                label={t('nacos.form.content') as string}
                rules={[{ required: true, message: t('misc.isRequired') as string }]}
              >
                <CodeEditor
                  editorHeight="500px"
                  defaultLanguage={watchedType || 'text'}
                  value={watchedContent}
                  onChange={(val) => {
                    editForm.setFieldsValue({ content: val });
                    // Update API data if edited in raw view and JSON is valid
                    if (activeTab === 'raw') {
                      try {
                        const { apis, metadata } = parseApiData(val);
                        setApiData(apis);
                        setConfigMetadata(metadata);
                      } catch (e) {
                        // Ignore invalid JSON while typing
                      }
                    }
                  }}
                />
              </Form.Item>
            </TabPane>
          </Tabs>
        </Form>
      </Modal>
    </PageContainer >
  );
};

export default NacosList;
