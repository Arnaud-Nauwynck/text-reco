package fr.an.textreco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TextRecoServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TextRecoServerApplication.class, args);
    }
}
