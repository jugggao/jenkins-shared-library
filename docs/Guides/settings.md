# 配置文件说明

```yaml
base:
  job: ''
  type: ''
  action: deploy
  dir: ''
  env: dev
  name: eureka-demo
  port: 40001
  project: pipeline-demo
  user: ''
  userid: ''
  logLevel: ''
git:
  commitLength: 8
  commitId: ''
  repo: ''
  branch: dev
  projectId: 417
build:
  tool: ''
  buildDir: .
  appDir: .
  language: java
  options: ''
scanner:
  enabled: true
  sources: ''
  exclusions: ''
docker:
  imgName: ''
  imgTag: ''
  imgBuildOptions: ''
  dockerfile: ''
  ignorefile: ''
  appRoot: data/app
  content: ''
kubernetes:
  imgPullPolicy: Always
  namespace: pipeline-demo
  limitsCpu: 500m
  limitsMemory: 1024Mi
  requestsCpu: 1m
  requestsMemory: 10Mi
  probePath: /actuator/health
  probeInitialDelaySeconds: 60
  probePeriodSeconds: 30
  probeSuccessThreshold: 1
  probeFailureThreshold: 3
  probeTimeoutSeconds: 5
  deploymentFile: ''
  serviceFile: ''
  configMapFile: ''
  ingressFile: ''
domain:
  enabled: true
  resolved: true
  dnsServer:
  - aliCloud
  - winServer
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
  hosts:
  - host: web-pipeline-demo.example.com
    path: /
    pathType: ImplementationSpecific
  tls:
  - host: web-pipeline-demo.example.com
    cert: ambow-com-tls
nacos:
  enabled: true
  application: eureka
  address: ''
notification:
  build:
    enabled: true
    type: wechat
    wechat:
      mobile:
      - ''
      url: 
    email: {}
    webhook: {}
```