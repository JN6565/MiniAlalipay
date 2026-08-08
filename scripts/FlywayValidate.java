import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.exception.FlywayValidateException;

/**
 * Flyway 迁移预检工具（单文件源码启动，无需编译）。
 *
 * <p>用途：合并分支或修改迁移文件后、启动服务之前，直连远程数据库校验
 * flyway_schema_history 与本地迁移文件的一致性，提前暴露校验和不匹配问题，
 * 避免服务启动时才以 sqlSessionTemplate 等表层错误失败。</p>
 *
 * <p>用法（由 scripts/validate-flyway.ps1 调用，一般不直接手工执行）：</p>
 * <pre>
 * java -cp &lt;flyway等依赖jar&gt; FlywayValidate.java &lt;jdbcUrl&gt; &lt;用户名&gt; &lt;密码&gt; &lt;schema&gt; &lt;迁移目录&gt; &lt;outOfOrder&gt; [--repair]
 * </pre>
 *
 * <p>行为说明：</p>
 * <ul>
 *   <li>默认只执行 validate：校验通过打印 PASS 并以退出码 0 结束；
 *       校验失败打印不匹配详情并以退出码 1 结束；连接失败退出码 3。</li>
 *   <li>带 --repair 时先执行 flyway.repair()，仅对齐历史表 checksum、
 *       删除失败记录，不重放任何业务 SQL；repair 后自动复验。</li>
 *   <li>baselineOnMigrate 与各服务 application.yml 保持一致设为 true。</li>
 * </ul>
 */
public class FlywayValidate {

    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println("用法: FlywayValidate <jdbcUrl> <用户名> <密码> <schema> <迁移目录> <outOfOrder:true|false> [--repair]");
            System.exit(2);
        }
        String jdbcUrl = args[0];
        String user = args[1];
        String password = args[2];
        String schema = args[3];
        String migrationDir = args[4];
        boolean outOfOrder = Boolean.parseBoolean(args[5]);
        boolean repair = args.length > 6 && "--repair".equals(args[6]);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(jdbcUrl, user, password)
                    .locations("filesystem:" + migrationDir)
                    .schemas(schema)
                    .baselineOnMigrate(true)
                    .outOfOrder(outOfOrder)
                    .load();

            if (repair) {
                flyway.repair();
                System.out.println("  [repair] 已对齐 flyway_schema_history 校验和（未重放业务 SQL）");
            }

            MigrationInfo[] applied = flyway.info().applied();
            MigrationInfo[] pending = flyway.info().pending();
            System.out.println("  已执行迁移 " + applied.length + " 个，待执行迁移 " + pending.length + " 个");
            for (MigrationInfo info : pending) {
                System.out.println("  [pending] " + info.getVersion() + " " + info.getDescription());
            }

            // validate() 校验失败时抛 FlywayValidateException，异常消息包含
            // 具体哪个版本的 checksum / 描述不匹配，比 ValidateOutput 字段更直观
            try {
                flyway.validate();
                System.out.println("  [结果] PASS 校验通过");
                System.exit(0);
            } catch (FlywayValidateException e) {
                System.out.println("  [结果] FAIL 校验失败: " + e.getMessage());
                System.out.println("  [提示] 确认迁移文件内容无误后可用 -Repair 对齐校验和，或恢复文件为已执行版本");
                System.exit(1);
            }
        } catch (Exception e) {
            System.out.println("  [错误] 连接或执行异常: " + e.getMessage());
            System.exit(3);
        }
    }
}
