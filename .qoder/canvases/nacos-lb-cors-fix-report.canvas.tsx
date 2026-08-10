import {
    Callout,
    Code,
    H1,
    MetricsGrid,
    ReportSection,
    ReportShell,
    Stack,
    Table,
    Tag,
    Text,
    Timeline,
} from 'qoder/canvas';

/**
 * Nacos 负载均衡污染 + 网关 CORS 拦截 双根因修复完成报告。
 * 数据来源：2026-08-08 晚生产部署排障会话的服务器日志、curl 对照实验与仓库变更审计。
 */
export default function NacosLbCorsFixReport() {
    return (
        <ReportShell width="wide" ariaLabel="Nacos 负载均衡改造完成报告">
            <Stack gap="sectionCompact">
                <Stack gap="component">
                    <H1>Nacos 负载均衡改造完成报告</H1>
                    <Text tone="secondary">
                        范围：生产环境（121.43.51.164）管理端登录链路间歇性 403/503 故障 · 排障窗口：2026-08-08 21:00–22:40
                    </Text>
                    <MetricsGrid
                        variant="header"
                        columns={4}
                        items={[
                            { label: '定位根因', value: '3 个', description: '构建脚本 / Nacos 实例污染 / CORS', tone: 'info' },
                            { label: '登录接口', value: '200', description: 'curl 返回 accessToken，405ms', tone: 'success' },
                            { label: '网关超时记录', value: '30034ms', description: '命中不可达实例的连接超时', tone: 'danger' },
                            { label: '仓库修复文件', value: '2 个', description: '.env.example、build-and-upload.ps1', tone: 'neutral' },
                        ]}
                    />
                </Stack>

                <ReportSection title="成果总结" divided>
                    <Stack gap="component">
                        <Text>
                            管理端（B 端 :81）登录失败的表象是「同一接口时好时坏」：一条请求返回成功，下一条却得到
                            403 空体或挂起 30 秒后 503。最终定位为三个相互叠加的独立缺陷，全部修复后登录链路恢复稳定。
                        </Text>
                        <Table
                            density="compact"
                            headers={['根因', '故障表现', '机理', '状态']}
                            rows={[
                                [
                                    '① 构建脚本上传空前端产物',
                                    '两端 Nginx 直接 403',
                                    '脚本每次全量清空 stage 目录，跳过前端构建时把空目录上传，Nginx 找不到 index.html',
                                    <Tag tone="success" key="f1">已修复</Tag>,
                                ],
                                [
                                    '② 本地实例污染生产 Nacos',
                                    '间歇性 503（挂 30 秒）',
                                    '本地开发服务注册进同一 Nacos，网关 lb:// 轮询命中 VirtualBox 网卡 IP 192.168.56.1，服务器无法连通',
                                    <Tag tone="success" key="f2">已修复</Tag>,
                                ],
                                [
                                    '③ 网关 CORS 白名单缺生产来源',
                                    '浏览器 POST 一律 403 空体',
                                    '浏览器即使同源也携带 Origin 头，CorsWebFilter 默认只放行 localhost:8000/8001，生产来源被拒且无响应体',
                                    <Tag tone="success" key="f3">已修复</Tag>,
                                ],
                            ]}
                        />
                        <Callout tone="info" title="为什么 curl 成功、浏览器失败">
                            curl 不带 Origin 头，绕过 CORS 过滤器直达业务链路；浏览器的 POST 必然携带
                            Origin: http://121.43.51.164:81，被 CorsWebFilter 在进入网关业务过滤器之前拒绝，
                            返回 403 且 Content-Length: 0、无 X-Request-Id —— 与浏览器抓包指纹完全吻合。
                        </Callout>
                    </Stack>
                </ReportSection>

                <ReportSection title="关键步骤" divided>
                    <Timeline
                        density="compact"
                        events={[
                            {
                                id: 's1',
                                timestamp: '阶段 1',
                                title: '修复构建脚本空目录上传',
                                description: 'build-and-upload.ps1 不再全量清空 stage；跳过前端构建时强制校验 stage/web/*/index.html 存在，否则直接报错禁止上传。两端前端 403 消失。',
                                state: 'completed',
                                tone: 'success',
                            },
                            {
                                id: 's2',
                                timestamp: '阶段 2',
                                title: '抓取网关现场日志，锁定轮询污染',
                                description: '网关日志显示 user-center 同时存在 172.19.0.5:8081（容器）与 192.168.56.1:8081（本机 VirtualBox）两个实例；命中后者的请求 durationMs=30034 后 503。',
                                state: 'completed',
                                tone: 'warning',
                            },
                            {
                                id: 's3',
                                timestamp: '阶段 3',
                                title: '停止本地服务，等待心跳过期摘除',
                                description: '关闭全部本地开发服务并等待约 30-60 秒，Nacos 各服务恢复为仅 1 个容器内网实例；curl 真实密码登录返回 200。',
                                state: 'completed',
                                tone: 'success',
                            },
                            {
                                id: 's4',
                                timestamp: '阶段 4',
                                title: 'Origin 对照实验定位 CORS',
                                description: '同一登录请求：不带 Origin → 200；带不在白名单的 Origin → 403 空体。确认拒绝点为网关 CorsWebFilter 而非业务过滤器。',
                                state: 'completed',
                                tone: 'warning',
                            },
                            {
                                id: 's5',
                                timestamp: '阶段 5',
                                title: '启用生产 CORS 来源并固化到模板',
                                description: '.env.example 中 GATEWAY_CORS_ORIGINS 由注释可选改为默认必填，取值 http://121.43.51.164,http://121.43.51.164:81，并重写中文注释说明 403 机理。',
                                state: 'completed',
                                tone: 'success',
                            },
                        ]}
                    />
                </ReportSection>

                <ReportSection title="变更文件" divided>
                    <Table
                        density="compact"
                        headers={['文件', '变更内容', '目的']}
                        rows={[
                            [
                                <Code key="c1">deploy/production/.env.example</Code>,
                                'GATEWAY_CORS_ORIGINS 取消注释默认启用；注释改为「必填」并解释浏览器 POST 携带 Origin 被拒为 403 的机理',
                                '保证任何按模板部署的环境都不会再因缺 CORS 来源而整站登录失败',
                            ],
                            [
                                <Code key="c2">deploy/production/build-and-upload.ps1</Code>,
                                '跳过构建时保留 stage 产物并校验 index.html；重建前仅清空对应产物目录',
                                '杜绝把空前端目录上传到服务器造成 Nginx 403',
                            ],
                        ]}
                    />
                    <Text tone="secondary">
                        服务器侧对应收尾动作：/opt/minialalipay/.env 配置 GATEWAY_CORS_ORIGINS 后 docker compose up -d gateway 重启网关生效。
                    </Text>
                </ReportSection>

                <ReportSection title="验证证据" divided>
                    <Stack gap="component">
                        <Table
                            density="compact"
                            headers={['证据', '内容', '结论']}
                            rows={[
                                [
                                    '网关访问日志（22:38）',
                                    'POST /api/v1/auth/login status=200 durationMs=405，响应含 accessToken、nickname=B端系统管理员',
                                    '服务端登录链路完全正常',
                                ],
                                [
                                    '网关转发日志',
                                    '命中 192.168.56.1 实例的请求 durationMs=30034 后报「下游服务连接失败」',
                                    '503 根因是轮询命中不可达的本地实例',
                                ],
                                [
                                    'Nacos 实例列表',
                                    '修复前 user-center 2 实例（172.19.0.5 + 192.168.56.1）；停本地后仅剩 1 个容器实例且健康',
                                    '注册中心已净化',
                                ],
                                [
                                    'curl 对照实验',
                                    '错误密码 → 401 LOGIN_INVALID 带 JSON 体；无 Origin → 200；白名单外 Origin → 403 空体',
                                    '逐层排除业务错误，锁定 CORS 过滤器',
                                ],
                                [
                                    '服务器资源',
                                    'free -h：内存 14G、可用 9.4G；docker compose ps 全部 healthy',
                                    '排除 OOM / 容器崩溃循环假设',
                                ],
                                [
                                    '浏览器抓包指纹',
                                    '403 响应 Content-Length: 0、无 X-Request-Id、Referrer-Policy: no-referrer',
                                    '拒绝发生在网关业务过滤器之前（CorsWebFilter），非 Nginx、非 user-center',
                                ],
                            ]}
                        />
                        <Callout tone="warning" title="已沉淀的排障经验（记忆）">
                            本地与生产共用注册中心时，间歇性 503/403 应优先检查服务实例列表而非应用配置；
                            部署验证前先停本地服务并等待 30-60 秒心跳过期，再用网关日志确认实例集合干净。
                        </Callout>
                    </Stack>
                </ReportSection>

                <ReportSection title="最终结果" divided>
                    <Stack gap="component">
                        <Callout tone="success" title="登录链路已恢复（2026-08-09 实测验证）">
                            服务器 .env 启用 GATEWAY_CORS_ORIGINS 并强制重建网关后：curl 带 Origin 登录返回 200 且响应携带
                            Access-Control-Allow-Origin；浏览器实测登录成功，跳转运营看板，无任何错误提示与横幅。
                        </Callout>
                        <Table
                            density="compact"
                            headers={['检查项', '结果']}
                            rows={[
                                ['C 端 H5（:80）登录、转账', '正常'],
                                ['管理端（:81）服务端登录链路', '200，405ms'],
                                ['管理端浏览器 CORS 拦截', '已消除：实测登录成功跳转运营看板'],
                                ['Nacos 实例纯净度', '每服务仅 1 个容器内网实例'],
                                ['服务器内存 / 容器健康', '14G 内存、可用 9.4G，全部 healthy'],
                            ]}
                        />
                        <Text tone="secondary">
                            后续建议：生产与本地使用不同 Nacos 命名空间（或本地禁用注册）避免再次互相污染；
                            网关 CORS 拒绝建议增加带 JSON 体的错误响应与审计日志，避免再次出现「403 空体」这类难以归因的现场。
                        </Text>
                    </Stack>
                </ReportSection>
            </Stack>
        </ReportShell>
    );
}
