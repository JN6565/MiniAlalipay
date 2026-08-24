import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

public class ValidateFlyway {
    public static void main(String[] args) {
        String url = "jdbc:mysql://114.55.75.194:3306/business_db" + "?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
        Flyway flyway = Flyway.configure().dataSource(url, "root", "teamuser2026").locations("filesystem:d:/newproject/MiniAIalipay/backend/business-center/target/classes/db/migration").baselineOnMigrate(true).schemas("business_db").load();
        flyway.validate();
        System.out.println("VALIDATE_OK");
        for (MigrationInfo info : flyway.info().applied()) { System.out.println(info.getVersion() + " | " + info.getDescription() + " | " + info.getState() + " | checksum=" + info.getChecksum()); }
    }
}
