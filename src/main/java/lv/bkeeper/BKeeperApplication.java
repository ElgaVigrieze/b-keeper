package lv.bkeeper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BKeeperApplication {

    public static void main(String[] args) {
        SpringApplication.run(BKeeperApplication.class, args);
    }
}
