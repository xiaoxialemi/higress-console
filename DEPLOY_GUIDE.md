# Higress 自定义控制台全流程部署指南

本项目记录了如何构建自定义版本的 Higress Console，并将其与 Higress 核心组件（Gateway/Controller）彻底分离部署的完整流程。

---

## 一、 核心架构：彻底分离部署方案 (最佳实践)

由于官方 Higress Helm Chart (v2.1.10 及更早版本) 存在缺陷，不支持通过参数直接彻底禁用 Console，我们需要通过修改本地 Chart
源码来实现“干净”的解耦。

### 1.1 背景准备：修复官方 Chart

1. **下载并解压官方 Chart 包**：
   ```bash
   cd /安装目录
   helm pull higress.io/higress --untar
   cd higress
   ```
2. **修复 `Chart.yaml` 配置**：
   在 `dependencies` 部分定位到 `higress-console`，在它下面新增一行 `condition` 配置。
   ```yaml
   - name: higress-console
     repository: "https://higress.io/helm-charts/"
     version: 2.1.9
     condition: higress-console.enabled  # <--- 手动加上这一行
   ```

### 1.2 部署核心组件 (不含控制台)

在 `higress` 源码目录下执行安装：

```bash
# 安装核心组件，此时 enabled=false 将彻底生效，不再创建控制台相关资源
helm upgrade --install higress . -n higress-system \
  --create-namespace \
  --set global.local=false \
  --set higress-core.gateway.replicas=1 \
  --set higress-console.enabled=false \
  --set higress-core.gateway.type=NodePort

> [!IMPORTANT]
> **参数解析：`.` (点)**
> 命令中的 `.` 非常关键！它告诉 Helm 使用**当前目录下**（也就是你刚刚手动修改过 `Chart.yaml` 的那个本地文件夹）的源码进行安装，而不是去远程仓库下载原始的官方包。只有这样，你对 `Chart.yaml` 的修复才会生效。

# 固定网关端口 -> 30080
kubectl patch svc higress-gateway -n higress-system --type='merge' \
  -p '{"spec": {"ports": [{"name": "http", "port": 80, "targetPort": 80, "nodePort": 30080}]}}'

# 固定 Controller 监控端口 -> 31014
kubectl patch svc higress-controller -n higress-system --type='merge' -p '{"spec": {"type": "NodePort", "ports": [{"name": "http", "port": 8888, "targetPort": 8888}, {"name": "https", "port": 8889, "targetPort": 8889}, {"name": "grpc-xds", "port": 15051, "targetPort": 15051}, {"name": "grpc-ads", "port": 15010, "targetPort": 15010}, {"name": "https-dns", "port": 15012, "targetPort": 15012}, {"name": "https-webhook", "port": 443, "targetPort": 443}, {"name": "http-monitoring", "port": 15014, "targetPort": 15014, "nodePort": 31014}]}}'
```

### 1.3 部署自定义控制台 (使用本项目 Chart)

```bash
cd /higress-console的安装目录

# 使用独立 Release 部署自定义项目
helm install higress-console ./helm -n higress-system \
  --set image.repository=higress-console \
  --set image.tag=custom-v1 \
  --set image.pullPolicy=IfNotPresent \
  --set service.type=NodePort

# 固定自定义控制台端口 -> 30527
kubectl patch svc higress-console -n higress-system --type='merge' \
  -p '{"spec": {"ports": [{"port": 8888, "nodePort": 30527}]}}'
  
# 如果启动失败，修改deployment中的端口改为8888
kubectl edit deployment -n higress-system higress-console

  
```

---

## 二、 日常开发：构建与更新流程

### 2.1 本地编译 (Windows)

```powershell
# 1. 检查 application.properties 确保端口为默认(15014)，无硬编码外部IP
# 2. 编译生成 JAR
.\mvnw clean package -Dmaven.test.skip=true
```

### 2.2 镜像构建 (服务器)

```bash
cd /home/haiyu/app/higress-console

# 1. 预处理：修复 Windows 编辑产生的换行符兼容问题
sed -i 's/\r$//' start.sh

# 2. 构建镜像并导入 k3s
docker build --build-arg TARGETARCH=amd64 -t higress-console:custom-v1 .
docker save higress-console:custom-v1 | k3s ctr images import -
```

### 2.3 生效更新

```bash
# 重启自定义控制台 Pod
kubectl rollout restart deployment/higress-console -n higress-system
```

---

## 三、 避坑指南总结

| 问题场景                | 原因分析                            | 解决方案                               |
|:--------------------|:--------------------------------|:-----------------------------------|
| **官方控制台删不掉**        | `Chart.yaml` 缺少 `condition` 绑定。 | 参考 1.1 节，手动修改官方 Chart 源码。          |
| **服务列表超时**          | JAR 里硬编码了 NodePort (31014)。     | 确保包内配置使用 Service Port (15014)。     |
| **脚本执行失败**          | Windows 换行符 (CRLF) 冲突。          | 构建前执行 `sed -i 's/\r$//' start.sh`。 |
| **k3s 无法加载镜像**      | k3s 不共用 Docker 的本地存储。           | 必须使用 `k3s ctr images import` 导入。   |
| **自定义 NodePort 偏移** | Helm Chart 默认可能不锁定 NodePort。    | 使用 `kubectl patch svc` 显式固定端口。     |

---

## 四、 常用维护命令

- **资源监控**: `kubectl get pods -n higress-system -w`
- **查看监听端口** `kubectl get svc -n higress-system`
- **日志诊断**: `kubectl logs -f deployment/higress-console -n higress-system`
- **看指定pod的日志**: `kubectl logs -n higress-system higress-console-5b7f965dc6-rq5qx`
- **加 -p 参数看上一次崩溃的遗言**: `kubectl logs -p -n higress-system higress-console-5b7f965dc6-rq5qx`
- **连通测试**:
  `kubectl exec -it <pod-name> -n higress-system -- curl -I http://higress-controller.higress-system.svc:15014/debug/registryz`
